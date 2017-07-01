package padfsThreads;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.RestTemplate;

import jobManagement.jobOperation.JobOperation;
import jobManagement.jobOperation.serverOp.UpdateMetaInfo;
import restInterface.RestInterface;
import restInterface.RestServer;
import restInterface.manageOp.RestIsPresent;
import restInterface.manageOp.RestNotifyPutFileResponse;
import restInterface.manageOp.RestTransfer;
import system.SystemEnvironment;
import system.SystemEnvironment.Constants;
import system.SystemEnvironment.Constants.Rest;
import system.consensus.ConsensusServerGroup;
import system.containers.MetaInfo;
import system.containers.Server;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;
import system.managers.SqlManager;
import system.SystemEnvironment.Variables;

public class FileManager extends  StoppableThread{
	private PriorityBlockingQueue<JobOperation> inOp;
	
	public FileManager(PriorityBlockingQueue<JobOperation> inOp){
		super();
		this.inOp = inOp;
	}
	
	public void run() {
		
		while(getExitingRequested() == false){
			try{
				PadFsLogger.log(LogLevel.DEBUG, "starting FileManager procedure");
				// read filesManaged list from DB
				List<MetaInfo> list = SqlManager.getManagedFiles();
				
				PadFsLogger.log(LogLevel.DEBUG, "list of metainfo. size: "+list.size());
				
				Iterator<MetaInfo> it = list.iterator();
				while(it.hasNext() && getExitingRequested() == false){
					MetaInfo file = it.next();
					
					if(!file.isDirectory()){
						// foreach file, check if all its replicas are available
						checkAndSpawnReplicas(file);
					}
					
					// foreach file or dir, notify to servers
					notifyPutFile(file);
					
				}
								
			    //sleep for the given amount of time or until a requestToStop call will arrive 
				PadFsLogger.log(LogLevel.DEBUG,"FileManager goes to sleep before recheck "+Variables.getSleepTime_CheckReplicasAlive());
				waitFor(Variables.getSleepTime_CheckReplicasAlive());
				
			}catch(Exception e){ 
				PadFsLogger.log(LogLevel.ERROR, e.getClass().getName() + ": " + e.getMessage());
			}
		}
		PadFsLogger.log(LogLevel.INFO, "FileManager shutdown");
		signalStopCompleted();
	}
	
	
	private void checkAndSpawnReplicas(MetaInfo file){
		List<Long> replicaServers = null;
		
		replicaServers = checkReplicasAlive( file );
		if(replicaServers == null){
			PadFsLogger.log(LogLevel.ERROR, "replicaServers can't be null");
			return;
		}
		if(replicaServers.size() == 0){
			PadFsLogger.log(LogLevel.WARNING, "no reachable replica servers for the file '"+file.getPath()+"'. there are network errors or the file is lost.");
		}
		else if( replicaServers.size() < Constants.replicaNumber){
			PadFsLogger.log(LogLevel.INFO, "spawning replicas for the file '"+file.getPath()+"'");
			int spawnedReplicas = spawnReplicas(file,replicaServers);
			if(spawnedReplicas > 0){
				/* construct the MetaInfo WITHOUT permission information. permission information are managed ONLY by chmod operation */
				MetaInfo updatedFile = new MetaInfo(file,replicaServers);
				UpdateMetaInfo job = new UpdateMetaInfo(updatedFile);
				inOp.put(job);
				PadFsLogger.log(LogLevel.DEBUG, "replicas reestablished for the file '"+file.getPath()+"'. UpdateInfo inserted in queue");
			}
			else{
				
				PadFsLogger.log(LogLevel.WARNING, "can't spawn new replicas for the file '"+file.getPath()+"'. current replica number = "+replicaServers.size());
			}
		}
	}
	

