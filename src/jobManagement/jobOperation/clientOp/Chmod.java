package jobManagement.jobOperation.clientOp;

import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.async.DeferredResult;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import restInterface.RestInterface;
import restInterface.op.RestChmod;
import system.SystemEnvironment;
import system.SystemEnvironment.Constants;
import system.SystemEnvironment.Constants.OperationPriority;
import system.SystemEnvironment.Constants.Permission;
import system.SystemEnvironment.Constants.Rest;
import system.containers.Server;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;
import system.managers.SqlManager;

public class Chmod extends JobClientOp{
	private DeferredResult<RestChmod> defResultMsg;
	Permission permission = null;
	String usernameTarget = null;
	
	/**
	 * 
	 * @param defResultMsg
	 * @param username			username of the user requesting the operation
	 * @param password			password of the user requesting the operation
	 * @param usernameOwner		username of the owner of the file
	 * @param path				path of the file (wrt the owner filesystem hierarchy)
	 * @param usernameTarget	username of the user that will be affected by this operation
	 * @param permission		the new permission to associate to the user "usernameTarget"
	 */
	public Chmod(DeferredResult<RestChmod> defResultMsg,
				 /* login info */
				 String username,
				 String password,
				 
				 /* file info */
				 String usernameOwner,
				 String path,
				 
				 /* permssion info */
				 String usernameTarget,
				 Permission permission){
		super(username,password,usernameOwner,path,OperationPriority.CHMOD);
		PadFsLogger.log(LogLevel.DEBUG, "path is: "+path+ "  owner is:"+usernameOwner+"  label is:"+getServerLabel());
		this.defResultMsg = defResultMsg;
		this.permission = permission;
		this.usernameTarget = usernameTarget;
	}
	
	@JsonCreator
	public Chmod(
			@JsonProperty("usernameOwner")	String usernameOwner, 
			@JsonProperty("usernameTarget")	String usernameTarget, 
			@JsonProperty("username") 		String username, 
			@JsonProperty("permission") 	Permission permission, 
			@JsonProperty("password") 		String password, 
			@JsonProperty("path") 			String path, 
			@JsonProperty("idOp")			String idOp,
			@JsonProperty("idConsRun") Long idConsRun) {
		super(username,password,usernameOwner,path,idOp,OperationPriority.CHMOD,idConsRun); 
		this.permission = permission;
		this.usernameTarget = usernameTarget;
	}
	
	public void answer(Rest.status status,  Rest.errors error){
		if(defResultMsg != null)
			defResultMsg.setResult( 
				new RestChmod(status,  error)
			);
	}


	public Permission getPermission(){
		return permission;
	}
	
	public String getUsernameTarget(){
		return usernameTarget;
	}
	
	@Override
	public boolean prepareOp() {
		
		//check permissions
		Rest.errors err = checkPermission(Constants.RequiredPermissions.Chmod);
		if(err != null){
			replyError(err);
			PadFsLogger.log(LogLevel.DEBUG, err.toString());
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
		Rest.errors err;
		err = checkPermission(Constants.RequiredPermissions.Chmod);
		if(err != null){
			replyError(err);
			PadFsLogger.log(LogLevel.DEBUG, err.toString());
			return false;
		}
		*/

		Integer idOwner = SqlManager.getIdUser(getUsernameOwner());
		Integer idUser  = SqlManager.getIdUser(usernameTarget);
		Integer idFile  = SqlManager.getIdFile(getPath(), idOwner);
		
		if(idUser == null || idUser < 0){
			replyError(Rest.errors.userDoNotExists);
		}
		
		if(idFile == null || idFile < 0){
			replyError(Rest.errors.fileNotFound);
		}
		
		return SqlManager.storePermission(idUser,idFile,permission);
		
	}

	
	@Override
	public void replyOperationCompleted() {
		answer(Constants.Rest.status.ok,null);
	}


	@Override
	public void replyError(Rest.errors message) {
		if(message == null){
			PadFsLogger.log(LogLevel.DEBUG, null);
		}
		else{
			PadFsLogger.log(LogLevel.DEBUG, message.toString());
		}
		answer(Constants.Rest.status.error,message);
	}



	
	public boolean forward(Server s) {
		String url = RestInterface.Chmod.generateUrl(s.getIp(),s.getPort(),
										getUsername(),
										getPassword(),
										usernameTarget,
										getPermission(),
										getUsernameOwner(),
										getPath());

				
		RestTemplate restTemplate = SystemEnvironment.generateRestTemplate();
		
		try{
			ResponseEntity<RestChmod> response;
			response = restTemplate.exchange(url, HttpMethod.GET, null, RestChmod.class);
			
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
