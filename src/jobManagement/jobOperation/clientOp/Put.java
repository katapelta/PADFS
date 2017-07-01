package jobManagement.jobOperation.clientOp;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import restInterface.RestInterface;
import restInterface.manageOp.RestNotifyPutFileResponse;
import restInterface.manageOp.RestTransfer;
import restInterface.op.RestPut;
import system.SystemEnvironment;
import system.SystemEnvironment.Constants;
import system.SystemEnvironment.Constants.OperationPriority;
import system.SystemEnvironment.Constants.Rest;
import system.SystemEnvironment.Constants.Rest.errors;
import system.SystemEnvironment.Variables;
import system.consensus.ConsensusServerGroup;
import system.containers.MetaInfo;
import system.containers.Server;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;
import system.managers.LocalFsManager;
import system.managers.SqlManager;

public class Put extends JobClientOp{
	
	private DeferredResult<RestPut> defResultMsg;
	private MultipartFile file;
	private long fileSize; // need to be transmitted to other servers during the consensus messages exchange
	private List<Server> hostingServerList = null;
	private String checksum = null;
	private String uniqueIdParent = null;
	/*
	 * {
	 * "user":"admin",
	 * "password":"admin",
	 * "path":"0.jpeg",
	 * "idOp":"2.1",
	 * }
	 */
	private FileSystemResource temporaryUploadedFile = null;
	private String name = null;
	
	private class PrepareVariables{

		private List<Server> outputList = new ArrayList<Server>();
		private Iterator<Server> it = null;
		private int pendingOperation = 0;
		
		public PrepareVariables(List<Server> serverList){
			if(serverList != null){
				it = serverList.iterator();
			}
			else{
				PadFsLogger.log(LogLevel.ERROR, "inputList should not be null");	
			}
		}
		
		
		public synchronized Server getNextServer() {
			if(it == null ){
				PadFsLogger.log(LogLevel.ERROR, "iterator should not be null");
				return null;
			}
			
			if(it.hasNext()){
				pendingOperation++;
				return it.next();
			}
			
			return null;
		}
		
		public synchronized int getResponseNumber(){
			return outputList.size();
		}
		
		public List<Server> getOutputList(){
			return outputList;
		}
		
		public synchronized void addServer(Server s){
			synchronized(this){
				outputList.add(s);
				pendingOperation--;
				this.notify();
			}
		}
		public synchronized void failedServer(){
			synchronized(this){
				pendingOperation--;
				this.notify();
			}
		}

		public void waitUntil(int replicanumber) {
			while(getResponseNumber() < replicanumber && pendingOperation > 0){
	    		try {
	    			synchronized(this){
	    				this.wait();
	    			}
				} catch (InterruptedException e) {
					;
				}
	    	}
		}
		
	}
	
	/**
	 * HAPV: {
	 * "user":"admin",
	 * "password":"admin",
	 * "path":"GZinnecker_Carousel_01.jpg",
	 * "idOp":"1.1",
	 *MANCA OWNER SERVER ID
	 * "priority":"PUT"} CL: jobManagement.jobOperation.clientOp.Put
	*/
	
	@JsonCreator
	public Put(
			@JsonProperty("user") 			String user, 
			@JsonProperty("password") 		String password, 
			@JsonProperty("usernameOwner") 	String usernameOwner, 
			@JsonProperty("path") 			String path, 
			@JsonProperty("name") 			String name, 
			@JsonProperty("idOp")			String idOp,
			@JsonProperty("hostingServersId") long[] ownerServersId,
			@JsonProperty("fileSize")		long fileSize,
			@JsonProperty("checksum")		String checksum,
			@JsonProperty("uniqueIdParent")	String uniqueIdParent,
			@JsonProperty("idConsRun") Long idConsRun) {
		super(user,password,usernameOwner,path,idOp,OperationPriority.PUT,idConsRun); 
		hostingServerList = SqlManager.getServerList(ownerServersId);
		this.fileSize = fileSize;
		this.checksum = checksum;
		this.uniqueIdParent = uniqueIdParent;
	}
	
	public String getChecksum(){
		return checksum;
	}
	
	public String getUniqueIdParent(){
		return uniqueIdParent;
	}
	
	
	public long[] getHostingServersId(){
		
		if(hostingServerList == null)
			return null;
		
		int maxSize = hostingServerList.size();
		long[] ids = new long[maxSize];
		Iterator<Server> it = hostingServerList.iterator();
		int i = 0;
		while(it.hasNext() && i < maxSize){
			Server s = it.next();
			ids[i++] = s.getId();
		}
		
		return ids;
		
	}
	
