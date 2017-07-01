package system.managementOp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import jobManagement.jobOperation.JobOperation;
import jobManagement.jobOperation.serverOp.SynchronizationCompleted;
import restInterface.RestInterface;
import restInterface.RestServer;
import restInterface.manageOp.RestGetDirectoryListing;
import restInterface.manageOp.RestGetLowerTreeResponse;
import restInterface.manageOp.RestGetMetaInfo;
import restInterface.manageOp.RestGetUpperTreeResponse;
import restInterface.manageOp.RestGlobalSynchResponse;
import restInterface.manageOp.RestPong;
import system.SystemEnvironment;
import system.SystemEnvironment.Constants;
import system.SystemEnvironment.Constants.Rest;
import system.SystemEnvironment.Constants.ServerStatus;
import system.SystemEnvironment.Variables;
import system.consensus.ConsensusVariables;
import system.containers.DirectoryListingItem;
import system.containers.MetaInfo;
import system.containers.Server;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;
import system.managers.SqlManager;
import system.merkleTree.MerkleTree;
import system.merkleTree.NodeLowerTree;
import system.merkleTree.NodeUpperTree;

public class SynchGlobal extends ManagementOp{
	Server server;
	long globalConsRunIdToSynch = 0;
	ServerStatus completedState;
	/**
	 * 
	 * @param synchServer Server to call for the synchronization
	 */
	public SynchGlobal (Server synchServer,ServerStatus completedState){
		this.server = synchServer;
		this.completedState = completedState;
	}
	
	/**
	 * 
	 * @return true if the server is already in synchronization or if the server has completed this synchronization call
	 * @return false if the synchronization is failed
	 */
	@Override
	public boolean execute() {
		PadFsLogger.log(LogLevel.DEBUG, "[OP - SynchGlobal] START");
		
		/* check that we are not trying to synch with ourself */
		if (server.getId() == Variables.getServerId()){
			PadFsLogger.log(LogLevel.DEBUG, "[OP - SynchGlobal] can't synchronize with myself");
			return false;
		}
		
		
		

		
		
		try{
			
					
			/* retrieve the updated data */
			String request = makeRequest();
			RestGlobalSynchResponse response = sendRequest(request);
			
			PadFsLogger.log(LogLevel.DEBUG, "synch request: "+request);
			
		
			if(		response == null || response.getStatus() == null || 
					response.getStatus() != Rest.status.ok || 
					response.getGlobalConsensusRunId() == 0 || response.getCreatorIpList() == null ||
					response.getCreatorId() == null || response.getCreatorPort() == null || 
					response.getServerList() == null || response.getUserList() == null){
				
				String logServerList,logUserList;
				if(response == null){
					PadFsLogger.log(LogLevel.ERROR, "synch failed: Response is null");
				}
				else{
					if(response.getServerList() == null) logServerList= "null";
					else 							 logServerList= "not null";
					if(response.getServerList() == null) logUserList= "null";
					else 							 logUserList= "not null";
					PadFsLogger.log(LogLevel.ERROR, "synch failed: response.status="+response.getStatus()+"  response.consRunId="+response.getGlobalConsensusRunId()+"  responses.serverList="+logServerList +"  responses.userList="+logUserList);
				}
				
				return false;
			}
		
			
			long myConsensusRunId = Variables.consensusVariableManager.getConsVariables(Constants.globalConsensusGroupId).getIdConsRun();
			if(response.getGlobalConsensusRunId() <= myConsensusRunId){
				PadFsLogger.log(LogLevel.DEBUG, "synch failed: response.consRunId <= myConsensusRunId ("+response.getGlobalConsensusRunId()+" <= " + myConsensusRunId + ")");
				
				
				return false;
			}
			this.globalConsRunIdToSynch = response.getGlobalConsensusRunId();
			
		
		
			
			/* update the user and server database tables and update myLabelStart and myLabelEnd */
			if(! SqlManager.globalConsensusSync(response.getServerList(),response.getUserList(),response.getCreatorIpList(),response.getCreatorPort(), response.getCreatorId())){		
				
				return false;
			}
			
				
			/* exchange merkle tree */
			if(! exchangeMerkleTree()){
				PadFsLogger.log(LogLevel.ERROR,"error retrieving merkle tree");
				
				return false;
			}
		
			
			/* synch file metainfo */
			if(! syncMetaInfo()){
				PadFsLogger.log(LogLevel.ERROR,"error retrieving files metaInformation ");
				
				return false;
			}
			
			
			/* update the globalIdConsensusRun */
			ConsensusVariables glob = Variables.consensusVariableManager.getConsVariables(Constants.globalConsensusGroupId);
			if(! glob.setIdConsRun(response.getGlobalConsensusRunId())){
				
				return false;
			}
			
			PadFsLogger.log(LogLevel.INFO, "Synchronization Completed");
			
			
			Variables.setServerStatus(completedState); //need to set the serverStatus before the synchronizationCompleted creation.
			
			JobOperation job = new SynchronizationCompleted();
			if(!Variables.getPrepareOpQueue().add(job)){
				
				return false;
			}
			
			/* exiting SynchronizationState */
			Variables.setNeedToGlobalSync(false);
		}
		catch(Exception e){
			PadFsLogger.log(LogLevel.ERROR, e.getMessage());
			return false;
		}
		
		
		return true;
	}

	

