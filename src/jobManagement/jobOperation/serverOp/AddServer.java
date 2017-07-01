package jobManagement.jobOperation.serverOp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.springframework.web.context.request.async.DeferredResult;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import restInterface.RestServer;
import restInterface.manageOp.RestAddServerResponse;
import system.SystemEnvironment.Constants;
import system.SystemEnvironment.Constants.OperationPriority;
import system.SystemEnvironment.Constants.Rest;
import system.SystemEnvironment.Constants.Rest.errors;
import system.SystemEnvironment.Constants.ServerStatus;
import system.containers.Server;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;
import system.managers.SqlManager;
import system.SystemEnvironment.Variables;

public class AddServer extends JobServerOp{
	private Long id;
	private int port;
	private String[] ipList = null;
	private String ip = null;
	
	private long label;
	private int  groupId;
	
	private DeferredResult<RestAddServerResponse> defResult;
	
	public AddServer(Long id,String ipList, int port,DeferredResult<RestAddServerResponse> defResult) {
		super(OperationPriority.ADDSERVER);
		this.id = id;
		this.port = port;
		this.ipList = ipList.split(";");
		this.defResult = defResult;
		
	}
	
	/*
	 * passo agli altri server solo l' indirizzo ip che il primo server ha trovato partendo dall'ipList iniziale.
	 */
	/**
	 * 
	 * @param idOp
	 * @param id   		id server to add
	 * @param ip		ip server to add
	 * @param port		port of the server to add
	 * @param label		
	 * @param groupId
	 */
	@JsonCreator
	public AddServer(	@JsonProperty("idOp") String idOp,
						@JsonProperty("id") Long id,
						@JsonProperty("ip") String ip,
						@JsonProperty("port") int port,
						@JsonProperty("label")long label, 
						@JsonProperty("groupId") int groupId,
						@JsonProperty("idConsRun") Long idConsRun) {
		super(idOp, OperationPriority.ADDSERVER,idConsRun);
		this.id = id;
		this.port = port;
		this.ip = ip;
		this.label = label;
		this.groupId = groupId;
	}
	
	
	
	private String toStringArrayString( String[] arr ){
		StringBuilder elem = new StringBuilder();
		
		for(int i=0; i<arr.length; i++){
			elem.append(arr[i]+",");
		}
		
		return elem.substring(0, elem.length()-1);
	}
	
	
	@Override
	public boolean prepareOp() {
		
		
		
		//check if the client is already in the net, or if it has no id
		if(id == null || id <= 0){
			id = SqlManager.getNextServerId();
			if(id == null){
				replyError(errors.internalError);
				return false;
			}
		}
		else{
			if(SqlManager.checkServerIdExists(id)){
				PadFsLogger.log(LogLevel.INFO, "SERVER ["+id+"] {"+toStringArrayString(ipList)+"}:"+String.valueOf(port)+" ALREADY PRESENT");
				
				/* reply that it is already in the network */
				replyAlreadyInTheNet(id);
				
				/* delete the reference to the deferredResult so that I can not fail answering again */
				defResult = null;
				
				/* return false to prevent the execution of Consensus for a useless operation */
				return false;
			}
		}
		
		/* 
		 * if the client is trying to connect for the first time, check that we are in maintenance mode
		 * if the client is not trying to connect for the first time, and it has successfully connect to the net in the past. then the replyAlreadyInTheNet is called in the previous lines
		 *  
		 */
		if(Variables.getServerStatus() != ServerStatus.MAINTENANCE_STATE){
			PadFsLogger.log(LogLevel.DEBUG, "cannot execute an addServer without being in Maintenance mode");
			replyError(errors.maintenanceModeNeeded);
			return false;
		}
		
		// trovare l'ip giusto nella lista (facendo un restPing)
		PadFsLogger.log(LogLevel.DEBUG, "start AddServer (looking for caller ipAddress) ipList.length="+ipList.length);
		for(int i = 0; i < ipList.length; i++){
			PadFsLogger.log(LogLevel.DEBUG, "  - looking for caller ipAddress: "+ipList[i]);
			if(RestServer.ping(ipList[i],String.valueOf(port))!= null){
				ip = ipList[i];
				break;
			}
		}
		
		if(ip == null){
			PadFsLogger.log(LogLevel.WARNING, "Caller Server not reachable");
			return false;
		}		
		
		// calcolare la label e il groupId 
		long[] labelAndGroupId = SqlManager.findNextLabel();

		if(labelAndGroupId == null)
			return false;
		
		label 	= labelAndGroupId[0];
		groupId = (int) labelAndGroupId[1];
				
		return true;
	}
	
	

	public long getLabel(){
		return label;
	}
	public int getGroupId(){
		return groupId;
	}
	public long getId(){
		return id;
	}
	public String getIp(){
		return ip;
	}
	public int getPort(){
		return port;
	}
	