	@JsonIgnore
	public Put(
			DeferredResult<RestPut> defResultMsg,
			String user, 
			String password, 
			String usernameOwner, 
			String path, 
			MultipartFile file){
		super(	user,
				password,
				usernameOwner,
				SystemEnvironment.getLogicalPath(path,file.getOriginalFilename()),
				OperationPriority.PUT
				); 																	
		this.defResultMsg = defResultMsg;
		this.file= file;
		this.name = file.getOriginalFilename();
		
	}	
	
	@JsonIgnore
	public Put(
			DeferredResult<RestPut> defResultMsg,
			String user, 
			String password, 
			String usernameOwner, 
			String path, 
			String name,
			MultipartFile file){
		super(	user,
				password,
				usernameOwner,
				SystemEnvironment.getLogicalPath(path,name),
				OperationPriority.PUT											
				); 
		this.defResultMsg = defResultMsg;
		this.file= file;
		this.name = name;
		
	}	
		
	/**
	 * 
	 * @param dataPostMessage the values to be transferred
	 * @param pv the status variables of the transfer progress
	 */
	private void transferToServer(	MultiValueMap<String, Object> dataPostMessage,
									PrepareVariables pv){
		
		ListenableFuture<ResponseEntity<RestTransfer>> responseFuture ;
		AsyncRestTemplate rest 	= SystemEnvironment.generateAsyncRestTemplate();
		
		
		Server server = pv.getNextServer();
		if(server == null){
			//no more available servers
			return;
		}

		
		String uri 	= RestInterface.Transfer.generateUrl(server.getIp(),server.getPort());
		system.logger.PadFsLogger.log(LogLevel.DEBUG, "transfer URI: "+uri);
		
		
		HttpHeaders requestHeaders = new HttpHeaders();
		HttpEntity<?> httpEntity = new HttpEntity<Object>(dataPostMessage, requestHeaders);

		try{
			responseFuture = rest.exchange(uri, HttpMethod.POST, httpEntity, RestTransfer.class);
	    	responseFuture.addCallback(new ListenableFutureCallback<ResponseEntity<RestTransfer>>() {
	
				@Override
				public void onSuccess(ResponseEntity<RestTransfer> response) {
					// ok => add to output queue
					if(response != null && response.getBody() != null && 
							(response.getBody().getStatus() == Rest.status.ack
							  || (response.getBody().getStatus()!=Rest.status.ack && response.getBody().getError().equals(Constants.Rest.errors.fileAlreadyHosted))
							)
						){
						system.logger.PadFsLogger.log(LogLevel.DEBUG, "++ ACCEPTED TRANSFER by "+server.getIp()+":"+server.getPort());
						pv.addServer(server);
						system.logger.PadFsLogger.log(LogLevel.DEBUG, "Added server to outputList "+server.getIp()+":"+server.getPort());
					}
					else{
						system.logger.PadFsLogger.log(LogLevel.WARNING, "-- REJECTED TRANSFER WITH: "+server.getIp()+":"+server.getPort());
						if(response == null){
							PadFsLogger.log(LogLevel.DEBUG, "response is null");
						}
						else if(response.getBody()==null){
							PadFsLogger.log(LogLevel.DEBUG, "response body is null");
						}
						else{
							PadFsLogger.log(LogLevel.DEBUG, "response status: "+response.getBody().getStatus() + "  response error: "+response.getBody().getError());
						}
						transferToServer(dataPostMessage,pv);
						pv.failedServer();
					}
					
				}
	
				@Override
				public void onFailure(Throwable ex) {
					ex.printStackTrace();
					
					system.logger.PadFsLogger.log(LogLevel.WARNING, "-- FAILED TRANSFER WITH: "+server.getIp()+":"+server.getPort());
					transferToServer(dataPostMessage,pv);	
					pv.failedServer();
				}
			});   
		}
    	catch(Exception e){
    		system.logger.PadFsLogger.log(LogLevel.WARNING, "-- FAILED COMMUNICATION: "+server.getIp()+":"+server.getPort());
			transferToServer(dataPostMessage,pv);
			pv.failedServer();
		}
	}	

