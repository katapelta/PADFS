package system.managementOp;

import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import jobManagement.jobOperation.JobOperation;
import restInterface.RestInterface;
import restInterface.RestServer;
import restInterface.manageOp.RestGroupSynchResponse;
import restInterface.manageOp.RestPong;
import system.SystemEnvironment;
import system.SystemEnvironment.Constants.Rest;
import system.SystemEnvironment.Constants.ServerStatus;
import system.consensus.ConsensusVariables;
import system.containers.Server;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;
import system.managers.SqlManager;
import system.SystemEnvironment.Variables;

public class SynchGroup extends ManagementOp{
	Server server;
	Long groupId;

	public SynchGroup(Server s,Long groupId) {
		server = s;
		this.groupId = groupId;
	}


	@Override
	public boolean execute() {
		PadFsLogger.log(PadFsLogger.LogLevel.DEBUG, "SynchGROUP START","White","yellow",true);
		
		/* check that we are not trying to synch with ourself */
		if (server.getId() == SystemEnvironment.Variables.getServerId()){
			PadFsLogger.log(PadFsLogger.LogLevel.DEBUG, "can't synchronize with myself","White","yellow",true);
			return false;
		}


		/* if the server is already running the synchronization procedure (called by another thread), do not restart it again */
		if(SystemEnvironment.Variables.testAndSetSynchronizationState()){
			PadFsLogger.log(PadFsLogger.LogLevel.DEBUG, "already in synchronization state","White","yellow",true);
			return true;
		}
		try{
			/* retrieve the updated data */
			String request = makeRequest();
			RestGroupSynchResponse response = sendRequest(request);
	
			PadFsLogger.log(PadFsLogger.LogLevel.DEBUG, "synch request: "+request,"White","yellow",true);


			if(		response == null || response.getStatus() == null ||
					response.getStatus() != SystemEnvironment.Constants.Rest.status.ok ||
					response.getJobOperationList().size() == 0 ){
	
				String logServerList;
	
				if(response.getJobOperationList() == null) logServerList= "null";
				else 							 logServerList= "not null";
	
				PadFsLogger.log(PadFsLogger.LogLevel.ERROR, "GROUP synch failed: response.status="+response.getStatus()
						+"   responses.getJobOperations="+logServerList ,"White","yellow",true);
	
				/* exiting SynchronizationState */
				SystemEnvironment.Variables.unsetSynchronizationState();
				return false;
			}


			long myConsRunId = SystemEnvironment.Variables.consensusVariableManager.getConsVariables(groupId).getIdConsRun();
	
			List<JobOperation> listJob = response.getJobOperationList();
			int i = 0;
			JobOperation currentJob = listJob.get(0);
			
			PadFsLogger.log(LogLevel.TRACE, "myConsRunId: "+myConsRunId+" - first job: "+currentJob.toJSON(),"white","yellow",true);
		
			Long currentIdConsRun = currentJob.getIdConsRun();
			if(currentIdConsRun > myConsRunId){
				PadFsLogger.log(PadFsLogger.LogLevel.ERROR, "GROUP synch ERROR TODO sync global differenza superiore","White","red",true);
			
				SystemEnvironment.Variables.unsetSynchronizationState(); 
				Variables.setNeedToGlobalSync(true);
				return true; //return true because the groupSynch cannot synch without a globalSynch. groupSynch has nothing to do
				
			}else {
				PadFsLogger.log(LogLevel.TRACE, "entering listJob loop of size: "+listJob.size(),"white","yellow",true);
				while ( i < listJob.size()) {
					currentJob = listJob.get(i);
					PadFsLogger.log(LogLevel.TRACE, "job num "+i+": "+currentJob.toJSON(),"white","yellow",true);
					
					currentIdConsRun = currentJob.getIdConsRun();
					if (currentIdConsRun >= myConsRunId) {
						SystemEnvironment.Variables.getCompleteOpQueue().add(currentJob);
						myConsRunId++;
						PadFsLogger.log(LogLevel.INFO,  "GROUP synch ADD JOB "+i+" TO COMPLETEOP QUEUE");
					}
					i++;
				}
			}
		
			/* update the synchId */
			ConsensusVariables synchGroupId = SystemEnvironment.Variables.consensusVariableManager.getConsVariables(groupId);
			if(! synchGroupId.setIdConsRun(myConsRunId)){
				/* exiting SynchronizationState */
				SystemEnvironment.Variables.unsetSynchronizationState();
				PadFsLogger.log(LogLevel.ERROR, "failed updating the consensusGroupId");
				return false;
			}
	
			PadFsLogger.log(LogLevel.INFO, "Synchronization Completed","White","yellow",true);
			SystemEnvironment.Variables.setServerStatus(ServerStatus.READY);
		}
		finally{
			/* exiting SynchronizationState */
			SystemEnvironment.Variables.unsetSynchronizationState();
		}
		
		return true;
	}


