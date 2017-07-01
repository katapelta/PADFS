package jobManagement.jobOperation.clientOp;

import java.util.Iterator;

import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.async.DeferredResult;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import restInterface.RestInterface;
import restInterface.manageOp.RestNotifyPutFileResponse;
import restInterface.op.RestMkdir;
import system.SystemEnvironment;
import system.SystemEnvironment.Constants;
import system.SystemEnvironment.Constants.OperationPriority;
import system.SystemEnvironment.Constants.Rest;
import system.consensus.ConsensusServerGroup;
import system.containers.MetaInfo;
import system.containers.Server;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;
import system.managers.SqlManager;
import system.SystemEnvironment.Variables;

public class Mkdir extends JobClientOp{
	private DeferredResult<RestMkdir> defResult;

	String uniqueIdParent;

	public Mkdir(String usernameOwner, String path, String username,  String password, DeferredResult<RestMkdir> defResult) {
		super(username,password,usernameOwner,path,OperationPriority.MKDIR);	
		this.defResult	= defResult;
		
	}

	public void answer(Rest.status status, String name, Rest.errors error){
		defResult.setResult(
			new RestMkdir(status, name, error)
		);
	}



	/**
	 *
	 * @param idOp
	 * @param path
	 * @param username
	 */
	@JsonCreator
	public Mkdir(@JsonProperty("idOp") 	 String idOp,
				 @JsonProperty("usernameOwner") String usernameOwner,
				 @JsonProperty("path") 	 String path,
				 @JsonProperty("username") String username,
				 @JsonProperty("password") String password,
				 @JsonProperty("idConsRun") Long idConsRun,
				 @JsonProperty("uniqueIdParent") String uniqueIdParent) {
		super(username,password,usernameOwner, path,idOp,OperationPriority.MKDIR,idConsRun); 
		this.uniqueIdParent = uniqueIdParent;
	}


	@Override
	public boolean prepareOp() {
		/* check user permissions on the parent directory*/
		Rest.errors err = checkPermission(Constants.RequiredPermissions.Mkdir,true);
		if(err != null){
			replyError(err);
			PadFsLogger.log(LogLevel.ERROR,err.toString());
			return false;
		}
		
		/* check that this directory does not exists yet. check it on this server because this server is responsible of this label */
		if(SqlManager.checkDirExists(getPath(),getUsernameOwner(),null)){
			PadFsLogger.log(PadFsLogger.LogLevel.INFO, "DIR ["+getPath()+"] ALREADY PRESENT");
			defResult.setResult(new RestMkdir(Rest.status.error, null, Rest.errors.directoryAlreadyPresent));

			return false;
		}
		
		/* check that the parent directory already exists. check it on all servers */
		String parentPath = SystemEnvironment.getParentPath(getPath());
		PadFsLogger.log(PadFsLogger.LogLevel.DEBUG, "MKDIR path: "+ getPath() + "ParentPath: ["+parentPath+"]");
		if(parentPath != null && !parentPath.equals("")){
			String uniqueId = SystemEnvironment.getDirUniqueId(parentPath,getUsernameOwner());
			if(uniqueId == null){
				PadFsLogger.log(PadFsLogger.LogLevel.INFO, "DIR ["+parentPath+"] cannot check if exists");
				defResult.setResult(new RestMkdir(Rest.status.error, getPath(), Rest.errors.networkError));
				return false;
				
			}
				
			if(uniqueId.equals("")){
				PadFsLogger.log(PadFsLogger.LogLevel.INFO, "DIR ["+parentPath+"] does not exist");
				defResult.setResult(new RestMkdir(Rest.status.error, getPath(), Rest.errors.parentDirectoryDoesNotExists));
				return false;
			}
			else{
				/* set the parentUniqueId before start consensus */
				PadFsLogger.log(LogLevel.DEBUG, "retrieved parentUniqueId: "+uniqueId);
				this.uniqueIdParent = uniqueId;
			}
		}
		

		
		PadFsLogger.log(PadFsLogger.LogLevel.DEBUG, "start Mkdir: "+getPath()+" - "+getUsernameOwner());
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
		 Rest.errors err = checkPermission(Constants.RequiredPermissions.Mkdir);
		
		if(err != null){
			replyError(err);
			PadFsLogger.log(LogLevel.ERROR,err.toString());
			return false;
		}
		*/
		
		if(getPath() == null || getUsernameOwner()==null){
			PadFsLogger.log(PadFsLogger.LogLevel.WARNING, "path or username is null");
			return false;
		}

		if(SqlManager.checkDirExists(getPath(),getUsernameOwner(),null)){
			defResult.setResult(new RestMkdir(Rest.status.error, null, Rest.errors.directoryAlreadyPresent));
			return false;
		}
		
		/* do not check that parent exists. If it is the case, this dir will be eventually deleted */

		Long label = getServerLabel();
		if(Variables.getLabelStart().compareTo(label) <=0 && label.compareTo(Variables.getLabelEnd())<=0){
			Long id = SqlManager.addDir(getPath(), getUsernameOwner(),label,getIdOp(),uniqueIdParent); 
			if(id != null){
				int idOwner = SqlManager.getIdUser(getUsernameOwner());
				if(!Variables.getMerkleTree().addFile(label,0,getPath(),0,idOwner)){
					PadFsLogger.log(LogLevel.ERROR, "failed updating MerkleTree");
					return false;
					
				}
				
				MetaInfo m = SqlManager.getMetaInfo(id);
				String dateTime = "";
				if(m != null)
					dateTime = m.getDateTime();
				
				notifyPutFile(idOwner,getPath(),getUsernameOwner(),uniqueIdParent,"0",dateTime); //do not check if it complete successfully. it will be executed eventually by other threads
				
				
				PadFsLogger.log(PadFsLogger.LogLevel.INFO, " + MKDIR ["+getPath()+"] "+getUsernameOwner()+" => Label: "+label+" DONE");
				return true;
			}
			else{
				PadFsLogger.log(PadFsLogger.LogLevel.ERROR, "Failed updating DB");
				return false;
			}
		}
		else{
			PadFsLogger.log(PadFsLogger.LogLevel.ERROR, "label not managed by this server");
			return false;
		}
	}


