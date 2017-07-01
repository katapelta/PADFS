package jobManagement.jobOperation.serverOp;

import java.util.Iterator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import system.SystemEnvironment;
import system.SystemEnvironment.Constants.OperationPriority;
import system.SystemEnvironment.Constants.Rest;
import system.SystemEnvironment.Constants.ServerStatus;
import system.SystemEnvironment.Variables;
import system.containers.Server;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;
import system.managers.SqlManager;

public class SynchronizationCompleted extends JobServerOp{
	private Long idServer;
	private ServerStatus status;
	
	
	public SynchronizationCompleted() {
		super(OperationPriority.SYNCHRONIZATION_COMPLETED);
		this.idServer = Variables.getServerId();
		this.status = Variables.getServerStatus();
				
	}
	
	@JsonCreator
	public SynchronizationCompleted(	@JsonProperty("idOp") String idOp,
						@JsonProperty("idServer") Long id,
						@JsonProperty("status") ServerStatus status,
						@JsonProperty("idConsRun") Long idConsRun) {
		super(idOp, OperationPriority.SYNCHRONIZATION_COMPLETED,idConsRun);
		this.idServer = id;
		this.status = status;
	}
	
	
	
	
	
	@Override
	public boolean prepareOp() { return true; }
		
	public long getIdServer(){
		return idServer;
	}
	
	public ServerStatus getStatus(){
		return status;
	}


	@Override
	public boolean completeOp() {
			
		
			boolean sync = false;
			if(!SqlManager.updateServerStatus(idServer, status)){
				PadFsLogger.log(LogLevel.ERROR, "the server "+idServer + "is not in the database");
				sync = true;
			}
			if(!SystemEnvironment.updateServerStatus(idServer, status)){
				PadFsLogger.log(LogLevel.ERROR, "the server "+idServer + "is not in the serverList");
				sync = true;
			}
		
			
			
			//get the label and the groupId of the server with id == idServer
			Long serverLabel= null;
			Integer serverGroupId= null;
			long myServerLabel = Variables.getLabelEnd();
			int myGroupId = Variables.getGroupId();
			Iterator<Server> it = Variables.getServerList().iterator();
			while(it.hasNext()){
				Server s = it.next();
				if(s.getId().equals(idServer)){
					serverLabel = s.getLabel();
					serverGroupId = s.getGroupId();
				}
			}
			if(serverLabel == null){
				PadFsLogger.log(LogLevel.ERROR, "the server "+idServer + "has not a label");
				sync = true;
			}
			if(serverGroupId == null){
				PadFsLogger.log(LogLevel.ERROR, "the server "+idServer + "has not a label");
				sync = true;
			}
			
			
			if(myGroupId == serverGroupId && serverLabel < myServerLabel){
				if(!SqlManager.cleanFilesManaged(serverLabel)){
					PadFsLogger.log(LogLevel.WARNING, "can't clean the database");
				}
			}
			
			SystemEnvironment.updateVariables(Variables.getServerList());
			
			if(sync){
				PadFsLogger.log(LogLevel.INFO, "need a global synchronization");
				/*
				 * the thread completeOp cannot start a synchronization due to an eventual deadlock.
				 * Thus, let the heartbeat thread do it signaling him.
				 */
				
				Variables.setNeedToGlobalSync(true);
				return false;
			}
			
			return true;
			
	}

	@Override
	public void replyOperationCompleted() { ; }
	@Override
	public void replyError(Rest.errors message) { ; }

	
}