	/**
	 * 
	 * @param ownerFileListServer the list of the candidate servers to become owner of idFile
	 * @param idFile id of the file to be transferred on the other servers
	 * 
	 * @return the list of the servers that hosted the idFile
	 */
	private List<Server> uploadOnOtherServers(List<Server> ownerFileListServer, Long idFile) {
		PrepareVariables pv = new PrepareVariables(ownerFileListServer);
		
		String newPath;
		try {
			newPath = URLEncoder.encode(getPath(),Constants.UTF8);
		} catch (UnsupportedEncodingException e) {
			PadFsLogger.log(LogLevel.ERROR, "path not supported: " + getPath());
			return null;
		}
		
		
	    String localPath 	= Variables.getFileSystemTMPPath()+Variables.getOSFileSeparator()+String.valueOf(idFile);
	    
	    MultiValueMap<String, Object> dataPostMessage;
	    dataPostMessage = RestInterface.Transfer.generatePostParameters(SqlManager.getIdUser(getUsernameOwner()),
	    																newPath,
	    																new FileSystemResource(localPath),
	    																this.getServerLabel(), 
	    																this.checksum
	    																);

	   	    
    	for(int i = 0;i<Constants.replicaNumber;i++){
	    	transferToServer(dataPostMessage,pv);
	    }
    	pv.waitUntil(Constants.replicaNumber);
    	PadFsLogger.log(LogLevel.DEBUG, "End Waiting transferToOtherServers");

    	    
	    return pv.getOutputList(); 
	}	
	
	public long getFileSize(){
		return fileSize;
	}
	
	@Override
	public boolean prepareOp() {
		/* check user permissions */
		Rest.errors err = checkPermission(Constants.RequiredPermissions.Put);
		
		if(err != null && err.equals(errors.fileNotFound)){
			/* if the file does not already exists, check permission on the parent directory to create it */
			err = checkPermission(Constants.RequiredPermissions.Put,true);
		}
		
		if(err != null){
			system.logger.PadFsLogger.log(LogLevel.ERROR,err.toString());
			this.replyError(err);
			return false;
		}

		/* check that the parent directory already exists. check it on all servers */

	//	PadFsLogger.log(LogLevel.ERROR,getPath());
		String parentPath = SystemEnvironment.getParentPath(getPath());
		if(parentPath != null && !parentPath.equals("")){
			String uniqueId = SystemEnvironment.getDirUniqueId(parentPath,getUsernameOwner());
			if(uniqueId == null){
				PadFsLogger.log(PadFsLogger.LogLevel.INFO, "DIR ["+parentPath+"] cannot check if exists");
				defResultMsg.setResult(new RestPut(Rest.status.error, getName(), Rest.errors.networkError));
				return false;
				
			}
				
			if(uniqueId.equals("")){
				PadFsLogger.log(PadFsLogger.LogLevel.INFO, "DIR ["+parentPath+"] does not exist");
				defResultMsg.setResult(new RestPut(Rest.status.error, getName(), Rest.errors.parentDirectoryDoesNotExists));
				return false;
			}
			else{
				PadFsLogger.log(LogLevel.DEBUG, "retrieved parentUniqueId");
				this.uniqueIdParent = uniqueId;
			}
		}
		
		
		Long idTmpFile = null;
		
		fileSize = file.getSize();
		/* upload the file in the temporary folder  and  insert in the database the new record */
		if((idTmpFile = LocalFsManager.uploadFileTMP(getUsernameOwner(),file)) == null){
			system.logger.PadFsLogger.log(LogLevel.ERROR,"ERROR UPLOAD FILE TO TMP!!");
			return false;
		}
		
		/* compute the checksum of the file */
		checksum = Constants.checksum(LocalFsManager.getPhysicalTmpFile(idTmpFile));			
		
		/* retrieve the id list of the replica servers */
		int maxRetriveServer = Constants.replicaNumber * Constants.replicaSearchFactor;
		hostingServerList = SqlManager.getServersLessLoaded(maxRetriveServer);

		if(hostingServerList == null || hostingServerList.size() < Constants.replicaNumber){
					
			system.logger.PadFsLogger.log(LogLevel.ERROR, "Failed to retrieve the replica servers for the op: "+this.getIdOp()+" -> DELETE TMP FILE ");
			if(hostingServerList != null){
				system.logger.PadFsLogger.log(LogLevel.ERROR, hostingServerList.toString());	  
			}	
			else{
				system.logger.PadFsLogger.log(LogLevel.ERROR, "ownerFileListServer is null");	 
			}
			PadFsLogger.log(LogLevel.WARNING, "failed to find the minimum number of servers to upload the file");
			replyError(Rest.errors.failedUploadOnOtherServer); 
			//rimuovere il file temporaneo
			LocalFsManager.deleteTmpFile(getPath(), SqlManager.getIdUser(getUsernameOwner())); 
			return false; 
		}		

		//upload the file on the servers  ownerFileListServer
		hostingServerList = uploadOnOtherServers(hostingServerList,idTmpFile);
		
		//rimuovere il file temporaneo
		if(!LocalFsManager.deleteTmpFile(idTmpFile)){
			PadFsLogger.log(LogLevel.WARNING, "failed to delete the temporary file idTmpFile = "+idTmpFile);
		}
		
		if(hostingServerList == null || hostingServerList.size() < Constants.replicaNumber){
			system.logger.PadFsLogger.log(LogLevel.ERROR, "-- FAILED to transfer the file to the replica servers. op: "+this.getIdOp());
			replyError(Rest.errors.failedUploadOnOtherServer); 
			return false;
		}		
			
		return true;
	}
	
