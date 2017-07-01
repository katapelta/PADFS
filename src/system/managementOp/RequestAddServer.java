package system.managementOp;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.List;

import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import restInterface.RestInterface;
import restInterface.RestServer;
import restInterface.manageOp.RestAddServerResponse;
import system.SystemEnvironment;
import system.SystemEnvironment.Constants;
import system.SystemEnvironment.Constants.Rest;
import system.SystemEnvironment.Constants.ServerStatus;
import system.SystemEnvironment.Variables;
import system.containers.Server;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;
import system.managers.SqlManager;


public class RequestAddServer extends ManagementOp{
	List<Server> serverList = null;
	boolean alreadyInTheNet = false;
	static int retry = Constants.maxRetryNumber;
	
	
	public RequestAddServer(List<Server> serverList){
		this.serverList = serverList;
		
		
	}
	
	public boolean execute() {
		
		/* Request maintencance state */
		/*
		if(serverList == null){
			PadFsLogger.log(LogLevel.ERROR, "serverList is null");
			return false;
		}
		Iterator<Server> it = serverList.iterator();
		boolean maintenanceRequested = false;
		while(it.hasNext() && !maintenanceRequested){
			Server s = it.next();
			String url = RestInterface.GoToMaintenance.generateUrl(s.getIp(),s.getPort());
			RestTemplate restTemplate = SystemEnvironment.generateRestTemplate();
			
			ResponseEntity<RestMaintenanceRequest> resp = null;
			
			try{
				resp = restTemplate.exchange(url, HttpMethod.GET,null, RestMaintenanceRequest.class);
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.WARNING, "impossible to communicate with "+s.getIp()+":"+s.getPort());
				resp = null;
			}
			if(resp != null && resp.getBody()!= null && resp.getBody().getStatus().equals(Rest.status.ok)){
				maintenanceRequested = true;
			}
					
		}

		if(maintenanceRequested == false){
			PadFsLogger.log(LogLevel.ERROR, "Impossible to put in MaintenanceState");
			return false;
		}
		*/
		
		/* preparing REST request */
		
		List<Server> serverTable = null;
		
		/* sending REST request to other servers */
		if( (serverTable=sendRequestToServers()) != null ){
			//PadFsLogger.log(LogLevel.DEBUG, "ADD SERVER ESEGUITA SERVERS: "+serverTable.size()+" \n\n "+serverTable.toString());
			
			//update Variables
			Variables.setServerList(serverTable);
			SystemEnvironment.updateVariables(serverTable);
			
			// update DB
			SqlManager.truncateRepopulateServer(serverTable);
			
			boolean ret;
			if(alreadyInTheNet){
				ret = SynchGlobal.getBackInTheNetSynch();
			}
			else{
				ret = SynchGlobal.addServerSynch();
			}
			
			if(ret){  
				PadFsLogger.log(LogLevel.INFO, "addServer Eseguita");
				system.logger.PadFsLogger.log(LogLevel.INFO, "*******Network is UP**************");
				return true;
			}
			else{
				retry--;
				if(retry <= 0){
					system.logger.PadFsLogger.log(LogLevel.FATAL, "cannot add this server to the net");
					return false;
				}

				system.logger.PadFsLogger.log(LogLevel.WARNING, "Synch Failed, retry other "+retry+ " times");
			}
			
			PadFsLogger.log(LogLevel.WARNING, "addServer Eseguita MA synch fallita");
			return false;
		}
		
		if(!(Variables.getIAmInTheNet() || Variables.getServerStatus() == ServerStatus.GLOBAL_SYNCHING)){
			/*
			 * if the CompleteOp thread has executed a BootNet proposed by someone else, 
			 * it can be possible that we are trying to do a RequestAddServer while we are already in the net
			 * 
			 * if it is not this case, we print a log because an error is occurred
			 */
			PadFsLogger.log(LogLevel.WARNING, "addServer Fallita");
		}
			
		return false;
		
	}

	
	private List<Server> sendRequestToServers(){
		int trial = 0;
		List<Server> serverTable = null;
		/* retry at max retryNumber times to contact each server */
		if(serverList == null){
			PadFsLogger.log(LogLevel.ERROR,"serverList is null");
			return null;
		}
		String request = null;
		String idToSend = "0";
		String send_id =null, send_port =null, send_ipList=null, send_configIp = null;


		while(trial < Variables.getRetryNumber()){
			
			/* retrieve each time the (possible) new activeServerLit ( Heartbeat updates it continuosly ) */		
			Iterator<Server> i = serverList.iterator();

			try {
				if(Variables.getServerId() != null){
					idToSend = Long.toUnsignedString(Variables.getServerId());
				}

				send_id 		= URLEncoder.encode(idToSend, "UTF-8");
				send_port 	 	= URLEncoder.encode(String.valueOf(Variables.getServerPort()), "UTF-8");
				send_ipList 	= URLEncoder.encode(String.valueOf(Variables.getMyInterfaceIpListToString()) , "UTF-8");
				send_configIp 	= Variables.getConfigServerIP();


					
				if(send_ipList == null || send_ipList.length() == 0){
					PadFsLogger.log(LogLevel.WARNING, "No server adresses available (maybe you are not connected in any net). try with localhost address.");
					send_ipList = Constants.localhost;
				}
				

				
				if(send_ipList != null && send_ipList.length()>0 && !send_configIp.equals("*"))
					send_ipList = URLEncoder.encode(send_configIp+";","UTF-8")+send_ipList;
				

			} catch (UnsupportedEncodingException e) {
				PadFsLogger.log(LogLevel.ERROR, "Malformed URL: addServer/"+send_id+"/"+send_ipList+"/"+send_port);
			}


			while(i.hasNext()){ 
				Server tmpServer = i.next();

				String ip 	= tmpServer.getIp();
				String port = tmpServer.getPort();

				//GENERATE THE URL FOR THE REQUEST FOR THE SELECTED SERVER
				request = RestInterface.AddServer.generateUrl(ip,port, send_id, send_port, send_ipList);
										
				PadFsLogger.log(LogLevel.DEBUG," -- RequestAddServer TO: "+ip+":"+port+" -  trial:"+trial);
				if( (serverTable = sendRequest(request,ip,port)) != null ){ 
				
					PadFsLogger.log(LogLevel.DEBUG," -- RequestAddServer SUCCEDED TO: "+ip+":"+port+" - trial:"+trial);
					return serverTable;
				}
				else{
					if(Variables.getIAmInTheNet()){
						/*
						 * if the CompleteOp thread has executed a BootNet, it can be the case that we are already in the net  
						 */
						return null;
					}
				}
			} 
			trial++;
		}		
		return null;
	}
	
	
	
	
	
