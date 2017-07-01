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
import restInterface.op.RestDeldirResponse;
import system.SystemEnvironment;
import system.SystemEnvironment.Constants;
import system.SystemEnvironment.Constants.OperationPriority;
import system.SystemEnvironment.Constants.Rest;
import system.consensus.ConsensusServerGroup;
import system.containers.Server;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;
import system.managers.SqlManager;
import system.SystemEnvironment.Variables;

public class Deldir extends JobClientOp{

	
	private DeferredResult<RestDeldirResponse> defResult;


	public Deldir(String usernameOwner, String path, String username, String password, DeferredResult<RestDeldirResponse> defResult) {
		super(username,password,usernameOwner,path,OperationPriority.DELDIR);
		
		this.defResult	= defResult;
	}

	public void answer(Rest.status status, String name, Rest.errors error){
		if(defResult != null)
			defResult.setResult(
				new RestDeldirResponse(status, name, error)
			);
	}




	@JsonCreator
	public Deldir(@JsonProperty("idOp") 	 String idOp,
				  @JsonProperty("path") 	 String path,
				  @JsonProperty("username") String username,
				  @JsonProperty("usernameOwner") String usernameOwner,
				  @JsonProperty("password") String password,
				  @JsonProperty("idConsRun") Long idConsRun) {
		super(username,password,usernameOwner,path,idOp,OperationPriority.DELDIR,idConsRun);
	}





	@Override
	public boolean prepareOp() {
		Rest.errors err = checkPermission(Constants.RequiredPermissions.Deldir);
		if(err != null){
			replyError(err);
			PadFsLogger.log(LogLevel.ERROR,err.toString());
			return false;
		}
		
		if(SqlManager.checkDirExists(getPath(),getUsernameOwner(),null) == false){
			PadFsLogger.log(PadFsLogger.LogLevel.INFO, "DIR ["+getPath()+"] NOT PRESENT");

			defResult.setResult(new RestDeldirResponse(Rest.status.error, null, Rest.errors.directoryNotFound));

				/* return false to prevent the execution of Consensus for a useless operation */
			return false;
		}


		PadFsLogger.log(PadFsLogger.LogLevel.DEBUG, "start Deldir: "+getPath()+" - "+getUsernameOwner());

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
		Rest.errors err = checkPermission(Constants.RequiredPermissions.Deldir);
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

		if(SqlManager.checkDirExists(getPath(),getUsernameOwner(),null) == false){
			defResult.setResult(new RestDeldirResponse(Rest.status.error, null, Rest.errors.directoryNotFound));
			defResult = null;
			return false;
		}
		
		
		
		Long label = getServerLabel();
		if(Variables.getLabelStart().compareTo(label) <=0 && label.compareTo(Variables.getLabelEnd())<=0){
			
			if(!Variables.getMerkleTree().removeFile(label,0,getPath(),0,SqlManager.getIdUser(getUsernameOwner()))){
				PadFsLogger.log(PadFsLogger.LogLevel.ERROR, "Failed updating MerkleTree");
				return false;
			}
			
			
			if(SqlManager.delDir(getPath(), getUsernameOwner(),getServerLabel())){
				PadFsLogger.log(PadFsLogger.LogLevel.INFO, " + DELDIR ["+getPath()+"] "+getUsernameOwner()+" DONE");
				
				Integer idOwner = SqlManager.getIdUser(getUsernameOwner());
				notifyDeleteFile(idOwner,getPath(),getUsernameOwner()); //do not check if it complete successfully. it will be executed eventually by other threads
								
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

				
				Boolean isDir = true;
				
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
	public void replyOperationCompleted() {
		if(defResult == null)
			return;
		PadFsLogger.log(PadFsLogger.LogLevel.DEBUG, "Deldir completed");

		defResult.setResult(new RestDeldirResponse(Rest.status.ok,getPath(),null));
	}

	@Override
	public void replyError(Rest.errors message) {
		if(defResult == null)
			return;
		PadFsLogger.log(PadFsLogger.LogLevel.DEBUG, "Deldir failed: "+ message);
		defResult.setResult(new RestDeldirResponse(Rest.status.error, getPath(), message));
	}






	
	public boolean forward(Server s) {
		String url = RestInterface.DelDir.generateUrl(s.getIp(), s.getPort(), getUsername(),getPassword(),getUsernameOwner(),getPath());
		RestTemplate restTemplate = SystemEnvironment.generateRestTemplate();
		
		try{
			ResponseEntity<RestDeldirResponse> response;
			response = restTemplate.exchange(url, HttpMethod.GET, null, RestDeldirResponse.class);
			
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
	

}