	private boolean syncMetaInfo() {
		PadFsLogger.log(LogLevel.INFO, "start synching metaInfo");
		long myLabelStart = Variables.getLabelStart();
		long myLabelEnd = Variables.getLabelEnd();
		
		
		
		
		
		/* foreach subInterval of the label space specified in the merkle tree */
		Iterator<long[]> nodeIt = Variables.getMerkleTree().intervalIterator();
		if(nodeIt == null){
			PadFsLogger.log(LogLevel.ERROR, "failed to retrieve interval iterator");
			return false;
		}
		while(nodeIt.hasNext()){
			long arr[] = nodeIt.next();
			if(arr == null || arr.length != 2){
				PadFsLogger.log(LogLevel.ERROR, "wrong interval");
				return false;
			}
			long intervalStart = arr[0];
			long intervalEnd   = arr[1];
			
			

			
			if(Long.compareUnsigned(myLabelStart, intervalStart) <= 0 && Long.compareUnsigned(myLabelEnd, intervalEnd) >= 0  ){
				PadFsLogger.log(LogLevel.DEBUG, "intervalSelected ("+intervalStart+","+intervalEnd+")");
				
				/* get the servers that I can contact */
				List<Server> servList = getServersManagingInterval(intervalStart,intervalEnd);
				if(servList == null){
					PadFsLogger.log(LogLevel.ERROR, "failed to retrieve servers in the interval ("+intervalStart+","+intervalEnd+")");
					return false;
				}
				PadFsLogger.log(LogLevel.DEBUG, "server list managing interval: " + servList.toString());
				
				/* ask metaInfo */
				boolean completedMetaInfo = false;
				boolean completedDirectoryListing = false;
				Iterator<Server> it = servList.iterator();
				while(it.hasNext() && ( !completedMetaInfo || !completedDirectoryListing )){
					Server s = it.next();
					
					/* check I'm not trying to talk with myself */
					if(s.getId() != Variables.getServerId()){
						if(!completedMetaInfo){
							if( updateMetaInfo(s,myLabelStart,myLabelEnd)){
								completedMetaInfo = true;
							}
						}
						if(!completedDirectoryListing){
							 if(updateDirectoryListing(s,myLabelStart,myLabelEnd)){
								 completedDirectoryListing = true;
							 }	
						}
					}
				}
				
				if(!completedMetaInfo){ //do not check for completion of directoryListing. this will be eventually updated
					PadFsLogger.log(LogLevel.ERROR, "failed to retrieve files Meta Information");
					return false;
				}
			}
			else{
				PadFsLogger.log(LogLevel.TRACE, "skip interval not managed ("+intervalStart+","+intervalEnd+")");
			}
		}			
		
		//if all subintervals are synchronized, return true
		return true;
	}

private boolean updateMetaInfo(Server server, long myLabelStart, long myLabelEnd) {
		
		// make url request
		String  req = RestInterface.GetMetaInfo.generateUrl(server.getIp(),server.getPort(),myLabelStart,myLabelEnd);
		if(req == null) {
			PadFsLogger.log(LogLevel.ERROR, "null url generated! check!");
			return false;
		}

		PadFsLogger.log(LogLevel.DEBUG, req);
				
		RestTemplate restTemplate = SystemEnvironment.generateRestTemplate();
		ResponseEntity<RestGetMetaInfo> resp = null;
	
		// contact the server
		try{
			resp = restTemplate.exchange(req, HttpMethod.GET,null, RestGetMetaInfo.class);
		}
		catch(Exception e){
			PadFsLogger.log(LogLevel.WARNING,"impossible to retrieve MetaInfo from "+server.getIp()+":"+server.getPort());
			//e.printStackTrace();
			return false;
		}
		
		//check correctness of response
		if(resp != null && resp.getBody() != null){
			//check if the remote server is out of sync
			long myGlobalConsRunId = Variables.consensusVariableManager.getConsVariables(Constants.globalConsensusGroupId).getIdConsRun();
			if(Long.compareUnsigned(resp.getBody().getConsRunId(),myGlobalConsRunId) < 0){
				PadFsLogger.log(LogLevel.ERROR, "the remote server is out of sync. can't believe its metaInfo list");
				return false;
			}
			
			//retrieve the metaInfoList (it can be empty but not null)
			List<MetaInfo> metaInfoList = resp.getBody().getMetaInfoList();
			if(metaInfoList == null){
				PadFsLogger.log(LogLevel.ERROR, "null metaInfoList found in the answer");
				return false;
			}
			
			if(!SqlManager.truncate("filesManaged")){
				PadFsLogger.log(LogLevel.ERROR, "failed to clean the filesManaged table"); 
				return false;
			}
			
			if(!SqlManager.truncate("host")){
				PadFsLogger.log(LogLevel.ERROR, "failed to clean the host table"); 
				return false;
			}
			
			if(!SqlManager.truncate("grant")){
				PadFsLogger.log(LogLevel.ERROR, "failed to clean the grant table"); 
				return false;
			}

			
			//store metaInfo in the db
			if(!SqlManager.storeMetaInfo(metaInfoList)){
				PadFsLogger.log(LogLevel.ERROR, "failed to store the metaInfoList");
				return false;
			}
			
			PadFsLogger.log(LogLevel.TRACE, "meta info updated"); 
			return true;
		}
		else{
			PadFsLogger.log(LogLevel.ERROR,"null answer arrived retrieving metaInfo from: "+server);
		}

		return false;
	}


