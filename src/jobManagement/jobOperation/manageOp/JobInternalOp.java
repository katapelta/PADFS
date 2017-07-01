package jobManagement.jobOperation.manageOp;

import jobManagement.jobOperation.JobOperation;
import system.SystemEnvironment.Constants.OperationPriority;
import system.consensus.ConsensusServerGroup;

public abstract class JobInternalOp extends JobOperation{

	
	
	public JobInternalOp(OperationPriority priority) {
		super(priority);
	}
	
	public JobInternalOp(String idOp, OperationPriority priority) {
		super(idOp,priority,null);
	}
	

	@Override
	public abstract boolean prepareOp() ;

	@Override
	public abstract boolean completeOp() ;
	
	@Override
	public ConsensusServerGroup consensusGroupServerList() {
		return null;
	}
	
	@Override
	public boolean doNotTrack() { return true; }
	
	@Override 
	public boolean isForwardingNeeded(){
		return false;
	}
	
}