	@Override
	public boolean completeOp() {
		/* 
		 * DO NOT check user permissions. User permissions were checked during prepareOp by only One server.
		 * If all the servers in the group repeat the checkPermission in the completeOp, it may happen that they will evaluate different results
		 * because the checkPermission involve evaluation in other groups. This operation is no more considered serializable in the stream of operations of the whole net
		 * 
		 */
		/*
		Rest.errors err = checkPermission(Constants.RequiredPermissions.Put);
		if(err != null){
			system.PadFsLogger.log(LogLevel.ERROR,err.toString());
			this.replyError(err);
			return false;
		}
		*/

		/* do not check that parent exists. If it is the case this dir will be eventually deleted */

		system.logger.PadFsLogger.log(LogLevel.DEBUG, " ### COMPLETEOP START : "+this.getIdOp()+" ###");
		
		int idOwner = SqlManager.getIdUser(getUsernameOwner());
		Long idFile;
		
		long size = fileSize/1024;
		
		/*
		 * insert in the database the metaInfo of the file.
		 * if the file is already present in the database, it is updated increasing the updateNumber
		 */
		
		idFile = SqlManager.insertMetaInfoWithoutPermission(getPath(), String.valueOf(size), idOwner, getServerLabel(), getHostingServersId(),checksum,getUniqueIdParent());

		if(idFile != null){
			int updatesNumber = SqlManager.getUpdateNumber(idFile);
			Long label = getServerLabel();
		
			if(!Variables.getMerkleTree().addFile(label,updatesNumber,getPath(),fileSize,idOwner)){
				PadFsLogger.log(LogLevel.ERROR, "failed updating MerkleTree");
				return false;
			}
		
			system.logger.PadFsLogger.log(LogLevel.INFO, " ### COMPLETEOP FINITA: "+this.getIdOp());
			system.logger.PadFsLogger.log(LogLevel.DEBUG, "INSERT DB id file: "+idFile);

			MetaInfo m = SqlManager.getMetaInfo(idFile);
			String dateTime = "";
			if(m != null)
				dateTime = m.getDateTime();
			
			//PadFsLogger.log(LogLevel.INFO,String.valueOf(Variables.getMerkleTree()));
			//Variables.getMerkleTree().getUpperTree().getLowerTree().printLabelTree();
			
			notifyPutFile(idOwner,getPath(),getUsernameOwner(),uniqueIdParent,String.valueOf(size),dateTime); //do not check if it completes successfully. it will be executed eventually by other threads
			
			return true;
		}else{
			system.logger.PadFsLogger.log(LogLevel.ERROR, " ### ERROR COMPLETEOP INSERT DB: "+this.getIdOp());
		}


		return false;
	}
	