	private boolean updateDirectoryListing(Server server, long myLabelStart, long myLabelEnd) {
	
	// make url request
	
	String  req = RestInterface.GetDirectoryListing.generateUrl(server.getIp(),server.getPort(),myLabelStart,myLabelEnd);
	if(req == null) {
		PadFsLogger.log(LogLevel.ERROR, "null url generated! check!");
		return false;
	}

	PadFsLogger.log(LogLevel.DEBUG, req);
			
	RestTemplate restTemplate = SystemEnvironment.generateRestTemplate();
	ResponseEntity<RestGetDirectoryListing> resp = null;

	// contact the server
	try{
		resp = restTemplate.exchange(req, HttpMethod.GET,null, RestGetDirectoryListing.class);
	}
	catch(Exception e){
		PadFsLogger.log(LogLevel.WARNING,"impossible to retrieve directoryListing from "+server.getIp()+":"+server.getPort());
		//e.printStackTrace();
		return false;
	}
	
	//check correctness of response
	if(resp != null && resp.getBody() != null){
		//check if the remote server is out of sync
		long myGlobalConsRunId = Variables.consensusVariableManager.getConsVariables(Constants.globalConsensusGroupId).getIdConsRun();
		if(Long.compareUnsigned(resp.getBody().getConsRunId(),myGlobalConsRunId) < 0){
			PadFsLogger.log(LogLevel.ERROR, "the remote server is out of sync. can't believe its directoryListing list");
			return false;
		}
		
		//retrieve the metaInfoList (it can be empty but not null)
		List<DirectoryListingItem> directoryList = resp.getBody().getDirectoryList();
		if(directoryList == null){
			PadFsLogger.log(LogLevel.ERROR, "null metaInfoList found in the answer");
			return false;
		}
		
		if(!SqlManager.truncate("directoryListing")){
			PadFsLogger.log(LogLevel.ERROR, "failed to clean the directoryListing table"); 
			return false;
		}
		
		//store metaInfo in the db
		if(!SqlManager.storeDirectoryListing(directoryList)){
			PadFsLogger.log(LogLevel.ERROR, "failed to store the directoryList");
			return false;
		}
		
		PadFsLogger.log(LogLevel.TRACE, "directoryListing updated"); 
		return true;
	}
	else{
		PadFsLogger.log(LogLevel.ERROR,"null answer arrived retrieving directoryListing from: "+server);
	}

	return false;
}


