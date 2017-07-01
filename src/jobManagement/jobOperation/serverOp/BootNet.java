package jobManagement.jobOperation.serverOp;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import restInterface.RestServer;
import system.SystemEnvironment;
import system.SystemEnvironment.Constants;
import system.SystemEnvironment.Constants.OperationPriority;
import system.SystemEnvironment.Constants.Rest;
import system.SystemEnvironment.Constants.ServerStatus;
import system.consensus.ConsensusServerGroup;
import system.containers.Server;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;
import system.managers.SqlManager;
import system.SystemEnvironment.Variables;
import system.WrongOperationData;


public class BootNet extends JobServerOp{
	private List<Server> serverList		  = null;//new LinkedList<Server>(Variables.getServerList());
	private List<Server> myServerList	  = null;
	private List<String> creatorIpList	  = Variables.getMyInterfaceIpList();
	private String creatorPort 			  = Variables.getServerPort();
	private String creatorId			  = Long.toUnsignedString(Variables.getServerId());
	
	
   
	public BootNet(List<Server> serverList) {
		super(OperationPriority.BOOTNET);
		this.serverList = new LinkedList<Server>();
		Iterator<Server> it = serverList.iterator();
		
		int i = 0;
		while(i<3 && it.hasNext()){
			this.serverList.add(it.next());
			i++;
		}
		
	}
	/*public BootNet() {
		super(OperationPriority.BOOTNET);
		serverList = new LinkedList<Server>();
		Iterator<Server> it = Variables.getServerList().iterator();
		
		// select only the firsts replicaNumber servers
		int numServers = 0;
		while(it.hasNext() && numServers < Constants.replicaNumber){
			Server s = it.next();
			serverList.add(s);
			numServers++;
		}
		
		creatorIpList = Variables.getMyInterfaceIpList();
		creatorPort = Variables.getServerPort();
		creatorId = Long.toUnsignedString(Variables.getServerId());
	}*/
	
	/*public BootNet(String idOp) {
		super(idOp, OperationPriority.BOOTNET);
	}*/


	@JsonCreator
	public BootNet( @JsonProperty("serverList") 	ArrayList<Server> 	serverList,
					@JsonProperty("idOp") 			String 				idOp,
					@JsonProperty("creatorIpList") 	List<String> 		creatorIpList,
					@JsonProperty("creatorPort") 	String		 		creatorPort,
					@JsonProperty("creatorId") 		String		 		creatorId,
					@JsonProperty("idConsRun") Long idConsRun)throws WrongOperationData {

		
	//	super(idOp, OperationPriority.BOOTNET,serverList,creatorIpList,creatorPort);
		super(idOp,OperationPriority.BOOTNET,idConsRun);
		this.serverList 	= serverList;
		this.creatorIpList 	= creatorIpList;
		this.creatorPort	= creatorPort;
		this.creatorId		= creatorId;
		
		
		myServerList = RestServer.fixReceivedServerList(serverList,creatorIpList,creatorPort,creatorId);
		if(myServerList == null){
			throw new WrongOperationData(Constants.wrongOperationDataErrorIpList);
		}
		//System.out.println("\n\n\n"+myServerList.get(0).getStatus()+"\n\n\n");
	} 
	
	@Override
	public boolean prepareOp() {
		
		// scegliere groupId e label per ogni server
		Iterator<Server> iter = this.serverList.iterator();
		Server current = null;
		
		/* 
		 * the first replicaNumber servers received the label 0.
		 * then the label of other servers is calculated by sql.getNewLabel()
		 */
		int  groupId = 0;
		long label = Constants.maxLabel;
		int j = 0;
		
		PadFsLogger.log(LogLevel.DEBUG, "NUMBER OF SERVERS in the list : "+this.serverList.size());
		while(iter.hasNext()){
			
			// initialize label and groupId of servers 
			if(!(j < Constants.replicaNumber)){ 
				j = 0;
				
				groupId = j;
				label = label/2;
			}
			else{ 
				groupId = j;
			}
			
			current = iter.next();
			current.setGroupId(groupId);
			current.setLabel(label);
			current.setStatus(ServerStatus.READY);
			
			PadFsLogger.log(LogLevel.DEBUG,"idServer: "+Long.toUnsignedString(current.getId())+
					"  label: "+Long.toUnsignedString(label) + "  groupId: "+groupId);
			
			
			
			if( !SqlManager.updateServerLabelGroupId(current.getId(), label, groupId) ){
				PadFsLogger.log(LogLevel.DEBUG,"Error during update server label ");
				return false;
			}
			
			j++;
			
		}

		return true;
	}

	@Override
	public boolean completeOp() {
		if(Variables.isNetworkUp()){
			/* nothing to do, the network is already up */

			Variables.setIsNetworkStarting(false); 
			PadFsLogger.log(LogLevel.DEBUG,"Network already up => Nothing todo. Bootnet ignored");
			return true;
		}
		
		
		//Server.fixReceivedServerList(serverList,creatorIpList,creatorPort);
		
		
		List<Server> correctList = null;
		if(myServerList == null)
			correctList = serverList;
		else
			correctList = myServerList;
		
		//update
		SystemEnvironment.updateVariables(correctList);
		
		// update database
		SqlManager.truncateRepopulateServer(correctList);

		// aggiornare groupId e label per ogni server
		Variables.setServerList(correctList);

		
		//check if I am inside the network created just now  and set the "iAmInTheNet" variable
		if (Variables.getServerId() == null){
			// if I have not an id, set my serverId 
			Long myId = findMyID(correctList);
			if(myId != null){
				Variables.setServerId(myId);
				Variables.setIAmInTheNet(true);
				Variables.setServerStatus(ServerStatus.READY);
			}  
			else{
				PadFsLogger.log(LogLevel.INFO, "BootNet executed, but I am not inside the network: need addServer");
			}
		}
		else{
			long myId = Variables.getServerId();
			if(SqlManager.checkServerIdExists(myId)){
				Variables.setIAmInTheNet(true);
				Variables.setServerStatus(ServerStatus.READY);
			}
		}
		
		

		Variables.setNetworkUp(true);
		Variables.setIsNetworkStarting(false); 
		return true;
	}

	
	private Long findMyID(List<Server> serverList){
		Iterator<Server> i = serverList.iterator();
		while(i.hasNext()){
			Server s = i.next();
			if(Constants.localhostAddresses.contains(s.getIp()) && s.getPort() == Variables.getServerPort())
				return s.getId();
				
		}
		return null;
	}
	
	
	public List<String> getCreatorIpList(){
		return this.creatorIpList;
	}
	
	public String getCreatorPort(){
		return this.creatorPort;
	}
	
	public String getCreatorId(){
		return this.creatorId;
	}
	
	public List<Server> getServerList(){
		return serverList;
	}

	
	@Override
	public ConsensusServerGroup consensusGroupServerList() {
		List<Server> correctList = null;
		if(myServerList == null)
			correctList = serverList;
		else
			correctList = myServerList;
		
		ConsensusServerGroup ret = new ConsensusServerGroup(correctList,true);
			
		return ret;
	}
	
	
	/* noone call this, noone need reply */
	
	@Override
	public void replyOperationCompleted() { ; }

	@Override
	public void replyError(Rest.errors message) { 
		Variables.setIsNetworkStarting(false); 
	}
}
