package jobManagement.jobOperation.clientOp;

import java.util.Iterator;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jobManagement.ForwardableInterface;
import jobManagement.jobOperation.JobOperation;
import system.SystemEnvironment;
import system.SystemEnvironment.Constants.OperationPriority;
import system.SystemEnvironment.Constants.Permission;
import system.SystemEnvironment.Constants.Rest;
import system.consensus.ConsensusServerGroup;
import system.containers.Server;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;
import system.managers.SqlManager;

/**
 * Classe astratta da cui ereditano le clientOp
 */
public abstract class JobClientOp extends JobOperation implements ForwardableInterface {

	private String usernameOwner = null;
	private String username = null; 
	private String password = null; 
	private String path = null;
	private ConsensusServerGroup serverList = null;
	
	/**parte eseguita dal thread prepare op prima del consenso
	 * 
	 * @return true|false if the clientOp is ready to be executed | if something is going wrong (i.e. permision check)
	 * 
	 */
	public abstract boolean prepareOp();
	
	
	
	
	public boolean actAsAProxy() {
		Iterator<Server> it = consensusGroupServerList().iterator();
		
		
		while(it.hasNext()){
			Server s = it.next();
			if(this.forward(s)){
				return true;
			}
			else{
				PadFsLogger.log(LogLevel.WARNING, "failed to forward to server "+s.getId());
			}
		}
		this.replyError(Rest.errors.networkError);
		return false;
		
	}

	JobClientOp(String username, String password, String usernameOwner, String path,OperationPriority priority){
		super(priority);

		this.usernameOwner = usernameOwner;
		this.username = username;
		this.password = password;
		this.path = path;
	}
	
	JobClientOp( String username, String password, String usernameOwner,String path, String idOp,OperationPriority priority, Long idConsRun){
		super(idOp,priority,idConsRun);
		
		this.usernameOwner = usernameOwner;
		this.username = username;
		this.password 	= password;
		this.path 		= path;
	}


	public String getUsernameOwner() {
		return usernameOwner;
	}
	
	public String getUsername() {
		return username;
	}

	public String getPassword() {
		return password;
	}

	public String getPath() {
		return path;
	}

	protected Rest.errors checkPermission(Permission permissionRequired){
		return checkPermission(permissionRequired,false);
	}
	
	
	
	protected Rest.errors checkPermission(Permission permissionRequired, boolean checkOnlyParentPermission){
		Integer idUser = SqlManager.getIdUser(username, password);
		Integer idOwner = SqlManager.getIdUser(usernameOwner);
		PadFsLogger.log(LogLevel.TRACE, "Check permission: user-"+username+"- password-"+password+"- usernameOwner-"+usernameOwner+"- idUser-"+idUser+"- idOwner-"+idOwner);
		
		if(getPath() == null){
			PadFsLogger.log(LogLevel.DEBUG, "path is null");
			return Rest.errors.parameterError;
		}
		
		if(permissionRequired == null){
			PadFsLogger.log(LogLevel.DEBUG, "permissionRequired is null");
			return Rest.errors.internalError;
		}
		
		/* controllo password */
		if(idUser == null || idUser <= 0){
			PadFsLogger.log(LogLevel.DEBUG, "user not found  or  wrong password:-"+username+"-"+password+"-");
			return Rest.errors.wrongUserPassword;
		}
		
		
		if(idOwner != null && idOwner == idUser){
			PadFsLogger.log(LogLevel.TRACE, "the owner has the maximum right on his files"); 
			return null;
		}
		
		Integer idFile = null;
		if(idOwner != null){
			idFile = SqlManager.getIdFile(getPath(), idOwner);
		}
		
		Permission storedPermission = Permission.unset;
		
		if(checkOnlyParentPermission == false){
			/* check path permission locally*/
			
			if(idFile == null || idFile < 0){
				PadFsLogger.log(LogLevel.DEBUG, "file not found");
				return Rest.errors.fileNotFound;
			}
			
			
			PadFsLogger.log(LogLevel.DEBUG, "CHECKING PERMISSION"); 
			storedPermission = SqlManager.getPermission(idUser, idFile);
			if(storedPermission == null){
				return Rest.errors.internalError;
			}
		}
		
		/* if needed, check parent permission */

		if(storedPermission == Permission.unset){
			storedPermission = SystemEnvironment.getParentPermission(idUser,idOwner,path);			
		}
		
		if(storedPermission != null && storedPermission.getNumVal() >= permissionRequired.getNumVal())
			return null;
				
		
		return Rest.errors.permissionDenied;
	}
	
	/**
	 * Return the list of servers of the group that have to manage the 
	 * consensus problem
	 * 
	 * @return null|ServerList 
	 */
	public final ConsensusServerGroup consensusGroupServerList(){
		long serverLabel;
		long[] serverIds;
		
		
		serverLabel = getServerLabel();
		serverIds = SqlManager.getIdFromConsensusLabel(serverLabel);
			
		serverList = new ConsensusServerGroup(serverIds);		
				
		return serverList;
	}
	
	@JsonIgnore
	public long getServerLabel(){
		return SystemEnvironment.getLabel(this.usernameOwner,this.path);
	}
	
	@Override
	public boolean doNotTrack() { return false; }

}