	private String makeRequest() {
		List<Server> serverIdList = SqlManager.getServerListConsGroup(groupId);
		
		String req = RestInterface.GroupSynchRequest.generateUrl(server.getIp(),server.getPort(),Variables.getServerId(),serverIdList);
		if(req == null) {
			PadFsLogger.log(PadFsLogger.LogLevel.ERROR, "null url generated! check!");
			return null;
		}
		return req;
	}


	private RestGroupSynchResponse sendRequest(String req){

		PadFsLogger.log(PadFsLogger.LogLevel.DEBUG,"Sending GroupSynchRequest TO: "+server.getIp()+":"+server.getPort(),"White","yellow",true);
		PadFsLogger.log(PadFsLogger.LogLevel.TRACE,"Sending GroupSynchRequest: "+req,"White","yellow",true);

		RestTemplate restTemplate = SystemEnvironment.generateRestTemplate();
		ResponseEntity<RestGroupSynchResponse> resp = null;

		try{
			resp = restTemplate.exchange(req, HttpMethod.GET,null, RestGroupSynchResponse.class);
		}
		catch(Exception e){
			PadFsLogger.log(PadFsLogger.LogLevel.ERROR, "impossible to receive GroupSynchResponse FROM: "+server.getIp()+":"+server.getPort());
			return null;
		}

		if(resp != null && resp.getBody() != null){
			return  resp.getBody();

		}else{
			return null;
		}
	}

	public static void delayedGroupSynch(Long groupId){
		PadFsLogger.log(LogLevel.TRACE, "SCHEDULING GROUP SYNCH");
		new Timer().schedule(new TimerTask() {        
		    @Override
		    public void run() {
		    	PadFsLogger.log(LogLevel.TRACE, "EXECUTING GROUP SYNCH");
		    	executeGroupSynch(groupId);      
		    }
		}, Variables.getWaitBeforeSynch());
	}
	
	private static boolean executeGroupSynch(Long groupId){
		int trial = 0;
		boolean isThereOneServerForSynch = false;
		while(trial < SystemEnvironment.Variables.getRetryNumber()){
			long myIdConsRun = SystemEnvironment.Variables.consensusVariableManager.getConsVariables(groupId).getIdConsRun();

			PadFsLogger.log(PadFsLogger.LogLevel.DEBUG, "groupSynch trial number "+ trial,"White","yellow",true);
			/* retrieve each time the (possible) new activeServerLit ( Heartbeat updates it continuosly ) */
			List<Server> l  = SqlManager.getServerListConsGroup(groupId);
			PadFsLogger.log(PadFsLogger.LogLevel.DEBUG, "number of possible servers FOR groupSynch: "+l.size(),"White","yellow",true);
			Iterator<Server> i = l.iterator();
			while(i.hasNext()){
				Server tmpServer = i.next();
				
				PadFsLogger.log(PadFsLogger.LogLevel.DEBUG,"SynchGroup starting with " + tmpServer.getIp() + ":" + tmpServer.getPort(),"White","yellow",true);
				RestPong pong = RestServer.pingGroup(tmpServer.getIp(),tmpServer.getPort(),groupId);
				if(pong != null && pong.getStatus() != Rest.status.error){
					if(pong.getIdConsRun() > myIdConsRun){
						SynchGroup synch = new SynchGroup(tmpServer,groupId);
						PadFsLogger.log(PadFsLogger.LogLevel.DEBUG,"try SynchGroup with " + tmpServer.getIp() + ":" + tmpServer.getPort(),"White","yellow",true);
						isThereOneServerForSynch = true;
						if(synch.execute()){
							PadFsLogger.log(PadFsLogger.LogLevel.INFO,"SynchGroup completed with " + tmpServer.getIp() + ":" + tmpServer.getPort(),"White","yellow",true);
							return true;
						}else
							PadFsLogger.log(PadFsLogger.LogLevel.DEBUG,"SynchGroup FAILED with " + tmpServer.getIp() + ":" + tmpServer.getPort(),"White","yellow",true);
					}
					else{
						PadFsLogger.log(PadFsLogger.LogLevel.DEBUG,"NO SynchGroup with " + tmpServer.getIp() + ":" + tmpServer.getPort()+" because its groupIdConsRun is not greater than mine","White","yellow",true);
					}
				}
				else{
					PadFsLogger.log(LogLevel.DEBUG, "cannot communicate with server "+tmpServer.getId());
				}

			}
			trial++;
		}

		if(!isThereOneServerForSynch){
			return true;
		}

		return false;
	}
}
 