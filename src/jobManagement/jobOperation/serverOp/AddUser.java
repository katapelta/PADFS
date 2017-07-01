package jobManagement.jobOperation.serverOp;

import java.util.Iterator;

import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.context.request.async.DeferredResult;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import restInterface.RestInterface;
import restInterface.manageOp.RestAddUserResponse;
import restInterface.op.RestMkdir;
import system.SystemEnvironment;
import system.SystemEnvironment.Constants;
import system.SystemEnvironment.Constants.OperationPriority;
import system.SystemEnvironment.Constants.Rest;
import system.SystemEnvironment.Constants.Rest.errors;
import system.SystemEnvironment.Constants.ServerStatus;
import system.consensus.ConsensusServerGroup;
import system.containers.Server;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;
import system.managers.SqlManager;
import system.SystemEnvironment.Variables;

public class AddUser extends JobServerOp{
	private Long id = null;
	private String password;
	private String username;

	private DeferredResult<RestAddUserResponse> defResult;

	public AddUser( String username, String password, DeferredResult<RestAddUserResponse> defResult) {
		super(OperationPriority.ADDUSER);
		this.password 	= password;
		this.username 	= username;
		this.defResult	= defResult;
	}

	/**
	 *
	 * @param idOp
	 * @param id
	 * @param username
     * @param password
     */
	@JsonCreator
	public AddUser(@JsonProperty("idOp") 	 String idOp,
				   @JsonProperty("id") 		 Long id,
				   @JsonProperty("username") String username,
				   @JsonProperty("password") String password,
					@JsonProperty("idConsRun") Long idConsRun) {
		super(idOp, OperationPriority.ADDUSER,idConsRun);
		this.username = username;
		this.password = password;
		this.id 	  = id;
	}
	
	

	
	
	@Override
	public boolean prepareOp() {
		
		if(Variables.getServerStatus() == ServerStatus.MAINTENANCE_REQUESTED || Variables.getServerStatus() == ServerStatus.MAINTENANCE_STATE){
			PadFsLogger.log(LogLevel.DEBUG, "cannot add an user in maintenance state");
			replyError(errors.maintenanceMode);
			return false;
		}

		if(SqlManager.getIdUser(getUsername()) > 0){
			PadFsLogger.log(LogLevel.INFO, "USER ["+id+"] ["+username+"] ALREADY PRESENT");

			defResult.setResult(new RestAddUserResponse(Rest.status.error, null, Rest.errors.userAlreadyPresent));

				/* return false to prevent the execution of Consensus for a useless operation */
			return false;
		}

		//check userId
		id = SqlManager.getNextUserId();
		if(id == null){
			PadFsLogger.log(LogLevel.INFO, "the generated userId is null");
			return false;
		}
	
		
		PadFsLogger.log(LogLevel.DEBUG, "start AddUser: "+id+" - "+username);

		return true;
	}
	
	


	public Long   getId(){
		return id;
	}
	public String getUsername(){
		return username;
	}
	public String getPassword(){
		return password;
	}
	

	@Override
	public boolean completeOp() {
		
		//controllare se le modifiche sono attuabili, se no allora rifare l'operazione ripartendo dalla prepareOp
		// salvare le modifiche nel DB
		// rispondere al mittente
		
		if(id == null || username==null || password == null){
			PadFsLogger.log(LogLevel.WARNING, "username is null");
			return false;
		}

		//check if this serverId already exist in the net
		if(SqlManager.checkUserIdExists(id)){
			PadFsLogger.log(LogLevel.DEBUG, " + USER ["+id+"] already ADDED. AddUser Ignored");
			return false;
		}

		//add the user to the database
		if(SqlManager.addUser(id, username, password)){
			PadFsLogger.log(LogLevel.INFO, " + USER ["+id+"] "+username+" ADDED");
			return true;
		}
		else{
			PadFsLogger.log(LogLevel.ERROR, "Failed updating DB");
			return false;
		}
			
	}


	@Override
	public void replyOperationCompleted() {
		
		if(defResult == null){
			PadFsLogger.log(LogLevel.DEBUG, "user added - starting creation of directory. NO ANSWER");
			return;
		}
		
		/* mkdir root directory for this user */
		asyncCreationOfDirectory();
		PadFsLogger.log(LogLevel.DEBUG, "user added - starting creation of directory");
		return;
	}
	
	
	private void asyncSendRequest(Iterator<Server> it){
		if(it != null && it.hasNext()){
			Server s = it.next();

			String url = RestInterface.Mkdir.generateUrl( s.getIp(), s.getPort(), username,password,username,Constants.rootDirectory);
			PadFsLogger.log(LogLevel.TRACE, url);
			AsyncRestTemplate restTemplate = SystemEnvironment.generateAsyncRestTemplate();
			
			ListenableFuture<ResponseEntity<RestMkdir>> reply = null;
			reply = restTemplate.exchange(url, HttpMethod.GET,null, RestMkdir.class);
			
			reply.addCallback(new ListenableFutureCallback<ResponseEntity<RestMkdir>>() {
				
				
				@Override
				public void onSuccess(ResponseEntity<RestMkdir> res) {
					try{
						
						if(res.getBody() != null && res.getBody().getStatus() == Rest.status.ok){
							PadFsLogger.log(LogLevel.DEBUG,	"CALLBACK - MKDIR messagge SUCCESS" );
							
							defResult.setResult(new RestAddUserResponse(Rest.status.ok,getId(),null));
						}
						else{
							if(it.hasNext()){
								asyncSendRequest(it);
							}
							else{
								defResult.setResult(new RestAddUserResponse(Rest.status.error,getId(),Rest.errors.addUserOKButNoDirCreated));
							}
						}
					} catch (Exception e) {
						PadFsLogger.log(LogLevel.WARNING,	"CALLBACK - RestReply onSuccess FAILURE: "	+ e.getMessage());				
						//e.printStackTrace();
					}								
				}

				@Override
				public void onFailure(Throwable e) {
					PadFsLogger.log(LogLevel.WARNING, "CALLBACK - MKDIR messagge FAILURE");
					PadFsLogger.log(LogLevel.TRACE,	"CALLBACK - MKDIR messagge FAILURE: " + e.getMessage());
					
					if(it.hasNext()){
						asyncSendRequest(it);
					}
					else{
						defResult.setResult(new RestAddUserResponse(Rest.status.error,getId(),Rest.errors.addUserOKButNoDirCreated));
					}
													
				}
			});
			
			
		}
		else{
			if(it == null){
				PadFsLogger.log(LogLevel.WARNING, "server iterator is null");
			}
			else{
				PadFsLogger.log(LogLevel.ERROR, "Failed to create the root directory for the user '"+username+"'");
			}
		}
	}
	
	private void asyncCreationOfDirectory() {
		
		/* if this server has received the operation from outside */
		if(defResult != null){
			long label = SystemEnvironment.getLabel(username,Constants.rootDirectory);
			long[] serverIds = SqlManager.getIdFromConsensusLabel(label);
			
			Iterator<Server> it = (new ConsensusServerGroup(serverIds)).iterator();	
			asyncSendRequest(it);
		
		}
		
	}

	@Override
	public void replyError(Rest.errors message) {
		if(defResult == null)
			return;
		PadFsLogger.log(LogLevel.DEBUG, "AddServer failed: "+ message);
		defResult.setResult(new RestAddUserResponse(Rest.status.error, null, Rest.errors.addUserFailed));
	}

}
