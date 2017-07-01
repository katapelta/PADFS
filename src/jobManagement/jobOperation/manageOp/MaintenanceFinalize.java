package jobManagement.jobOperation.manageOp;

import org.springframework.web.context.request.async.DeferredResult;

import restInterface.RestServer;
import restInterface.op.RestMaintenanceRequest;
import system.SystemEnvironment.Constants;
import system.SystemEnvironment.Constants.OperationPriority;
import system.SystemEnvironment.Constants.Rest;
import system.SystemEnvironment.Constants.ServerStatus;
import system.SystemEnvironment.Variables;
import system.containers.Server;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;

public class MaintenanceFinalize extends JobInternalOp{
		DeferredResult<RestMaintenanceRequest> defResult;
		public MaintenanceFinalize(DeferredResult<RestMaintenanceRequest> defResult) {
			super(OperationPriority.MAINTENANCE_FINALIZE);
			this.defResult = defResult;
		}
		
	
		@Override
		public boolean prepareOp() { return true; }
			
		
		@Override
		public boolean completeOp() {
				
			
			if(!Variables.setServerStatus(ServerStatus.MAINTENANCE_STATE)){
				PadFsLogger.log(LogLevel.ERROR, "cannot put in maintenance state");
				return false;
			}
			
			return true;
				
		}

		@Override
		public void replyOperationCompleted() { 
			if(defResult != null){
				try {
					Thread.sleep(Constants.waitBeforeCheckMaintenanceState);
				} catch (InterruptedException e) { ; }
				
				Boolean b = checkOtherServersCompletion();
				if(b == null){
					replyError(Rest.errors.cannotCollectAnswers);
				}
				else if(b == false){
					replyError(Rest.errors.notAllServersInMaintenanceMode);
				}
				else{
					defResult.setResult(new RestMaintenanceRequest(Rest.status.ok, null));
				}
			}
		}
		
		/**
		 * ping other servers and update their information
		 * @return true if all other servers are in MaintenanceState
		 * @return false if not all other servers are in MaintenanceState
		 * @return null if at least one server does not answer
		 * 
		 */
		private Boolean checkOtherServersCompletion() {
			for(Server s : Variables.getServerList()){
				boolean b = RestServer.pingUpdateServerKeepAlive(s.getId(), s.getIp(), s.getPort());
				if(b == false){
					return null;
				}
			}
			
			for(Server s : Variables.getServerList()){ //get new serverList
				if(s.getStatus() != ServerStatus.MAINTENANCE_STATE){
					return false;
				}
			}
			
			return true;
		}


		@Override
		public void replyError(Rest.errors message) { 
			if(defResult != null){
				defResult.setResult(new RestMaintenanceRequest(Rest.status.error, message));
			}
		}

		
}
