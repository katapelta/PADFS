package jobManagement.jobOperation.serverOp;

import org.springframework.web.context.request.async.DeferredResult;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jobManagement.jobOperation.JobOperation;
import jobManagement.jobOperation.manageOp.MaintenanceFinalize;
import restInterface.op.RestMaintenanceRequest;
import system.SystemEnvironment.Constants.OperationPriority;
import system.SystemEnvironment.Constants.Rest;
import system.SystemEnvironment.Constants.ServerStatus;
import system.SystemEnvironment.Variables;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;

public class MaintenanceRequested extends JobServerOp{
	
	DeferredResult<RestMaintenanceRequest> defResult = null;
	
	public MaintenanceRequested(DeferredResult<RestMaintenanceRequest> defResult) {
		super(OperationPriority.MAINTENANCE_REQUESTED);
		this.defResult = defResult;
	}
	
	@JsonCreator
	public MaintenanceRequested(	@JsonProperty("idOp") String idOp,
						@JsonProperty("idConsRun") Long idConsRun) {
		super(idOp, OperationPriority.MAINTENANCE_REQUESTED,idConsRun);
			
	}
	
	
	@Override
	public boolean prepareOp() { return true; }
		
	
	@Override
	public boolean completeOp() {
			
			
			Variables.setStateBeforeMaintenance(Variables.getServerStatus());
			
			if(!Variables.setServerStatus(ServerStatus.MAINTENANCE_REQUESTED)){
				PadFsLogger.log(LogLevel.ERROR, "cannot put in maintenance state");
				return false;
			}
		
			JobOperation op = new MaintenanceFinalize(defResult);
			Variables.getPrepareOpQueue().add(op);
			
			return true;
			
	}

	@Override
	public void replyOperationCompleted() { 
		; /* do not reply here. the answer is provided by the MaintenanceFinalize operation */
	}
	
	@Override
	public void replyError(Rest.errors message) { 
		if(defResult != null){
			defResult.setResult(new RestMaintenanceRequest(Rest.status.error, message));
		}
	}
	
}
