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
import restInterface.manageOp.RestNotifyDeleteFileResponse;
import restInterface.op.RestRemove;
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

public class Remove extends JobClientOp{
	private DeferredResult<RestRemove> defResultMsg;

	
	public Remove(DeferredResult<RestRemove> defResultMsg,String usernameOwner, String path, String user, String password){
		super(	user,
				password,
				usernameOwner,												
				SystemEnvironment.getLogicalPath(path),
				OperationPriority.REMOVE);
		this.defResultMsg = defResultMsg;

	}
	
	@JsonCreator
	public Remove(
			@JsonProperty("user") 			String user, 
			@JsonProperty("password") 		String password,
			@JsonProperty("usernameOwner") 			String usernameOwner,  
			@JsonProperty("path") 			String path, 
			@JsonProperty("idOp")			String idOp,
			@JsonProperty("idConsRun") Long idConsRun) {
		super(user,password,usernameOwner, path,idOp,OperationPriority.REMOVE,idConsRun); 	
	}
	
	
	public void answer(Rest.status status, Rest.errors error){
		if(defResultMsg != null)
			defResultMsg.setResult( 
				new RestRemove(status, error)
			);
	}
	
	
	@Override
	public boolean prepareOp() {
		
		//check permissions
		Rest.errors err = checkPermission(Constants.RequiredPermissions.Remove);
		if(err != null){
			system.logger.PadFsLogger.log(LogLevel.ERROR,err.toString());
			this.replyError(err);
			return false;
		}

		
		Integer idOwner = SqlManager.getIdUser(getUsernameOwner());
		
		//check if the file exists
		if(!SqlManager.fileUserExists(getPath(), idOwner)){
			replyError(Rest.errors.fileNotFound);
			PadFsLogger.log(LogLevel.DEBUG, Rest.errors.fileNotFound + ": "+getPath());
			return false;
		}
		
		
		//nothing todo
		return true;
	}


	private void notifyDeleteFile(Integer idOwner, String path, String username) {
		String directory = SystemEnvironment.getParentPath(path);
		if(directory != null && !directory.equals("")){
			PadFsLogger.log(LogLevel.DEBUG, "notifyDelete to servers for file: "+path);
			long label = SystemEnvironment.getLabel(username,directory);
			long[] serverIds = SqlManager.getIdFromConsensusLabel(label);
			
			Iterator<Server> it = (new ConsensusServerGroup(serverIds)).iterator();	
			while(it != null && it.hasNext()){
				Server s = it.next();
				PadFsLogger.log(LogLevel.DEBUG, "notifyDelete to server "+s.getId());

				
				Boolean isDir = false;
				String url = RestInterface.NotifyDeleteFile.generateUrl(s.getIp(), s.getPort(), idOwner,isDir,path);
				PadFsLogger.log(LogLevel.TRACE, url);
				
				AsyncRestTemplate rt = SystemEnvironment.generateAsyncRestTemplate();
				ListenableFuture<ResponseEntity<RestNotifyDeleteFileResponse>> reply;
				reply = rt.exchange(url, HttpMethod.GET, null, RestNotifyDeleteFileResponse.class); //ignore result
				
				reply.addCallback(new ListenableFutureCallback<ResponseEntity<RestNotifyDeleteFileResponse>>() {
					
					
					@Override
					public void onSuccess(ResponseEntity<RestNotifyDeleteFileResponse> res) {
						try{
							
							PadFsLogger.log(LogLevel.DEBUG,	"CALLBACK - RestNotifyDeleteFileResponse messagge SUCCESS" );
						} catch (Exception e) {
							PadFsLogger.log(LogLevel.WARNING,	"CALLBACK - RestNotifyDeleteFileResponse onSuccess FAILURE: "	+ e.getMessage());				
							e.printStackTrace();
						}								
					}

					@Override
					public void onFailure(Throwable e) {
					
						PadFsLogger.log(LogLevel.WARNING,	"CALLBACK - RestNotifyDeleteFileResponse messagge FAILURE");
						PadFsLogger.log(LogLevel.TRACE,	"CALLBACK - RestNotifyDeleteFileResponse messagge FAILURE: "+e.getMessage());
									
					}
				});
				
			}
		}
		
		
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
		Rest.errors err = checkPermission(Constants.RequiredPermissions.Remove);
		if(err != null){
			system.PadFsLogger.log(LogLevel.ERROR,err.toString());
			this.replyError(err);
			return false;
		}
		*/
		
		Integer idOwner = SqlManager.getIdUser(getUsernameOwner());
		
		//check if the file exists
		if(!SqlManager.fileUserExists(getPath(), idOwner)){
			replyError(Rest.errors.fileNotFound);
			PadFsLogger.log(LogLevel.DEBUG, Rest.errors.fileNotFound.toString());
			return false;
		}
		
		MetaInfo f = SqlManager.getManagedFile(idOwner, getPath(),null);
		if(f.isDirectory()){
			replyError(Rest.errors.isDirectory);
			PadFsLogger.log(LogLevel.DEBUG, Rest.errors.isDirectory.toString());
			return false;
		}
		
		if(!Variables.getMerkleTree().removeFile(f.getLabel(), f.getUpdatesNumber(), f.getPath(), Long.valueOf(f.getSize()), f.getIdOwner())){
			replyError(Rest.errors.deleteFailed);
			PadFsLogger.log(LogLevel.ERROR, "impossible to delete file from the Merkle tree");
			return false;
		}
		
		//delete the file
		if(!SqlManager.deleteManagedFile(idOwner,getPath())){
			replyError(Rest.errors.deleteFailed);
			PadFsLogger.log(LogLevel.ERROR, "cannot remove the file");
			return false;
		}
		
		notifyDeleteFile(idOwner,getPath(),getUsernameOwner()); //do not check if it complete successfully. it will be executed eventually by other threads
		
		
		return true;
	}



	@Override
	public void replyOperationCompleted() {
		answer(SystemEnvironment.Constants.Rest.status.ok,null);
	}


	@Override
	public void replyError(Rest.errors message) {
		answer(SystemEnvironment.Constants.Rest.status.error,message);	
	}



	
	public boolean forward(Server s) {
		String url = RestInterface.Remove.generateUrl(s.getIp(), s.getPort(), getUsername(),getPassword(),getUsernameOwner(),getPath());
		RestTemplate restTemplate = SystemEnvironment.generateRestTemplate();
		
		try{
			ResponseEntity<RestRemove> response;
			response = restTemplate.exchange(url, HttpMethod.GET, null, RestRemove.class);
			
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
	
	
}