	private boolean exchangeMerkleTree() {
			
		PadFsLogger.log(LogLevel.DEBUG, "getUpperTree");
		NodeUpperTree T = getUpperTree();
		if(T == null)
			return false;
		
		long labStart = Variables.getLabelStart();
		long labEnd = Variables.getLabelEnd();
		

		PadFsLogger.log(LogLevel.DEBUG, "getLowerTrees");
		if(!getLowerTrees(T,labStart,labEnd))
			return false;
		
		Variables.setMerkleTree(new MerkleTree(T));
		return true;
	}
	
	private NodeUpperTree getUpperTree(){
		NodeUpperTree ret = null;
		
		List<Server> Servers = Variables.getServerList();

		

		Iterator<Server> i = Servers.iterator();
		
		/*
		 * Send request to other servers until one answer correctly
		 */
		while(ret == null && i.hasNext()){
			Server s = i.next();
			
			/*
			 * check that I'm not the server s
			 */
			if(s.getId() != Variables.getServerId()){
				//PadFsLogger.log(LogLevel.INFO, "server id="+s.getId()+" status="+s.getStatus()+" groupId="+s.getGroupId(), "red");
				ret = sendRequestGetUpperTree(s);
			}
		}	
		
		return ret;
	}
	
	private NodeUpperTree sendRequestGetUpperTree(Server s){
		NodeUpperTree ret = null;
		
		/* send request to server s */
		String url_req = makeRequestGetUpperTree(s);
		RestTemplate restTemplate = SystemEnvironment.generateRestTemplate();
		ResponseEntity<RestGetUpperTreeResponse> resp = null;
	
		PadFsLogger.log(LogLevel.TRACE, "Request MerkleTree.UpperTree to Server "+s.toString());
		PadFsLogger.log(LogLevel.DEBUG, "request url "+url_req);
		try{
			resp = restTemplate.exchange(url_req, HttpMethod.GET,null, RestGetUpperTreeResponse.class);
		}catch(Exception e){
			PadFsLogger.log(LogLevel.ERROR, "Problem retrieving the upperTree");
			return null;
		}
		
		if(resp == null){
			PadFsLogger.log(LogLevel.DEBUG, "Server "+s.toString()+" does not answer");
			return null;
		}
		
		RestGetUpperTreeResponse response = resp.getBody();
		if(response == null){
			PadFsLogger.log(LogLevel.DEBUG, "Server "+s.toString()+" give us a null response");
			return null;
		}
		
		/* check that the ConsensusRunId of the server is greater or equal than previously observed*/
		if(Long.compareUnsigned(response.getConsRunId(), this.globalConsRunIdToSynch) < 0 ){
			PadFsLogger.log(LogLevel.DEBUG, "Server "+s.toString()+" globalConsRunId: "+response.getConsRunId() + "  <  "+ this.globalConsRunIdToSynch + " previously observed globalConsRunId");
			return null;
		}
		/* if the globalConsRunId is greater, will update it */
		if(Long.compareUnsigned(response.getConsRunId(), this.globalConsRunIdToSynch) > 0){
			this.globalConsRunIdToSynch = response.getConsRunId();
		}
		
		ret = response.getUpperTree();
		return ret;
		
	}
	
	
	/* per ogni sotto-intervallo che gestisco (per ogni foglia del upper tree che gestisco) */
			/* per ogni server che gestisce questo sotto-intervallo 
			 *   prova a richiedere il lower tree
			 */
	