	/**
	 * 
	 * @param file the MetaInfo of the physical file that has to be transfered on other servers
	 * @param replicaServers the replicaServers that have a copy of the physical file
	 * 
	 * the replicaServers.length() must be at least 1, otherwise the physical file is lost.
	 * 
	 * This function can fail due to communication problem and empty replicaServers
	 * 
	 * @return the number of spawned replicas
	 *  
	 */
	private int spawnReplicas(MetaInfo file, List<Long> replicaServers) {
		int spawnedReplicas = 0;
		if(replicaServers == null){
			PadFsLogger.log(LogLevel.ERROR, "replicaServers is null");
			return spawnedReplicas;
		}
		if(replicaServers.size() <= 0){
			PadFsLogger.log(LogLevel.WARNING, "no reachable replica servers for the file '"+file.getPath()+"'. there are network errors or the file is lost.");
			return spawnedReplicas;
		}
		if(replicaServers.size() >= Constants.replicaNumber){
			PadFsLogger.log(LogLevel.DEBUG, "no new replica for the file '"+file.getPath()+"'. is necessary");
			return spawnedReplicas;
		}

		
		/* retrieve the file */
		//MultipartFile multipartFile = null;
		String tmpName;
		File tmpFile;
		try{
			Server s = Server.find(replicaServers.get(0));

			String url = RestInterface.GetFile.generateUrl(s.getIp(),s.getPort(),file.getIdOwner(),file.getChecksum(),file.getPath());
						
			if((tmpName = RestServer.download(url)) == null){	
				PadFsLogger.log(LogLevel.WARNING, "failed downloading file");
				return spawnedReplicas;
			}
			tmpFile = new File(tmpName);
			
		}
		catch(Exception e){
			PadFsLogger.log(LogLevel.WARNING, "failed to retrieve the file");
			return spawnedReplicas;
		}
		
		
		int maxRetrieveServer = Constants.replicaNumber * Constants.replicaSearchFactor;
		List<Server> l = SqlManager.getServersLessLoaded(maxRetrieveServer);
		if(l == null){
			PadFsLogger.log(LogLevel.WARNING, "no servers available to spawn new replicas");
			try{
				if(tmpFile != null)
					tmpFile.delete();
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.ERROR,"temporary file can not be deleted");
			}
			return spawnedReplicas;
		}
		

		
		/* spawn a new server until replicaServers reach the replicaNumber dimension */
		Iterator<Server> it = l.iterator();
		while(it.hasNext() && replicaServers.size() < Constants.replicaNumber && getExitingRequested() == false){
				Server s = it.next();
			
				/* check that server s has not already the file hosted */
				if(!replicaServers.contains(s.getId())){
	
					MultiValueMap<String, Object> dataPostMessage = new LinkedMultiValueMap<String, Object>();
				    dataPostMessage.add("owner", file.getIdOwner());
				    dataPostMessage.add("path" , file.getPath());
				    dataPostMessage.add("file" , new FileSystemResource(tmpName));
				    dataPostMessage.add("label" , file.getLabel());
				    dataPostMessage.add("checksum" , file.getChecksum());
				    dataPostMessage.add("password", Variables.getServerPassword());
					
					if(sendTransfer(s,dataPostMessage)){ 
						PadFsLogger.log(LogLevel.DEBUG, "new replica of '"+file.getPath()+"' spawned on server "+s.getId());
						replicaServers.add(s.getId());
						spawnedReplicas++;
					}
					else{
						PadFsLogger.log(LogLevel.WARNING, "spawning replica on server "+s.getId() +" failed");								
					}
					
				}
		}
		
		/* delete temporary file */
		try{
			if(tmpFile != null)
				tmpFile.delete();
		}
		catch(Exception e){
			PadFsLogger.log(LogLevel.ERROR,"temporary file can not be deleted");
		}
		
		if(replicaServers.size() >= Constants.replicaNumber){
			PadFsLogger.log(LogLevel.INFO, "replicas of '"+file.getPath()+"' transferred");
			return spawnedReplicas;
		}
		
