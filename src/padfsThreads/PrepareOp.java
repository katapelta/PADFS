package padfsThreads;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;

import jobManagement.ForwardableInterface;
import jobManagement.jobOperation.JobOperation;
import jobManagement.jobOperation.clientOp.JobClientOp;
import jobManagement.jobOperation.manageOp.ExitOperation;
import jobManagement.jobOperation.serverOp.BootNet;
import restInterface.RestServer;
import system.SystemEnvironment.Constants;
import system.SystemEnvironment.Constants.Rest;
import system.SystemEnvironment.Constants.Rest.errors;
import system.SystemEnvironment.Constants.ServerStatus;
import system.SystemEnvironment.Variables;
import system.consensus.ConsensusServerGroup;
import system.containers.Server;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;
import system.managementOp.SynchGroup;

public class PrepareOp extends Thread{
	private BlockingQueue<JobOperation> inConsOp;
	private BlockingQueue<JobOperation> inOp;
	private boolean receivedExitOperation = false;
	
	public PrepareOp(PriorityBlockingQueue<JobOperation> inOp, PriorityBlockingQueue <JobOperation> inConsOp){
		this.inConsOp = inConsOp;
		this.inOp   = inOp;
	}
	
	public void run() {
		while(!receivedExitOperation){
			try{
				
				/* select next operation */
				JobOperation j= inOp.take();
				
				/* check if it is the exitOperation */
				if(j.getClass().equals(ExitOperation.class)){
					receivedExitOperation = true;
					inConsOp.add(j); 
					continue;
				}
				
				/* check that the serverStatus is READY, 
				 * or that we are in MAINTENANCE_STATE and we have not received a jobClientOp 
				 * or that we are in STARTING and we have received a BOOTNET operation
				 *  */
				ServerStatus myStatus = Variables.getServerStatus();
				if(( myStatus == ServerStatus.MAINTENANCE_STATE  || myStatus == ServerStatus.MAINTENANCE_REQUESTED ) &&
						!JobClientOp.class.isAssignableFrom(j.getClass()) ){
					PadFsLogger.log(LogLevel.TRACE, "operation "+j.getClass()+" can be executed also in MAINTENANCE STATE");
					; /* can continue prepareOp execution*/
				}
				else if(myStatus == ServerStatus.STARTING && j.getClass().equals(BootNet.class)){
					PadFsLogger.log(LogLevel.TRACE, "operation "+j.getClass()+" can be executed in STARTING STATE");
					; /* can continue prepareOp execution*/
				}
				else{
					if(myStatus  != ServerStatus.READY){
						if(myStatus  == ServerStatus.MAINTENANCE_REQUESTED || myStatus  == ServerStatus.MAINTENANCE_STATE){
							j.replyError(Rest.errors.maintenanceMode);
							PadFsLogger.log(LogLevel.DEBUG, "operation rejected: MaintenanceMode. "+j.getClass(), "red", "green", true); 
						}
						else{
							j.replyError(Rest.errors.serverNotReady);
							PadFsLogger.log(LogLevel.DEBUG, "operation rejected: serverNotReady. "+j.getClass(), "red", "green", true);
						}
						continue;
					}
				}
				
				
				
				PadFsLogger.log(LogLevel.DEBUG, "PREPAREOp extracted", "white", "green", true); 
				
				
				/* if it is required to check if forward this operation to some other server AND  if I am not in the consensusGroup for this operation */
				if(j.isForwardingNeeded() && !j.isServerInConsensusGroup(Variables.getServerId())){
						
						/* act as a proxy for the consensusGroup */
						PadFsLogger.log(LogLevel.DEBUG, "act as a proxy for op "+j.getIdOp());		
						ForwardableInterface job = null;
						try{
							 job = (ForwardableInterface) j;
							 job.actAsAProxy();
						}
						catch(Exception e){
							PadFsLogger.log(LogLevel.ERROR, "not forwardable Job: "+j.getIdOp());
						}
						
				}
				else{
					/* no forwarding required */
					
					/* if no consensus is needed for this operation, then MUST check that this server is synchronized in this group */
					if(!j.isConsensusNeeded()){
						/* check idConsRun of the group */
						Server serverSynchronized = null;
						final Server serverToSynch;
						ConsensusServerGroup group = j.consensusGroupServerList();
						Long groupId = group.getConsensusGroupId();
						if(groupId != Constants.globalConsensusGroupId){
							serverSynchronized = RestServer.checkGroupSynchronization(group);
						}
						
						if( serverSynchronized != null){ //this is null if the group is the globlal one, or if we are synchronized in the group
							/* this server is not synchronized, asynchronously forward this operation to one synchronized server in the group */
							try{
								ForwardableInterface job = (ForwardableInterface) j;
								serverToSynch = serverSynchronized;
								new Thread(() -> {
								   job.forward(serverToSynch);
								}).start();
								
							}
							catch(Exception e){
								PadFsLogger.log(LogLevel.DEBUG, "cannot forward the job.");
								j.replyError(errors.internalError);
							}
							SynchGroup.delayedGroupSynch(groupId);
							j = null; /* prevent execution on this server */
						}
						else{
							/* nothing to do. this server is synchronized and can execute prepareOp */
						}
										
					}
					
					/* execute prepareOp */
					if( j != null && j.prepareOp() ){
						PadFsLogger.log(LogLevel.DEBUG, "  ###  OPERATION INITIALIZED - idOperation="+j.getIdOp(), "white", "green", true);
						if(j.isConsensusNeeded()){
							inConsOp.add(j);
						}
					}
					else{
						if(j != null){
							j.replyError(Rest.errors.prepareOpFail);
							PadFsLogger.log(LogLevel.DEBUG, "  ###  prepare op failed - idOperation="+j.getIdOp(),"WHITE","RED",true);
						}
					}
					
					
				}
				
			}catch(Exception e){ 
				PadFsLogger.log(LogLevel.ERROR, e.getClass().getName() + ": " + e.getMessage());
			}
		}
		PadFsLogger.log(LogLevel.INFO, "PrepareOp Thread shutdown");
	}

	
}