	private Boolean getLowerTrees(NodeUpperTree T, long labStart, long labEnd){
		if(T == null)
			return true;
		
		if(T.isLeaf()){
			if(T.getMinValue() >= labStart && T.getMaxValue() <= labEnd){
				// io gestisco questo intervallo, quindi prendo la lista di server che gestisce questo intervallo
				List<Server> servList = getServersManagingInterval(labStart,labEnd);
				Iterator<Server> it = servList.iterator();
				
				Boolean lowerTreeRetrieved = false;
				while(!lowerTreeRetrieved && it.hasNext()){
					Server s = it.next();
					NodeLowerTree lt = null;
					
					/*
					 * check that I'm not the server s
					 */
					if(s.getId() != Variables.getServerId()){
						// ask the lower tree to the server s
						PadFsLogger.log(LogLevel.DEBUG, "asking a lower tree to server "+s.getId());
						PadFsLogger.log(LogLevel.TRACE, "asking a lower tree to  "+s);
						lt = sendRequestGetLowerTree(s,T.getMinValue(),T.getMaxValue());
						
					
						
						// se il server risponde bene, settare il lowerTree in T e uscire dal while
						if(lt != null)	{
							if(T.setLowerTree(lt)){
								lowerTreeRetrieved = true;
								PadFsLogger.log(LogLevel.DEBUG, 
										"lowerTree("+T.getMinValue()+","+T.getMaxValue()+") retrieved");
								PadFsLogger.log(LogLevel.TRACE, 
										"lowerTree("+T.getMinValue()+","+T.getMaxValue()+") retrieved from "+s);
							}
							else{
								PadFsLogger.log(LogLevel.ERROR, 
										"impossible to set the lowerTree("+T.getMinValue()+","+T.getMaxValue()+")");
								return false;
							}
							
						}
					}
						
				}
			
					
				
			}
			return true;
		}
		else{
			if(T.getLeftChild() != null && labEnd <= T.getLeftChild().getMaxValue() ){
				//go only left
				if(! getLowerTrees(T.getLeftChild(),labStart,labEnd)){
					return false;
				}
			}
			else if(T.getRightChild() != null &&  labStart >= T.getRightChild().getMinValue() ){
				//go only right
				if(!getLowerTrees(T.getRightChild(),labStart,labEnd)){
					return false;
				}
			}
			else {
				// go both sides
				
				if(!getLowerTrees(T.getRightChild(),labStart,labEnd)){
					return false;
				}
				if(! getLowerTrees(T.getLeftChild(),labStart,labEnd)){
					return false;
				}
			}
			return true;
		}
		
		
		
	}
	

	/**
	 * get the list of servers managing the specified label interval
	 * 
	 * this function will consider only the servers in READY state.
	 * 
	 * inside a group
	 * 	each server s is responsible for all labels lower than s.label 
	 * 	but higher than other lower server labels
	 * 
	 * it may happen that a server that a server not responsible for the labels in the interval (labStart, labEnd) is inserted in the returned list. 
	 * for example, this happen if this interval si managed by a server that when this function is called, it is in SYNCHING state.
	
	 * @param labStart starting label of the interval
	 * @param labEnd ending label of the interval
	 * @return the list of servers
	 */
	private List<Server> getServersManagingInterval(long labStart,long labEnd) {
		List<Server> retList = new LinkedList<Server>();
		
		// we create a new version of the list to sort it
		List<Server> serverList = new ArrayList<>(Variables.getServerList());
		
		//sort according to groupId and label
		Collections.sort(serverList, new Server.ServerComparator()) ;
		
		
		Iterator<Server> it = serverList.iterator();
		int currentGroupId = -1;
		Boolean finded = true;
		while(it.hasNext()){
			Server s = it.next();
			
			if(s.getStatus().getNumVal() >= ServerStatus.READY.getNumVal()){
				if(s.getGroupId() != currentGroupId){
					currentGroupId = s.getGroupId();
					finded = false;
				}
				if(!finded){
					if(s.getLabel() >= labEnd){
						retList.add(s);
						finded = true;
					}
				}
			}
				
		}
		
		return retList;
	}

