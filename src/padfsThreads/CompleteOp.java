package padfsThreads;

import java.util.concurrent.BlockingQueue;

import jobManagement.jobOperation.JobOperation;
import jobManagement.jobOperation.manageOp.ExitOperation;
import system.SystemEnvironment;
import system.SystemEnvironment.Constants.Rest;
import system.consensus.ConsensusServerGroup;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;

public class CompleteOp extends Thread{
	private BlockingQueue<JobOperation> outOp;
	
	public CompleteOp(BlockingQueue<JobOperation> outConsOp){
		this.outOp  = outConsOp;
	}
	
	private boolean receivedExitOperation = false;
	public void run() {
		JobOperation j = null;
		while(receivedExitOperation == false){
			try{
				
				if(outOp.isEmpty()){
					SystemEnvironment.sleepingUntilExitSynchState();
				}
				
				j= outOp.take();
				
				if(j.getClass().equals(ExitOperation.class)){
					receivedExitOperation = true;
					continue;
				}
				
				PadFsLogger.log(LogLevel.DEBUG, "COMPLETEOp extracted", "white", "black", true);
				
				String [] a = j.getClass().getName().split(".");
				String operationName;
				if(a.length>0)
					operationName = a[a.length-1];
				else
					operationName = j.getClass().getName();
				
				
				
				if(j.completeOp()){
					
					operationName = operationName.substring("jobManagement.jobOperation.".length());
					
					PadFsLogger.log(LogLevel.INFO, " - OPERATION EXECUTED - " + operationName + " - idOperation="+j.getIdOp(), "white", "green", true);
					j.replyOperationCompleted();
					PadFsLogger.log(LogLevel.DEBUG, "  ###  reply to client sent - idOperation="+j.getIdOp());


				}
				else{
					j.replyError(Rest.errors.completeOpFail);
					PadFsLogger.log(LogLevel.WARNING, "COMPLETEOp failed - " + operationName + " - idOperation="+j.getIdOp(), "white", "red", true); 
				}

				//add operatIon in both cases otherwise if synch is done we have problem
				ConsensusServerGroup group = j.consensusGroupServerList();
				if(group != null){
					Long consGroupId = group.getConsensusGroupId();
					if(consGroupId != null && consGroupId != SystemEnvironment.Constants.globalConsensusGroupId && !j.doNotTrack()){
						SystemEnvironment.Variables.addJobOperationToGroupList(consGroupId,j);
					}
				}

			}catch(Exception e){ 
				
				PadFsLogger.log(LogLevel.ERROR, e.getMessage());
				try{
					if(j != null){
						j.replyError(Rest.errors.internalError);
					}
				}
				catch(Exception e2){
					PadFsLogger.log(LogLevel.ERROR, "cannot answer to client" + e2.getMessage());;
				}
			}
		}

		PadFsLogger.log(LogLevel.INFO, "CompleteOp Thread shutdown");
		SystemEnvironment.signalExitCompleted();
	}
}
