package jobManagement.jobOperation.serverOp;

import org.springframework.web.context.request.async.DeferredResult;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import restInterface.manageOp.RestDelUserResponse;
import system.SystemEnvironment.Constants.OperationPriority;
import system.SystemEnvironment.Constants.Rest;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;
import system.managers.SqlManager;

public class DelUser extends JobServerOp{
	private String password;
	private String username;

	private DeferredResult<RestDelUserResponse> defResult;

	public DelUser(String username, String password, DeferredResult<RestDelUserResponse> defResult) {
		super(OperationPriority.DELUSER);
		this.password 	= password;
		this.username 	= username;
		this.defResult	= defResult;
	}

	/**
	 *
	 * @param idOp
	 * @param username
     * @param password
     */
	@JsonCreator
	public DelUser(@JsonProperty("idOp") 	 String idOp,
				   @JsonProperty("username") String username,
				   @JsonProperty("password") String password,
					@JsonProperty("idConsRun") Long idConsRun) {
		super(idOp, OperationPriority.DELUSER,idConsRun);
		this.username = username;
		this.password = password;
	}
	
	

	
	
	@Override
	public boolean prepareOp() {
		

		if(SqlManager.getIdUser(getUsername())<0){
			PadFsLogger.log(LogLevel.INFO, "USER ["+getUsername()+"] NOT PRESENT");

			/* delete the reference to the deferredResult so that I can not fail answering again */
			defResult.setResult(new RestDelUserResponse(Rest.status.error, null, Rest.errors.delUserFailed));


			/* return false to prevent the execution of Consensus for a useless operation */
			return false;
		}
		
		PadFsLogger.log(LogLevel.DEBUG, "start DelUser: "+getUsername()+" - "+username);

		return true;
	}
	
	


	public String getUsername(){
		return username;
	}
	public String getPassword(){
		return password;
	}
	

	@Override
	public boolean completeOp() {
		Integer idUser = null;

		//controllare se le modifiche sono attuabili, se no allora rifare l'operazione ripartendo dalla prepareOp
		// salvare le modifiche nel DB
		// rispondere al mittente
		
		if(username==null || password == null){
			PadFsLogger.log(LogLevel.WARNING, "username is null");
			return false;
		}

		//check if this userId already exist in the net
		idUser=SqlManager.getIdUser(getUsername(),getPassword());
		if( idUser == null || idUser <= 0){
			PadFsLogger.log(LogLevel.DEBUG, " + USER ["+getUsername()+"] NOT PRESENT. DelUser Ignored");
			return false;
		}

		//add the user to the database
		if(SqlManager.delUser(idUser)){
			PadFsLogger.log(LogLevel.INFO, " + USER ["+getUsername()+"] REMOVED");
			return true;
		}
		else{
			PadFsLogger.log(LogLevel.ERROR, "Failed updating DB");
			return false;
		}
			
	}


	@Override
	public void replyOperationCompleted() {
		if(defResult == null)
			return;
		PadFsLogger.log(LogLevel.DEBUG, "DelUser completed");

		defResult.setResult(new RestDelUserResponse(Rest.status.ok,getUsername(),null));
	}

	@Override
	public void replyError(Rest.errors message) {
		if(defResult == null)
			return;
		PadFsLogger.log(LogLevel.DEBUG, "AddServer failed: "+ message);
		defResult.setResult(new RestDelUserResponse(Rest.status.error, null, Rest.errors.delUserFailed));
	}

}