	private NodeLowerTree sendRequestGetLowerTree(Server s, long minValue, long maxValue) {
		String url_req = makeRequestGetLowerTree(s,minValue,maxValue);
		
		PadFsLogger.log(LogLevel.TRACE,"Sending lowerTree request TO: "+s.getIp()+":"+s.getPort());
		
		RestTemplate restTemplate = SystemEnvironment.generateRestTemplate();
		ResponseEntity<RestGetLowerTreeResponse> resp = null;
	
		try{
			resp = restTemplate.exchange(url_req, HttpMethod.GET,null, RestGetLowerTreeResponse.class);
		}
		catch(Exception e){
			PadFsLogger.log(LogLevel.ERROR,"impossible to retrieve NodeLowerTree from "+s.getIp()+":"+s.getPort());
			return null;
		}
		
		if(resp == null || resp.getBody() == null){
			PadFsLogger.log(LogLevel.WARNING,"null answer arrived retrieving lowerTree from: "+s);
			return null;
		}
		
		
		/* check that the ConsensusRunId of the server is greater or equal than previously observed*/
		if(Long.compareUnsigned(resp.getBody().getConsRunId(), this.globalConsRunIdToSynch) < 0 ){
			PadFsLogger.log(LogLevel.DEBUG, "Server "+s.toString()+" globalConsRunId: "+resp.getBody().getConsRunId() + "  <  "+ this.globalConsRunIdToSynch + " previously observed globalConsRunId");
			return null;
		}
		/* if the globalConsRunId is greater, will update it */
		if(Long.compareUnsigned(resp.getBody().getConsRunId(), this.globalConsRunIdToSynch) > 0){
			this.globalConsRunIdToSynch = resp.getBody().getConsRunId();
		}
		
		return  resp.getBody().getLowerTree();
	}

	private String makeRequestGetLowerTree(Server server, long minValue, long maxValue) {
		
		String req = RestInterface.GetLowerTree.generateUrl(server.getIp(),server.getPort(),minValue,maxValue);
		
		if(req == null) {
			PadFsLogger.log(LogLevel.ERROR, "null url generated! check!");
			return null;
		}
		return req;
	}

	private String makeRequestGetUpperTree(Server server) {
		if(server == null)
			return null;
		String req = RestInterface.GetUpperTree.generateUrl(server.getIp(),server.getPort());
		if(req == null) {
			PadFsLogger.log(LogLevel.ERROR, "null url generated! check!");
			return null;
		}
		return req;
	}
	

	private String makeRequest() {
		
		String req = RestInterface.GlobalSynchRequest.generateUrl(server.getIp(),server.getPort(),Variables.getServerId());
		if(req == null) {
			PadFsLogger.log(LogLevel.ERROR, "null url generated! check!");
			return null;
		}
		return req;
	}
	

	
	