		PadFsLogger.log(LogLevel.WARNING, "spawning replicas of '"+file.getPath()+"' not completed");
		return spawnedReplicas;
		
	}

	private boolean sendTransfer(Server s, MultiValueMap<String, Object> dataPostMessage) {
		String uri 	= Variables.getProtocol()+"://"+s.getIp()+":"+s.getPort()+"/transfer/"; 
		system.logger.PadFsLogger.log(LogLevel.DEBUG, "transfer URI: "+uri);
		
		HttpHeaders requestHeaders = new HttpHeaders();
		HttpEntity<?> httpEntity = new HttpEntity<Object>(dataPostMessage, requestHeaders);
		
		RestTemplate restTemplate = SystemEnvironment.generateRestTemplate();
		ResponseEntity<RestTransfer> response = null;
		try{
			response = restTemplate.exchange(uri, HttpMethod.POST, httpEntity, RestTransfer.class);
		}
		catch(Exception e){
			PadFsLogger.log(LogLevel.WARNING, "impossible to communicate with server "+s.getId());
			return false;
		}
		
		if(response != null && response.getBody()!= null && 
				(response.getBody().getStatus() == Rest.status.ack  || (response.getBody().getError()!= null && 
																		response.getBody().getError().equals(Rest.errors.fileAlreadyHosted)))){
			return true;
		}
		
		PadFsLogger.log(LogLevel.WARNING, "Failed transfer file to server "+s.getId());
		return false;
	}

	/*private MultipartFile getMultipartFile(String tmpName,String originalFileName) {
		 MultipartFile multipartFile= null;
		 try {

		   File file = new File(tmpName);
		  
			FileInputStream input = new FileInputStream(file);
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();

			int nRead;
			byte[] data = new byte[16384];

			while ((nRead = input.read(data, 0, data.length)) != -1) {
			  buffer.write(data, 0, nRead);
			}

			buffer.flush();
		
		   multipartFile = new MockMultipartFile(Constants.Rest.Put.fieldNameFileUpload,
		            originalFileName, "multipart/form-data", buffer.toByteArray());  //text/pain
		
		    input.close();
			
		 } catch (Exception e) {
				e.printStackTrace();
		 }
				
		return multipartFile;
	}*/

	
	

	/**
	 * @return the id list of reachable server that host this file
	 */
	private static List<Long> checkReplicasAlive(MetaInfo file) {
		PadFsLogger.log(LogLevel.DEBUG, "start checkReplicasAlive"); 
		List<Long> ret = new LinkedList<Long>();
		PadFsLogger.log(LogLevel.TRACE, "replicaServers to be checked are "+file.getHostersId());
		
		for(Long id: file.getHostersId()){
			PadFsLogger.log(LogLevel.TRACE, "check replica on server "+id); 
			Server s = Server.find(id);					

			RestIsPresent response = RestServer.isPresent(s, file);
			
			if(response == null){
				PadFsLogger.log(LogLevel.WARNING, "response is null");
				continue;
			}
			
			if(response.getStatus() == Rest.status.ok){
				ret.add(id);
			}
		}
		return ret;
	}
	
	
	
	private void notifyPutFile(MetaInfo file) { 
		String path 	= file.getPath();
		int idOwner 	= file.getIdOwner();
		long label  	= file.getLabel();
		String parentId = file.getChecksumParent();
		
		PadFsLogger.log(LogLevel.DEBUG, "notifyPutFile: "+path+ " - "+idOwner);
		String directory = SystemEnvironment.getParentPath(path);
		if(directory != null && !directory.equals("")){
			PadFsLogger.log(LogLevel.DEBUG, "notifyPut to servers for file: "+path);
			long[] serverIds = SqlManager.getIdFromConsensusLabel(label);
			
			Iterator<Server> it = (new ConsensusServerGroup(serverIds)).iterator();	
			while(it != null && it.hasNext() && getExitingRequested() == false){
				Server s = it.next();
				PadFsLogger.log(LogLevel.DEBUG, "notifyPut to server "+s.getId());

				
				
				String url = RestInterface.NotifyPutFile.generateUrl(s.getIp(),s.getPort(),idOwner,file.getSize(),file.getDateTime(),file.isDirectory(),parentId,path);
			
				PadFsLogger.log(LogLevel.TRACE, url);
				
				AsyncRestTemplate rt = SystemEnvironment.generateAsyncRestTemplate();
				ListenableFuture<ResponseEntity<RestNotifyPutFileResponse>> reply;
				reply = rt.exchange(url, HttpMethod.GET, null, RestNotifyPutFileResponse.class); //ignore result
				
				reply.addCallback(new ListenableFutureCallback<ResponseEntity<RestNotifyPutFileResponse>>() {
					
					
					@Override
					public void onSuccess(ResponseEntity<RestNotifyPutFileResponse> res) {
						try{
							//TODO improvement collect responses and flag the file in the DB if all the servers have stored this information to not send it again
							PadFsLogger.log(LogLevel.DEBUG,	"CALLBACK - RestNotifyPutFileResponse messagge SUCCESS" );
						} catch (Exception e) {
							PadFsLogger.log(LogLevel.WARNING,	"CALLBACK - RestNotifyPutFileResponse onSuccess FAILURE: "	+ e.getMessage());				
							e.printStackTrace();
						}								
					}

					@Override
					public void onFailure(Throwable e) {
					
						PadFsLogger.log(LogLevel.WARNING,	"CALLBACK - RestNotifyPutFileResponse messagge FAILURE");
						PadFsLogger.log(LogLevel.TRACE,	"CALLBACK - RestNotifyPutFileResponse messagge FAILURE: "+e.getMessage());
									
					}
				});
				
			}
		}
		
	}
	
	
}