	private List<Server> sendRequest(String req, String ip,String port){

		PadFsLogger.log(LogLevel.DEBUG,"Sending AddServerRequest TO: "+ip+":"+port);
		PadFsLogger.log(LogLevel.TRACE,"Sending AddServerRequest: "+req);
		
		RestTemplate restTemplate = SystemEnvironment.generateRestTemplate();
		ResponseEntity<RestAddServerResponse> resp = null;
	
		try{
			resp = restTemplate.exchange(req, HttpMethod.GET,null, RestAddServerResponse.class);
		}
		catch(Exception e){
			PadFsLogger.log(LogLevel.WARNING, "impossible to communicate with "+ip+":"+port);
			return null;
		}
		if(resp != null)
			PadFsLogger.log(LogLevel.DEBUG, "SERVER: "+ip+":"+port+" => RISPOSTA: "+resp.getStatusCode().value());

		alreadyInTheNet = false;
		boolean myIdExist = false;
		
		// if the answer is "already in the net"
		if(resp != null && resp.getBody() != null && resp.getBody().getStatus() == Constants.Rest.status.error
				&& resp.getBody().getError().equals(Constants.Rest.errors.serverAlreadyInTheNet)){
			
			List<Server> serverConfig = resp.getBody().getServerConfiguration();
			long messageServerId = resp.getBody().getIdServer();
			
			long myId;
			if(Variables.getServerId() != null ){
				myId = Variables.getServerId();
				
				//check if the "already existing server" has really my id
				if(myId != messageServerId ){
					//wrong answer from the server: ignore it
					PadFsLogger.log(LogLevel.ERROR, "Wrong answer from server "+ip+":"+port);
					return null;
				}
			

				//check if it exists some one with my same id and I can not join the network
				//or if I am already in the network and I do not need to execute anything
				
				Iterator<Server> it = serverConfig.iterator();
				while(it.hasNext() && !myIdExist){
					Server s = it.next();
					if(s.getId() == myId){
						myIdExist = true;
						
						// The id myId is already present in the net. check if the network have added me and I miss the answer
						PadFsLogger.log(LogLevel.DEBUG, "The id ["+myId+"] is already present in the net. checking...");
						
						if(Variables.getMyInterfaceIpList().contains(s.getIp()) || 
							Constants.localhostAddresses.contains(s.getIp())){
							
							if(Variables.getServerPort().equals(s.getPort())){
								//yes I am
								
								if(Variables.getIAmInTheNet()){
									// yes I am: nothing to do
									PadFsLogger.log(LogLevel.DEBUG, "I am already in the net. STOP RequestAddServer");
									return null;
								}
								else{
									//yes I am: need to run RequestAddServer procedure
									alreadyInTheNet = true;
									PadFsLogger.log(LogLevel.DEBUG, " Yes, I am already in the net, completing addserver...","green","red");
									
								}
							}				
						}
					}
				}
				
				if(myIdExist && !alreadyInTheNet){
					PadFsLogger.log(LogLevel.FATAL, "The id ["+myId+"] is already in the net. Something wrong...");
					return null;
				}
			}
			
			
		}
		else if(resp != null && resp.getBody() != null && resp.getBody().getStatus() == Rest.status.error && 
				resp.getBody().getError() == Rest.errors.maintenanceModeNeeded){
			PadFsLogger.log(LogLevel.FATAL, "padfs must be in maintenence mode to add a server");
			return null;
		}
		
		if((resp != null && resp.getBody() != null && resp.getBody().getStatus() == Constants.Rest.status.ok)
			||
			(myIdExist && alreadyInTheNet)
				){
			/* set the server variables assigned by the network */
			List<Server> serverConfig = resp.getBody().getServerConfiguration();
			
			if(serverConfig == null || serverConfig.size() < Constants.replicaNumber){
				PadFsLogger.log(LogLevel.WARNING, "received uncorrect serverList by requestAddServer response from server:"+ip+":"+port);
				if(serverConfig != null){
					PadFsLogger.log(LogLevel.WARNING, "\t SERVER LIST: "+serverConfig.toString());
				}
				return null;
			}
			
			//PadFsLogger.log(LogLevel.DEBUG, "\t=> STATUS: "+resp.getBody().getStatus());
			/*
			 * if the user have inserted an id in the config file 
			 * 		check if the net has provided us the same id.
			 * otherwise
			 * 		set the serverId as the one provided by the net
			 */
			long messageServerId = resp.getBody().getIdServer();
			if(Variables.getServerId() != null){
				if( messageServerId != Variables.getServerId()){
					PadFsLogger.log(LogLevel.FATAL, "id server mismatch!");
					return null;
				}
			}
			else{
				if(!Variables.setServerId(messageServerId)){
					PadFsLogger.log(LogLevel.FATAL, "cannot set serverId");
					return null;
				}
			}
			
			String creatorId = Long.toUnsignedString(resp.getBody().getIdServer());
			String creatorIp = ip;
			String creatorPort = port;
			serverConfig = RestServer.fixReceivedServerList(serverConfig, creatorIp, creatorPort, creatorId);
			return serverConfig;
		}else{
			if(resp == null){
				PadFsLogger.log(LogLevel.WARNING, "resp is null");
			}
			else if(resp.getBody() == null){
				PadFsLogger.log(LogLevel.WARNING, "resp.body is null");
			}
			else{
				PadFsLogger.log(LogLevel.WARNING, "status: "+resp.getBody().getStatus()+ "  error: "+resp.getBody().getError());
			}
			PadFsLogger.log(LogLevel.WARNING," resp || resp.body  AddServerRequest NULL - REQUEST: \n "+req);
			return null;
		}
	}
	



}