	private RestGlobalSynchResponse sendRequest(String req){
		
		PadFsLogger.log(LogLevel.DEBUG,"Sending globalSynchRequest TO: "+server.getIp()+":"+server.getPort());
		PadFsLogger.log(LogLevel.TRACE,"Sending globalSynchRequest: "+req);

		RestTemplate restTemplate = SystemEnvironment.generateRestTemplate();
		ResponseEntity<RestGlobalSynchResponse> resp = null;
	
		try{
			resp = restTemplate.exchange(req, HttpMethod.GET,null, RestGlobalSynchResponse.class);
		}
		catch(Exception e){
			PadFsLogger.log(LogLevel.ERROR, "impossible to send globalSynchRequest TO: "+server.getIp()+":"+server.getPort());
			return null;
		}
		
		if(resp != null && resp.getBody() != null){
			return  resp.getBody();
			
		}else{
			return null;
		}
	}
	
	
	public static void delayedGlobalSynch(){
		PadFsLogger.log(LogLevel.TRACE, "SCHEDULING GLOBAL SYNCH");
		new Timer().schedule(new TimerTask() {        
		    @Override
		    public void run() {
		    	PadFsLogger.log(LogLevel.TRACE, "EXECUTING GLOBAL SYNCH");
		    	globalSynch();      
		    }
		}, Variables.getWaitBeforeSynch());
	}
	

	public static boolean globalSynch(){
		return globalSynch(false,ServerStatus.READY);
	}
	