	@Override
	public boolean completeOp() {
		
		//controllare se le modifiche sono attuabili, se no allora rifare l'operazione ripartendo dalla prepareOp
		// salvare le modifiche nel DB
		// rispondere al mittente
		if(ip==null){
			PadFsLogger.log(LogLevel.WARNING, "ip is null");
			return false;
		}

		ip = RestServer.fixReceivedServerLocalHost(ip);
		
		//check if this serverId already exist in the net
		if(SqlManager.checkServerIdExists(id)){
			PadFsLogger.log(LogLevel.DEBUG, " + SERVER ["+id+"] already in the net. AddServer Ignored");
			return false;
		}
		
		/*
		 * check if this label and groupId are already available
		 * 		if this AddServer is "prepared" before that some other AddServer(s) are completed, 
		 * 		it can be the case that groupId and Label are no more valid.
		 */
		if(!checkGroupAndLabel(groupId,label)){
			PadFsLogger.log(LogLevel.DEBUG, " + SERVER ["+id+"]: groupId and label already present ("+groupId+","+label+")");
			return false;
		}
		


		//add the server to the database and to the Variables
		if(SqlManager.addServerToDB_idGroupLabel(ip, String.valueOf(port), id, groupId, label)){


			Server newServer = new Server(id,ip,String.valueOf(port),Constants.ServerStatus.GLOBAL_SYNCHING,groupId,label);

			Variables.getServerList().add(newServer);
			updateMerkleTree();
			PadFsLogger.log(LogLevel.INFO, " + SERVER ["+id+"] ADDED  ("+ip+":"+port+ "  label:"+label+ "  groupId:"+groupId+")");
			return true;
		}
		else{
			PadFsLogger.log(LogLevel.ERROR, "Failed updating DB: SERVER to Add ["+id+"]  ("+ip+":"+port+ "  label:"+label+ "  groupId:"+groupId+")");
			return false;
		}
			
	}

	/**
	 * check that groupId and label are not already present in the net.
	 * 
	 * @param groupId
	 * @param label
	 * @return false if groupId and label are NOT available
	 * @return true otherwise
	 * 
	 */
	private boolean checkGroupAndLabel(int groupId, long label) {
		Iterator<Server> it = Variables.getServerList().iterator();
		while(it.hasNext()){
			Server s = it.next();
			if(s.getGroupId() == groupId && s.getLabel() == label){
				return false;
			}
		}
		return true;
	}

	private void updateMerkleTree() {
		long label = this.label;
		int groupId = this.groupId;
		long maxRangeLowerThanLabel = 0;
		
		// we create a new version of the list to sort it
		List<Server> serverList = new ArrayList<>(Variables.getServerList());
		
		//sort according to groupId and label
		Collections.sort(serverList, new Server.ServerComparator()) ;
		
		Iterator<Server> it = serverList.iterator();
		while(it.hasNext()){
			Server s = it.next();	
			if(s.getGroupId() == groupId && Long.compareUnsigned(s.getLabel() , maxRangeLowerThanLabel) > 0 
										 && Long.compareUnsigned(s.getLabel() , label) < 0) {
				maxRangeLowerThanLabel = s.getLabel();
				
			}
	
		}
		
		//if no label is found, we have maxLabelLowerThanLabel = 0, otherwise we increment of one
		if(maxRangeLowerThanLabel != 0)
			maxRangeLowerThanLabel++;
		
		PadFsLogger.log(LogLevel.DEBUG, "addServer("+maxRangeLowerThanLabel+","+label+")","green");
		//addServer to merkleTree. it can be already present
		Variables.getMerkleTree().addServer(maxRangeLowerThanLabel, label);
	}

	@Override
	public void replyOperationCompleted() {
		if(defResult == null)
			return;
		PadFsLogger.log(LogLevel.DEBUG, "AddServer completed");

		/**DEPRECATED
		ResultSet r = SqlManager.getActiveServerList(); 
		List<Server> sConf = SqlManager.resultSetServerToList(r);
		*/
		
		List<Server> sConf = Variables.getServerList();
		
	//	PadFsLogger.log(LogLevel.DEBUG, "LISTA INVIATA COME RISPOSTA A RequestAddServer: "+sConf.size()+" -- "+sConf.toString());
		defResult.setResult(new RestAddServerResponse(Rest.status.ok, sConf, this.id, null));
	}

	@Override
	public void replyError(Rest.errors message) {
		if(defResult == null)
			return;

		PadFsLogger.log(LogLevel.DEBUG, "AddServer failed: "+ message);
		if(message == null){
			message = Rest.errors.addServerFailed;
		}
		defResult.setResult(new RestAddServerResponse(Rest.status.error, null, null, message));
	}

	private void replyAlreadyInTheNet(long id) {
		if(defResult == null)
			return;

		List<Server> sConf = Variables.getServerList();
		PadFsLogger.log(LogLevel.DEBUG, "reply that AddServer is not required");
		defResult.setResult(new RestAddServerResponse(Rest.status.error, sConf, id, Rest.errors.serverAlreadyInTheNet));
		
	}
}