	@Override
	public void replyOperationCompleted() {
		if(defResult == null)
			return;
		PadFsLogger.log(PadFsLogger.LogLevel.DEBUG, "Mkdir completed");

		defResult.setResult(new RestMkdir(Rest.status.ok,getPath(),null));
	}

	@Override
	public void replyError(Rest.errors message) {
		if(defResult == null)
			return;
		PadFsLogger.log(PadFsLogger.LogLevel.DEBUG, "Mkdir failed: "+ message);
		defResult.setResult(new RestMkdir(Rest.status.error, null, message));
	}




	public String getUniqueIdParent(){
		return uniqueIdParent;
	}

	
	public boolean forward(Server s) {
		String url = RestInterface.Mkdir.generateUrl( s.getIp(), s.getPort(),getUsername(),getPassword(),getUsernameOwner(),getPath());
		
		RestTemplate restTemplate = SystemEnvironment.generateRestTemplate();
		
		try{
			ResponseEntity<RestMkdir> response;
			response = restTemplate.exchange(url, HttpMethod.GET, null, RestMkdir.class);
			
			if(response!= null && response.getBody() != null){
				defResult.setResult(response.getBody());
				return true;
			}
		}
		catch(Exception e){
			PadFsLogger.log(LogLevel.WARNING, "communication with server "+s.getId()+" failed");
			PadFsLogger.log(LogLevel.DEBUG, e.getMessage());
			
		}
		return false;
	}
	
	
	
	private static void notifyPutFile(int idOwner, String path, String username, String parentId,String size, String dateTime) { 
		path = SystemEnvironment.normalizePath(path);
		String directory = SystemEnvironment.getParentPath(path);
		if(directory != null && !directory.equals("")){
			PadFsLogger.log(LogLevel.DEBUG, "notifyPut to servers for directory: "+path);

			long label = SystemEnvironment.getLabel(username, directory);
			long[] serverIds = SqlManager.getIdFromConsensusLabel(label);
			
			Iterator<Server> it = (new ConsensusServerGroup(serverIds)).iterator();	
			while(it != null && it.hasNext()){
				Server s = it.next();
				PadFsLogger.log(LogLevel.DEBUG, "notifyPut to server "+s.getId());

				
				Boolean isDir = (true);
				
				String url = RestInterface.NotifyPutFile.generateUrl(s.getIp(), s.getPort(), idOwner, size, dateTime, isDir, parentId, path);
				
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

}