	/**
	 * set the serverStatus to GLOBAL_SYNCHING, then starts the synchronization.
	 * if it returns true, the serverStatus is setted to READY, otherwise it is setted to OUT_OF_SYNCH 
	 * 
	 * 
	 * 
	 * @param STRICT if it is false the fact that no servers are found with an higher globalConsRunId
	 *               than mine is considered as if we are upToDate
	 * @param isAddServer if it is true, the final state is putted in Maintenance
	 *               
	 * @return true if we are upToDate
	 */
	private static boolean globalSynch(boolean STRICT, ServerStatus completedState){
		
		/*if(Variables.getServerStatus().getNumVal() == ServerStatus.READY.getNumVal()){
			PadFsLogger.log(LogLevel.DEBUG, "cannot synch because the server is in "+Variables.getServerStatus()+" state");
			return false;
		}*/
		
		
		/* if the server is already running the synchronization procedure (called by another thread), do not restart it again */
		if(Variables.testAndSetSynchronizationState()){
			PadFsLogger.log(LogLevel.DEBUG, "already in synchronization state");
			return true;
		}
		
		if(! Variables.downgradeServerStatus(ServerStatus.GLOBAL_SYNCHING) ){
			PadFsLogger.log(LogLevel.DEBUG, "cannot synch because this server is in Maintenance State");
			return false;
		}

		
		/*
		 * from now on, the server is in GLOBAL_SYNCHING state
		 * no operation must be considered by the Consensus thread
		 * the completeOp thread must consumes all its input operations
		 */
		if(!SystemEnvironment.waitStopCompleteOpThread()){
			PadFsLogger.log(LogLevel.ERROR, "an error occured while waiting the CompleteOpThread consumes all its operations in queue");
			
			/* exiting SynchronizationState */
			Variables.unsetSynchronizationState();
			SystemEnvironment.restartCompleteOpThread();
			return false;
		}
		
		
		int trial = 0;
		boolean isThereOneServerForSynch = false;
		boolean atLeastOneServerFound = false;
		PadFsLogger.log(LogLevel.DEBUG, "[SYNCH GLOBAL] START");
		try{
		
			while(trial < Variables.getGlobalSynchRetryNumber()){
				long myIdConsRun = Variables.consensusVariableManager.getConsVariables(Constants.globalConsensusGroupId).getIdConsRun();
				
				PadFsLogger.log(LogLevel.DEBUG, "[SYNCH GLOBAL] trial number "+ trial,"black","white",true);
	
				/* retrieve each time the (possible) new activeServerLit ( Heartbeat updates it continuously ) */			
				List<Server> l  = SqlManager.getReadyServerList(true);
				PadFsLogger.log(LogLevel.DEBUG, "[SYNCH GLOBAL] number of possible servers FOR SYNCH: "+l.size(),"black","white",true);
	
				Iterator<Server> i = l.iterator();
	
				while(i.hasNext()){
					
					/* check that we are not synchronized or in maintenance state */
					/*if(Variables.getServerStatus().getNumVal() >= ServerStatus.READY.getNumVal()){
						PadFsLogger.log(LogLevel.DEBUG, "cannot synch because the server is in "+Variables.getServerStatus()+" state");
						return false;
					}*/
					
					
					Server tmpServer = i.next();
					SynchGlobal synch = new SynchGlobal(tmpServer,completedState);
					PadFsLogger.log(LogLevel.DEBUG,"[SYNCH GLOBAL] WITH: " + tmpServer.getIp() + ":" + tmpServer.getPort());
					
					RestPong pong = RestServer.ping(tmpServer.getIp(),tmpServer.getPort());
					if(pong != null && pong.getStatus() != Rest.status.error){
						atLeastOneServerFound = true;
					}
					if(pong != null && pong.getStatus() != Rest.status.error && pong.getIdConsRun() > myIdConsRun){
						isThereOneServerForSynch = true;
						if(synch.execute()){
							PadFsLogger.log(LogLevel.INFO,"[SYNCH GLOBAL] COMPLEATED WITH: " + tmpServer.getIp() + ":" + tmpServer.getPort(),"black","white",true);
							Variables.setServerStatus(completedState);
							
							Variables.setNeedToGlobalSync(false);
							Variables.unsetSynchronizationState();
							SystemEnvironment.restartCompleteOpThread();
							return true;
						}else
							PadFsLogger.log(LogLevel.DEBUG,"[SYNCH GLOBAL] FAILED WITH: " + tmpServer.getIp() + ":" + tmpServer.getPort(),"black","white",true);
					}
					else{
						PadFsLogger.log(LogLevel.DEBUG,"[SYNCH GLOBAL][ping] FAILED WITH: " + tmpServer.getIp() + ":" + tmpServer.getPort(),"black","white",true);
						String str = "";
						if(pong == null)
							str = "pong is null";
						else {
							str = "pong status = "+pong.getStatus() + "  , pong.idConsRun = " + pong.getIdConsRun() + "; myIdConsRun= "+myIdConsRun;
						}
						PadFsLogger.log(LogLevel.DEBUG,str,"red","black",true);
						
					}
				}
	
				trial++;
			}
		
		
			if(!STRICT && !isThereOneServerForSynch){
				/*
				 * there is no one that has a globalConsRunId greater than mine
				 * maybe because the Consensus thread received some OP later than expected. 
				 */
	
				if(atLeastOneServerFound){
					Variables.setServerStatus(completedState);
					
					Variables.setNeedToGlobalSync(false);
					Variables.unsetSynchronizationState();
					SystemEnvironment.restartCompleteOpThread();
					return true;
				}
	
				/*
				 * perché hai scritto queste 3 righe? se STRICT = true e se non abbiamo trovato alcun server raggiungibile vuol dire che siamo isolati nella rete (o che tutti gli altri server sono down) e non siamo sincronizzati con nessuno
				 * 
				Variables.setServerStatus(ServerStatus.READY);
				PadFsLogger.log(LogLevel.DEBUG, "[SYNCH GLOBAL] END: READY","black","white",true);
	
				return true;*/
			}
		}
		finally{
			Variables.unsetSynchronizationState();
			SystemEnvironment.restartCompleteOpThread();
		}
		

		Variables.downgradeServerStatus(ServerStatus.OUT_OF_SYNC);

		PadFsLogger.log(LogLevel.DEBUG, "[SYNCH GLOBAL] END: OUT_OF_SYNC","black","white",true);

		return false;
	}

	public static boolean addServerSynch() {
		/* after exiting maintenance state, the server can be in READY state */
		Variables.setStateBeforeMaintenance(ServerStatus.READY);
		return globalSynch(false,ServerStatus.MAINTENANCE_STATE);
	}
	
	public static boolean getBackInTheNetSynch() {
		ServerStatus completedState;
		if(RestServer.checkMajorityIsMaintenanceState()){
			Variables.setStateBeforeMaintenance(ServerStatus.READY);
			completedState = ServerStatus.MAINTENANCE_STATE;
		}
		else{
			completedState = ServerStatus.READY;
		}
		return globalSynch(false,completedState);
	}
	
}
