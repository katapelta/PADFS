package jobManagement.jobOperation.manageOp;

import system.SystemEnvironment.Constants.OperationPriority;
import system.SystemEnvironment.Constants.Rest;

public class NullOperation extends JobInternalOp{
	

	public NullOperation() {
		super(OperationPriority.NULL_OPERATION);
	}

	@Override
	public boolean prepareOp() { return true; }
	

	@Override
	public boolean completeOp() { return true; }


	@Override
	public void replyOperationCompleted() { ; }


	@Override
	public void replyError(Rest.errors message) { ; }

	
}
