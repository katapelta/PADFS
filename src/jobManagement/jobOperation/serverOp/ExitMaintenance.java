package jobManagement.jobOperation.serverOp;

import org.springframework.web.context.request.async.DeferredResult;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import restInterface.op.RestExitMaintenance;
import system.SystemEnvironment.Constants.OperationPriority;
import system.SystemEnvironment.Constants.Rest;
import system.SystemEnvironment.Constants.ServerStatus;
import system.SystemEnvironment.Variables;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;

public class ExitMaintenance extends JobServerOp{
	
	DeferredResult<RestExitMaintenance> defResult = null;
	
	public ExitMaintenance(DeferredResult<RestExitMaintenance> defResult) {
		super(OperationPriority.EXIT_MAINTENANCE);
		this.defResult = defResult;
	}
	
	@JsonCreator
	public ExitMaintenance(	@JsonProperty("idOp") String idOp,
						@JsonProperty("idConsRun") Long idConsRun) {
		super(idOp, OperationPriority.EXIT_MAINTENANCE,idConsRun);
			
	}
	

	@Override
	public boolean prepareOp() { return true; }
		
	
	@Override
	public boolean completeOp() {
			ServerStatus status = Variables.getStateBeforeMaintenance();
			if(status == null){
				status = ServerStatus.UNKNOWN;
			}
			if(!Variables.downgradeServerStatusFromMaintenance(status)){	
				PadFsLogger.log(LogLevel.ERROR, "cannot exit from MaintenanceState");
				return false;
			}
			
			Variables.deleteStateBeforeMaintenance();
			
			return true;
			
	}

	@Override
	public void replyOperationCompleted() { 
		if(defResult != null){
			defResult.setResult(new RestExitMaintenance(Rest.status.ok,null));
		}
	}
	@Override
	public void replyError(Rest.errors message) { 
		if(defResult != null){
			defResult.setResult(new RestExitMaintenance(Rest.status.error,message));
		}
	}

	
}
