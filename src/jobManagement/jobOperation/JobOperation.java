package jobManagement.jobOperation;

import java.io.IOException;
import java.util.Comparator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import jobManagement.Job;
import jobManagement.jobOperation.clientOp.Chmod;
import jobManagement.jobOperation.clientOp.Deldir;
import jobManagement.jobOperation.clientOp.Get;
import jobManagement.jobOperation.clientOp.List;
import jobManagement.jobOperation.clientOp.Mkdir;
import jobManagement.jobOperation.clientOp.Put;
import jobManagement.jobOperation.clientOp.Remove;
import jobManagement.jobOperation.manageOp.NullOperation;
import jobManagement.jobOperation.serverOp.AddServer;
import jobManagement.jobOperation.serverOp.AddUser;
import jobManagement.jobOperation.serverOp.BootNet;
import jobManagement.jobOperation.serverOp.DelUser;
import jobManagement.jobOperation.serverOp.ExitMaintenance;
import jobManagement.jobOperation.serverOp.MaintenanceRequested;
import jobManagement.jobOperation.serverOp.SynchronizationCompleted;
import jobManagement.jobOperation.serverOp.UpdateMetaInfo;
import system.SystemEnvironment.Constants;
import system.SystemEnvironment.Constants.OperationPriority;
import system.SystemEnvironment.Constants.Rest;
import system.SystemEnvironment.Variables;
import system.consensus.ConsensusServerGroup;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;

public abstract class JobOperation extends Job{
	private static long lastIdOp = 0;
	private OperationPriority priority;
	private Long idConsRun = null;
	
	private synchronized static String nextId() {
		lastIdOp++;
		return Variables.getServerId() + "." + ((Long)lastIdOp).toString();
	}
	
	
	public static JobOperation createFromJson(String jsonOperation,String type) {
		if(jsonOperation == null)
			return null;
		
		Class<?> cl = null;
		
		switch(type){
			case "jobManagement.jobOperation.clientOp.Chmod":
				cl = Chmod.class;
				break;
			case "jobManagement.jobOperation.clientOp.Get":
				cl = Get.class;
				break;
			case "jobManagement.jobOperation.clientOp.List":
				cl = List.class;
				break;
			case "jobManagement.jobOperation.clientOp.Mkdir":
				cl = Mkdir.class;
				break;
			case "jobManagement.jobOperation.clientOp.Deldir":
				cl = Deldir.class;
				break;
			case "jobManagement.jobOperation.clientOp.Put":
				cl = Put.class;
				break;
			case "jobManagement.jobOperation.clientOp.Remove":
				cl = Remove.class;
				break;
			case "jobManagement.jobOperation.serverOp.AddServer":
				cl = AddServer.class;
				break;
			case "jobManagement.jobOperation.serverOp.AddUser":
				cl = AddUser.class;
				break;
			case "jobManagement.jobOperation.serverOp.DelUser":
				cl = DelUser.class;
				break;
			case "jobManagement.jobOperation.serverOp.BootNet":
				cl = BootNet.class;
				break;
			case "jobManagement.jobOperation.serverOp.SynchronizationCompleted":
				cl = SynchronizationCompleted.class;
				break;
			case "jobManagement.jobOperation.serverOp.NullOperation":
				cl = NullOperation.class;
				break;
			case "jobManagement.jobOperation.serverOp.UpdateMetaInfo":
				cl = UpdateMetaInfo.class;
				break;
			case "jobManagement.jobOperation.serverOp.MaintenanceRequested":
				cl = MaintenanceRequested.class;
				break;
			case "jobManagement.jobOperation.serverOp.ExitMaintenance":
				cl = ExitMaintenance.class;
				break;
			default:
				PadFsLogger.log(LogLevel.ERROR, "[JobClientOp] createFromJson - wrongType ");
				return null;
		}
		
		try {	
			PadFsLogger.log(LogLevel.DEBUG, "CREATE FROM JSON ->  jsonOperation: "+jsonOperation);
			JobOperation ret = (JobOperation) new ObjectMapper().readValue(jsonOperation, cl);
			return ret;
		}
		
		catch (IOException e) {
			if(e.getMessage().contains(Constants.wrongOperationDataErrorIpList)){
				PadFsLogger.log(LogLevel.ERROR, Constants.wrongOperationDataErrorIpList);
			}
			else{
				PadFsLogger.log(LogLevel.ERROR, "Class Association JSON DECODE ERROR: " + e.getMessage() + "  the value is: "+jsonOperation);
			}
			return null;
		}
		
	}
	
	
	public String toJSON() {
		ObjectWriter writer = new ObjectMapper().writer();
		try {
			return writer.writeValueAsString(this);
		} catch (JsonProcessingException e) {
			PadFsLogger.log(LogLevel.ERROR,"[toJSON]: " + e.getMessage());
		}
		return null;
	}
	
	
	
	
	public abstract boolean prepareOp();
	//parte da eseguire al termine della fase di consenso
	public abstract boolean completeOp();
	public abstract ConsensusServerGroup consensusGroupServerList();
	//public abstract void replyConsensusAck();
	//public abstract void replyConsensusNack(String message);
	public abstract void replyOperationCompleted();
	public abstract void replyError(Rest.errors message);
	
	private String idOp;
	
	@JsonIgnore
	public JobOperation(OperationPriority priority){
		this.idOp = nextId();
		this.priority= priority;		
	}

	public JobOperation(String idOp,OperationPriority priority,Long idConsRun){
		this.idOp = idOp;
		this.priority= priority;		
		this.idConsRun = idConsRun;
	}
	
	public final String getIdOp(){
		return idOp;
	}
	
	/**
	 * 
	 * @return the idConsRun of the consensus group that approved this operation during the consensus vote
	 * @return null if this operation is still not approved
	 */
	public final Long getIdConsRun(){
		return idConsRun;
	}
	
	public void setIdConsRun(long idConsRun) {
		this.idConsRun = idConsRun;
		
	}
	
	public final OperationPriority getPriority(){
		return priority;
	}
	
    
    public static class  PriorityQueueComparator implements Comparator<JobOperation>{    	
    	public int compare(JobOperation j1, JobOperation j2) {
        	return j1.getPriority().getNumVal() - j2.getPriority().getNumVal();
        }    	
    }


	public boolean isServerInConsensusGroup(long serverId) {
		/* get consensusGroup */
		ConsensusServerGroup group = consensusGroupServerList();
		if(group == null){
			PadFsLogger.log(LogLevel.ERROR, "consensusServerGroup is null");
			return false;
		}
		
		/* check if the group is the global group */
		Long groupId = group.getConsensusGroupId(false);
		if(groupId != null && groupId == Constants.globalConsensusGroupId){
			return true;
		}
		
		/* get the id of servers in the consensusGroup */
		long[] idList = group.getIdList();
		if(idList == null){
			PadFsLogger.log(LogLevel.ERROR, "idList is null");
			return false;
		}

		/* check if serverId is contained in the idList */
		for(int i = 0; i<idList.length; i++){
			if(idList[i] == serverId)
				return true;
		}
		
		return false;
	}

	@JsonIgnore
	public boolean isConsensusNeeded(){
		return true;
	}
	
	@JsonIgnore
	public boolean isForwardingNeeded(){
		return true;
	}

	/**
	 * state if this operation must affect the consensus variables and if it must be inserted in the history of completed operations
	 * @return
	 */
	@JsonIgnore
	public abstract boolean doNotTrack(); 



}