	private static void notifyPutFile(int idOwner, String path, String username, String parentId,String size, String dateTime) { 
		String directory = SystemEnvironment.getParentPath(path);
		if(directory != null && !directory.equals("")){
			PadFsLogger.log(LogLevel.DEBUG, "notifyPut to servers for file: "+path);

			long label = SystemEnvironment.getLabel(username,directory);
			long[] serverIds = SqlManager.getIdFromConsensusLabel(label);
			
			Iterator<Server> it = (new ConsensusServerGroup(serverIds)).iterator();	
			while(it != null && it.hasNext()){
				Server s = it.next();
				PadFsLogger.log(LogLevel.DEBUG, "notifyPut to server "+s.getId());

				
				Boolean isDir = false;
				
				String url = RestInterface.NotifyPutFile.generateUrl(s.getIp(), s.getPort(),idOwner,size,dateTime,isDir,parentId,path);
				PadFsLogger.log(LogLevel.TRACE, url);
				
				AsyncRestTemplate rt = SystemEnvironment.generateAsyncRestTemplate();
				ListenableFuture<ResponseEntity<RestNotifyPutFileResponse>> reply;
				reply = rt.exchange(url, HttpMethod.GET, null, RestNotifyPutFileResponse.class); //ignore result
				
				reply.addCallback(new ListenableFutureCallback<ResponseEntity<RestNotifyPutFileResponse>>() {
					
					
					@Override
					public void onSuccess(ResponseEntity<RestNotifyPutFileResponse> res) {
						try{
							
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

	public void answer (Rest.status status,Rest.errors error) {
		if(defResultMsg != null){
			if(file != null){
				String name;
				if(this.name != null && !this.name.equals(""))
					name = this.name;
				else
					name = file.getOriginalFilename();
				
				defResultMsg.setResult( 
						
						new RestPut(status, name, error)
					);
			}
			else{
				defResultMsg.setResult( 
						new RestPut(Rest.status.error, null, error)
					);
			}
		}
		else{
			PadFsLogger.log(LogLevel.DEBUG, "do not answer");
		}
	}
	
	@Override
	public void replyOperationCompleted() {
		
		answer(Constants.Rest.status.ok,null);
	}
	
	@Override
	public void replyError(Rest.errors message) {
		
		answer(Constants.Rest.status.error,message);
	}



	@Override
	public boolean actAsAProxy() {
		boolean ret;
		Long id = null;
		if(temporaryUploadedFile == null){
			id = LocalFsManager.uploadFileTMP(getUsernameOwner(), file);
			temporaryUploadedFile = new FileSystemResource(LocalFsManager.getPhysicalTmpFile(id));
			PadFsLogger.log(LogLevel.DEBUG, "temporary file uploaded");
		}
		
		ret = super.actAsAProxy();
		
		
		if(!LocalFsManager.deleteTmpFile(id)){
			PadFsLogger.log(LogLevel.ERROR, "can't delete the temprary uploaded file");
		}
		PadFsLogger.log(LogLevel.DEBUG, "temporary file deleted");
	
		
		return ret;
		
	}
	
	
	public boolean forward(Server s) {
		RestTemplate restTemplate = SystemEnvironment.generateRestTemplate();
		String url;
		MultiValueMap<String,Object> postParam;
		url 	  = RestInterface.Put.generateUrl(s.getIp(),s.getPort());
		postParam = RestInterface.Put.generatePostParameters(this.getUsername(),
															 this.getPassword(),
															 this.getUsernameOwner(),
															 SystemEnvironment.getParentPath(getPath()),
															 this.getName(),
															 temporaryUploadedFile
															 );
		
		PadFsLogger.log(LogLevel.ERROR, "parent:"+SystemEnvironment.getParentPath(getPath()));
	
		HttpHeaders requestHeaders = new HttpHeaders();
		HttpEntity<?> httpEntity = new HttpEntity<Object>(postParam, requestHeaders);

	//	try{
	//		responseFuture = rest.exchange(uri, HttpMethod.POST, httpEntity, RestTransfer.class);
		
		
		try{
			PadFsLogger.log(LogLevel.DEBUG, url);
				
			ResponseEntity<RestPut> response = restTemplate.exchange(url, HttpMethod.POST, httpEntity,RestPut.class);
		//	RestPut response = restTemplate.postForObject(url, postParam, RestPut.class, m);
			
			if(response!= null && response.getBody() != null){
				defResultMsg.setResult(response.getBody());
				return true;
			
			}
		}
		catch(Exception e){
			PadFsLogger.log(LogLevel.WARNING, "communication with server "+s.getId()+" failed");
			PadFsLogger.log(LogLevel.DEBUG, e.getMessage());
			
		}
		
		
		return false;
	}


	private String getName() {
		return name ;
	}


	


	
	
}
