package jobManagement.jobOperation.serverOp;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jobManagement.jobOperation.JobOperation;
import system.SystemEnvironment.Constants.OperationPriority;
import system.consensus.ConsensusServerGroup;
import system.managers.SqlManager;

public abstract class JobServerOp extends JobOperation{

	
	
	public JobServerOp(OperationPriority priority) {
		super(priority);
	}
	
	public JobServerOp(String idOp, OperationPriority priority, Long idConsRun) {
		super(idOp,priority,idConsRun);
	}
	

	@Override
	public abstract boolean prepareOp() ;

	@Override
	public abstract boolean completeOp() ;
	
	@Override
	public boolean doNotTrack() { return false; }

	@Override
	public ConsensusServerGroup consensusGroupServerList() {
		/* return all the servers for a global consensus */
		//if(serverList == null){	
		long[] serverIds;
		ConsensusServerGroup serverList;
		
		serverIds = SqlManager.getAllServerId();
		serverList = new ConsensusServerGroup(serverIds,true);		
		//}	
				
		return serverList;
	}
	
	@JsonIgnore
	@Override
	public boolean isForwardingNeeded(){
		return false;
	}

/*	@Override
	public abstract void replyConsensusAck();

	@Override
	public abstract void replyConsensusNack(String message);
*/
	
}
