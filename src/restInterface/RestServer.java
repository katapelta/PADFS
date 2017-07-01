package restInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;

import org.springframework.beans.BeansException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import jobManagement.consensus.JobConsMsg;
import jobManagement.jobOperation.JobOperation;
import jobManagement.jobOperation.serverOp.BootNet;
import jobManagement.jobOperation.serverOp.JobServerOp;
import padfsThreads.Padfs;
import restInterface.manageOp.RestGetPermission;
import restInterface.manageOp.RestIsManaged;
import restInterface.manageOp.RestIsPresent;
import restInterface.manageOp.RestPong;
import restInterface.manageOp.RestPongExtraInfo;
import system.SystemEnvironment;
import system.SystemEnvironment.Constants;
import system.SystemEnvironment.Constants.ServerStatus;
import system.SystemEnvironment.Variables;
import system.consensus.ConsensusServerGroup;
import system.containers.HostedFile;
import system.containers.MetaInfo;
import system.containers.Server;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;
import system.managementOp.RequestAddServer;
import system.managementOp.SynchGlobal;
import system.managers.SqlManager;



@SpringBootApplication
public class RestServer implements ApplicationContextAware{

	private static ApplicationContext appContext = null;

	@Override
	public void setApplicationContext(ApplicationContext context) throws BeansException {
		appContext = context;
		
	}

	public static ApplicationContext getAppContext(){
		return appContext;
	}
	
	/**
	 * 
	 * @return true if the network is in maintenance state
	 * @return false if the network is not in maintenance state
	 * @return null if at least one server does not answer
	 * 
	 */
	public static Boolean checkMajorityIsMaintenanceState(){
		int inMaintenance = 0;
		int notInMaintenance = 0;
		for(Server s : Variables.getServerList()){
			RestPongExtraInfo pong = pingGetExtraInfos(s.getIp(), s.getPort());
			if(pong == null ){
				PadFsLogger.log(LogLevel.WARNING, "cannot communicate with server "+s.getId());
			}
			else if(pong.getServerStatus() == ServerStatus.MAINTENANCE_STATE){
				inMaintenance++;
			}
			else{
				notInMaintenance++;
			}
		}
		boolean ret = inMaintenance > notInMaintenance;
		if(ret)
			PadFsLogger.log(LogLevel.DEBUG, "the majority of servers is in maintenance state");
		else
			PadFsLogger.log(LogLevel.DEBUG, "the majority of servers is NOT in maintenance state");
		return ret;
	}

	public static boolean checkServer(String NetworkInterfaceIp, String ip, int timeout){
			
		if(!Padfs.validateString(new String[]{NetworkInterfaceIp,ip})){
    		return false;
    	}
		
		byte[] bytesIP = null;
		InetAddress IPNI = null;
		
		try {
			bytesIP = InetAddress.getByName(ip).getAddress();
			if(NetworkInterfaceIp.equals("*")){ 							 //NO OUTPUT INTERFACE SPECIFIED 
				return InetAddress.getByAddress(bytesIP).isReachable(timeout);
			}else{															  //OUTPUT INTERFACE 
				IPNI = InetAddress.getByName(NetworkInterfaceIp);				
				return InetAddress.getByAddress(bytesIP).isReachable(NetworkInterface.getByInetAddress(IPNI), 0, timeout);
			}
		} catch (IOException e) {
			PadFsLogger.log(LogLevel.WARNING, "[checkServer] - IP: "+ip+" - timeout: "+timeout+" - " + e.getMessage());
		}
		return false;
	}

	
	
	
	/**
	 *
	 * @param ip
	 * @param port
	 * @return null  	  se il server e' irraggiungibile
	 * @return RestPong   se il server e' raggiungibile e la rete e' gia' attiva|non e' attiva
	 */
	public static RestPong ping(String ip, String port){
		String pingRequest = null;
		RestTemplate restTemplate  = SystemEnvironment.generateRestTemplate();
		ResponseEntity<RestPong> reply = null;

		long idGlobalConsRun = Variables.consensusVariableManager.getConsVariables(Constants.globalConsensusGroupId).getIdConsRun();

		String myServerId = "0";
		if(Variables.getServerId() != null){
			myServerId = Long.toUnsignedString(Variables.getServerId());
		}

		pingRequest = RestInterface.Ping.generateUrl(ip,port,idGlobalConsRun,myServerId,Variables.getServerPort());

		PadFsLogger.log(LogLevel.DEBUG,"pingRequest - STARTED: "+ pingRequest);

		try{
			reply = restTemplate.exchange(pingRequest, HttpMethod.GET,null, RestPong.class);
		}catch(RestClientException e){
			//server non raggiungibile
			//e.printStackTrace();
			//PadFsLogger.log(LogLevel.ERROR,e.getMessage());
			//Padfs.harakiri();
			PadFsLogger.log(LogLevel.DEBUG,"pingRequest - server not reachable: "+ pingRequest);
			return null;
		}

		if(reply.getBody() != null){
			PadFsLogger.log(LogLevel.DEBUG,"pingRequest - OK: "+ pingRequest);
			return reply.getBody();
		}else{
			PadFsLogger.log(LogLevel.DEBUG,"pingRequest - FAILED: "+ pingRequest);
			return null;
		}
	}

/**
	 *
	 * @param ip
	 * @param port
	 * @return null  	  se il server e' irraggiungibile
	 * @return RestPong   se il server e' raggiungibile e la rete e' gia' attiva|non e' attiva
	 */
	public static RestPong pingGroup(String ip, String port, Long groupId){
		String pingRequest = null;
		RestTemplate restTemplate  = SystemEnvironment.generateRestTemplate();
		ResponseEntity<RestPong> reply = null;

		long myIdConsRun = SystemEnvironment.Variables.consensusVariableManager.getConsVariables(groupId).getIdConsRun();
		List<Server> serverIdList = SqlManager.getServerListConsGroup(groupId);
		

		String myServerId = "null";
		if(Variables.getServerId() != null){
			myServerId = Long.toUnsignedString(Variables.getServerId());
		}

		pingRequest =RestInterface.PingGroup.generateUrl(ip,port,myIdConsRun,myServerId,Variables.getServerPort(),serverIdList);


		PadFsLogger.log(LogLevel.DEBUG,"pingRequest - STARTED: "+ pingRequest);

		try{
			reply = restTemplate.exchange(pingRequest, HttpMethod.GET,null, RestPong.class);
		}catch(RestClientException e){
			//server non raggiungibile
			//e.printStackTrace();
			//PadFsLogger.log(LogLevel.ERROR,e.getMessage());
			//Padfs.harakiri();
			PadFsLogger.log(LogLevel.DEBUG,"pingRequest - server not reachable: "+ pingRequest);
			return null;
		}

		if(reply.getBody() != null){
			PadFsLogger.log(LogLevel.DEBUG,"pingRequest - OK: "+ pingRequest);
			return reply.getBody();
		}else{
			PadFsLogger.log(LogLevel.DEBUG,"pingRequest - FAILED: "+ pingRequest);
			return null;
		}
	}
	/**
	 * Ping the server and retrieve more infos on it
	 * @param ip
	 * @param port
	 * @return null  	  se il server e' irraggiungibile
	 * @return RestPong   se il server e' raggiungibile e la rete e' gia' attiva|non e' attiva
	 */
	public static RestPongExtraInfo pingGetExtraInfos(String ip, String port){
		String pingRequest = null;
		RestTemplate restTemplate  = SystemEnvironment.generateRestTemplate();
		ResponseEntity<RestPongExtraInfo> reply = null;

		long idGlobalConsRun = Variables.consensusVariableManager.getConsVariables(Constants.globalConsensusGroupId).getIdConsRun();

		String myServerId = "null";
		if(Variables.getServerId() != null){
			myServerId = Long.toUnsignedString(Variables.getServerId());
		}

		pingRequest = RestInterface.PingExtraInfos.generateUrl(ip,port,
																idGlobalConsRun,
																myServerId,
																Variables.getServerPort(),
																Variables.getAvailableSpace(),
																Variables.getTotalSpace(),
																Variables.getServerStatus()
														);


		PadFsLogger.log(LogLevel.DEBUG,"pingRequest - STARTED: "+ pingRequest);

		try{
			reply = restTemplate.exchange(pingRequest, HttpMethod.GET,null, RestPongExtraInfo.class);
		}catch(RestClientException e){
			//server non raggiungibile
			//e.printStackTrace();
			//Padfs.harakiri();
			PadFsLogger.log(LogLevel.DEBUG,"pingRequest - server not reachable: "+ pingRequest);
			return null;
		}

		if(reply.getBody() != null){
			PadFsLogger.log(LogLevel.DEBUG,"pingRequest - OK: "+ pingRequest);
			return reply.getBody();
		}else{
			PadFsLogger.log(LogLevel.DEBUG,"pingRequest - FAILED: "+ pingRequest);
			return null;
		}
	}
	
	public static boolean pingUpdateServerKeepAlive(long id, String ip, String port){
		RestPongExtraInfo pongResponse = pingGetExtraInfos(ip,port);
		if(pongResponse != null){
			Long pongId = pongResponse.getIdServer();
			if(pongId == null){
				PadFsLogger.log(LogLevel.DEBUG,"PING RESPONSE - idServer is null - " + ip + ":" + port);
				SqlManager.setDOWNServer(id);
				return false;
			}
			if(pongId != id){
				PadFsLogger.log(LogLevel.DEBUG,"PING RESPONSE - idServer is different from expected ("+id +"!=" +pongId+") - " + ip + ":" + port);
				SqlManager.setDOWNServer(id);
				return false;
			}


			PadFsLogger.log(LogLevel.DEBUG,"PING (pingUpdateServerKeepAlive) RESPONSE - "
					+ ip + ":" + port+" -> AS: "+pongResponse.getAvailableSpace()+" TS: "+pongResponse.getTotalSpace());
	
			SqlManager.updateServerExtraInfos(id,pongResponse.getAvailableSpace(),pongResponse.getTotalSpace(),pongResponse.getServerStatus());
			Variables.updateServerList(id, "1", SystemEnvironment.getDateTime(), pongResponse.getAvailableSpace(),pongResponse.getTotalSpace(),pongResponse.getServerStatus()); //UPDATE THE VARIABLES SERVER LIST
			
			
			long receivedIdConsRun = pongResponse.getIdConsRun();
			long myIdConsRun = Variables.consensusVariableManager.getConsVariables(Constants.globalConsensusGroupId).getIdConsRun(); 
			if( receivedIdConsRun > myIdConsRun ){
				
				PadFsLogger.log(LogLevel.INFO,"Global Synchronization started with " + ip + ":" + port);
				SynchGlobal.delayedGlobalSynch();
				
			}
			
			return true;
		}else{
			PadFsLogger.log(LogLevel.WARNING, "PING ERROR - " + ip + ":" + port);
			SqlManager.setDOWNServer(id);
			return false;
		}
	
	}
	
	private static boolean checkPortStatus(int port){
	    ServerSocket ss = null;
	    DatagramSocket ds = null;
	    try {
	        ss = new ServerSocket(port);
	        ss.setReuseAddress(true);
	        ds = new DatagramSocket(port);
	        ds.setReuseAddress(true);
	        return true;
	    } catch (IOException e) {
	    } finally {
	        if (ds != null) {
	            ds.close();
	        }

	        if (ss != null) {
	            try {
	                ss.close();
	            } catch (IOException e) {
	                /* should not be thrown */
	            }
	        }
	    }

	    return false;
	}



	
	public static void restServerStart(PriorityBlockingQueue<JobOperation> inOp, BlockingQueue<JobConsMsg<?>> inConsMsg, String serverPort, String serverIp, LogLevel logLevel) throws SocketException  {
		/* TODO trovare come sovrascrivere il messaggio d'errore la pagina  500 */
		StringBuilder sIP = new StringBuilder();
		SpringApplication app  = null;

		boolean found = false;
		Map<String,Object> springConfig = new HashMap<>();
		
		//SETTING ARGS LOGLEVEL PASS TO SPRING 
		if( logLevel != null)
			if( logLevel.getNumVal() < 4 ) //SE LOGLEVEL == DEBUG SCRIVE TROPPA ROBA => LO LIMITO A INFO
				springConfig.put("logging.level.", logLevel.toString());

		//SET PROPERTY FOR THE SERVER WITHOUT BEAN OR XML!!!
		springConfig.put("logging.level.",  "OFF");
		springConfig.put("server.port",  serverPort);		
		springConfig.put("multipart.maxFileSize",  "200000MB"); //200GB //TODO INSERIRE NEL CONFIG IL LIMITE
		springConfig.put("multipart.maxRequestSize",  "200000MB");		//TODO INSERIRE NEL CONFIG IL LIMITE
		springConfig.put("log4j.logger.",  "OFF");
		springConfig.put("http.client.HttpComponentsClientHttpRequestFactory.readTimeout",  "30"); //30 secondi    //TODO INSERIRE NELLE CONSTANTS IL LIMITE
		springConfig.put("http.client.HttpComponentsClientHttpRequestFactory.connectTimeout",  "30"); //30 secondi //TODO INSERIRE NELLE CONSTANTS IL LIMITE


//		springConfig.put("server.context-path",  "/html");
//		springConfig.put("management.context-path",  "/html");
		springConfig.put("spring.view.prefix",  "/html");
//		springConfig.put("spring.view.suffix",  ".html");

		if(Variables.getProtocol().equals("https")) {  //setting the variables if the choosen protocol is HTTPS
			//TOMCAT SERVER
			springConfig.put("server.ssl.enabled",  "true");
			springConfig.put("server.ssl.key-store",  Variables.getFileCertPath());
			springConfig.put("server.ssl.key-store-password",  Variables.getFileCertPassword());
			springConfig.put("server.ssl.trust-store",  Variables.getFileCertPath());
			springConfig.put("server.ssl.trust-store-password",  Variables.getFileCertPassword());
			//JAVA
			System.setProperty("javax.net.ssl.trustStore", Variables.getFileCertPath());
			System.setProperty("javax.net.ssl.trustStorePassword", Variables.getFileCertPassword());


			javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier(
					new javax.net.ssl.HostnameVerifier(){
						@Override
						public boolean verify(String hostname,
											  javax.net.ssl.SSLSession sslSession) {
							if (hostname.equals("localhost")) {
								return true;
							}
							return false;
						}
					});

			/**
			 *
			 static {
				 System.setProperty("javax.net.ssl.trustStore", Variables.getFileCertPath());
				 System.setProperty("javax.net.ssl.trustStorePassword", Variables.getFileCertPassword());

				 // workaround for localhost
				 HttpsURLConnection.setDefaultHostnameVerifier( new HostnameVerifier() {
				@Override
				public boolean verify(String hostname, SSLSession sslSession) {
				if (hostname.equals("localhost")) {
				return true;
				}
				return false;
				}
				});
			 }
			 */
		}

		List<String> myIpList = new LinkedList<>();
		
		/* IF NO IP SPECIFIED OPEN PORT AS *:<serverPort> */
		if( serverIp == null )
			serverIp = "*";

		/* 
		 * scan all my ip looking for the one specified in my configFile.
		 * if the ip specified in the config file is available, myIpList will contain only it.
		 * otherwise, myIpList will contain all my ip address.
		 */
		String ipAddress = null;
		for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
            NetworkInterface intf = en.nextElement();
            for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                InetAddress inetAddress = enumIpAddr.nextElement();
                if (inetAddress instanceof Inet4Address && !inetAddress.equals("*")) {
                	sIP.append(inetAddress.getHostAddress().toString()+"; ");
                	if(inetAddress.getHostAddress().toString().equals(serverIp)){
	                	found=true;
                	}
                	ipAddress = inetAddress.getHostAddress().toString();
                	if(	!Constants.localhostAddresses.contains(ipAddress) ){
                		myIpList.add(inetAddress.getHostAddress().toString());
                	}
	                
                }
            }
		}
	/*	
		for(int i=0;i<ip.length && !found;i++){
			sIP.append(ip[i].getHostAddress().toString()+"; ");
			
			if(ip[i].getHostAddress().toString().equals(serverIp)){
				found=true;
				
				myIpList = new LinkedList<>();
			}
			myIpList.add(ip[i].getHostAddress().toString());
		}
		*/
		if(found == false && serverIp != null && !serverIp.equals("*") && !Constants.localhostAddresses.contains(serverIp) )
			myIpList.add(serverIp);
				 
		
		if( serverIp.equals("*") || !found){
			PadFsLogger.log(LogLevel.WARNING, "CONFIG FILE IP NOT VALID - CONFIG_FILE: "+serverIp+" - AVAILABLE: "+sIP);
			serverIp="*";
		}else{
			springConfig.put("server.address",(Object)serverIp);					
		}
		
		PadFsLogger.log(LogLevel.DEBUG, "setMyInterfaceIpList: "+myIpList.toString());
		Variables.setMyInterfaceIpList(myIpList);
		
		//CHECK IF THE SERVER PORT IS FREE
		if(!checkPortStatus(Integer.valueOf(serverPort))){
			PadFsLogger.log(LogLevel.FATAL, "BIND SERVER PORT OCCUPIED");
		}
		
		//START THE SERVICE
		app = new SpringApplication(RestServer.class);
		app.setShowBanner(false);
		app.setLogStartupInfo(false);

		RestPadfsController.init(inConsMsg, inOp);
		app.setDefaultProperties(springConfig);
		
					
		PadFsLogger.log(LogLevel.INFO, "- SERVER TOMCAT STARTING... <"+serverIp+":"+serverPort+">");
		
		try{
			app.run();
			
		}catch(Throwable e){
			PadFsLogger.log(LogLevel.FATAL, "SERVER TOMCAT ERROR - "+ e.getClass().getName() + ": " + e.getMessage());
			System.exit(-2);
		}
		
		PadFsLogger.log(LogLevel.INFO, "- SERVER TOMCAT RUNNING");
		
	}

	

	/**
	 * Allow to boot the network in case of no network of servers is present
	 * @param serverList	the list of the server with boot the network
	 * 
	 * @return true if the network is started or if I've initialized the BootNet operation
	 * @return false otherwise
	 */
	public static boolean bootNet(List<Server> serverList){
		
		String ip = null;
		String port = null;
		RestPong reply = null;
		boolean netStarted = false;
		Server server = null;
		List<Server> listBootServer = new LinkedList<Server>(serverList);
		
		
		
		/* 
		 * Check if the net is already started or not.
		 * Add the alive servers to the DB
		 * 
		 * */
		for(int i=0; i<listBootServer.size(); i++){
			server 	= listBootServer.get(i);
			ip 		= server.getIp();
			port 	= server.getPort();
			
			reply	= ping(ip,port); // return true if the net is up, false if the net is down, null if the server is unreachable
			
			if(reply != null){
						
				system.logger.PadFsLogger.log(LogLevel.DEBUG, "reply from "+ip+":"+port+"  idConsRun="+reply.getIdConsRun()+" isNetworkStarting="+reply.getIsNetworkStarting());
			
				// if the network is in startingState, do nothing and wait
				/*if(reply.getIsNetworkStarting()){
					PadFsLogger.log(LogLevel.ERROR, "netStarting from "+ip+":"+port);
					return false;
				}*/
				
				//check if it exists a server with my ID.
				if(!(
					(Variables.getMyInterfaceIpList().contains(ip) && Variables.getServerPort().equals(port)) 
					||
					(Constants.localhostAddresses.contains(ip) && Variables.getServerPort().equals(port))	
				 )){
					if( (reply.getIdServer()!= null && Variables.getServerId() != null) ){
					  if(reply.getIdServer().equals(Variables.getServerId())){	
						system.logger.PadFsLogger.log(LogLevel.FATAL, "bootnet failed: server "+ip+":"+port+"  has my same id: "+reply.getIdServer());
						return false;
					  }
					}
				}
				
				// check if the network is already up
				if(reply.getIdConsRun() > 0){
					netStarted=true;
				}
				
				if(reply.getIdServer() != null){
					//update serverId in the local copy of serverList of the bootNet procedure
					//Padfs.addServerToDb(ip, port, reply.getIdServer());
					server.setId(reply.getIdServer());
				}
			}else {
				listBootServer.remove(i);
				i--;
			}
		}
		
		
			
		if(netStarted == true){
			Variables.setNetworkUp(true);
			//RETE ATTIVA, DEVO METTERE ADDSERVER IN CODA 
						
			system.logger.PadFsLogger.log(LogLevel.INFO, "bootNet  network is up, try addServer");
			RequestAddServer job = new RequestAddServer(listBootServer);
			if(job.execute()){
				Variables.setIAmInTheNet(true);
				return true;
			}
			else{
				if(Variables.getIAmInTheNet()){
					system.logger.PadFsLogger.log(LogLevel.INFO, "addServer not needed");
					return true;
				}
			}
			
			
		}else{
			/*if(Variables.isNetworkStarting() )
				return false;
				*/
			
			if(Variables.getServerId() == null){
				system.logger.PadFsLogger.log(LogLevel.FATAL, "BOOTNET FAILED: NULL idServer IS NOT ALLOWED TO BOOT THE NETWORK, change the configuration");
				return false;
			}
			
			//RETE NON ATTIVA
			int serverCount = listBootServer.size();
			//check if there is the min server number to do the boot
			if(serverCount >= Constants.replicaNumber){
				
				//check if the network is already started in the meanwhile
				if(Variables.isNetworkUp()){
					system.logger.PadFsLogger.log(LogLevel.DEBUG, "network is up");
					return true;
				}
				else{
					//SERVER A DISPOSIZIONE PER BOOT AGGIUNGERE BOOT IN CODA
					JobServerOp job = new BootNet(listBootServer);
					Variables.setIsNetworkStarting(true);
					Padfs.inOp.add(job);
					system.logger.PadFsLogger.log(LogLevel.INFO, "bootNet  network is down, trying to start the net");
					return true;
				}
			}
			else{
				//check if the acceptor have already completed a BootNet operation in the meanwhile
				if(Variables.isNetworkUp()){
					system.logger.PadFsLogger.log(LogLevel.INFO, "the network is up");
					return true;
				}
				else
					system.logger.PadFsLogger.log(LogLevel.INFO, "bootNet  network is down, waiting other servers");
			}
		}
		
		return false;
	}
	
	
	public static String download(String url) {
		PadFsLogger.log(LogLevel.DEBUG, "file download "+url);
		String fileName = null;
		try {
			File f = File.createTempFile("PadFs","tmp",new File(Variables.getFileSystemTMPPath()));

			PadFsLogger.log(LogLevel.DEBUG, "file download in "+f.getAbsolutePath());
			PadFsLogger.log(LogLevel.DEBUG, "tmpDir = "+Variables.getFileSystemTMPPath(),"green","red");
			fileName = f.getAbsolutePath();
			f.delete();
		} catch (Exception e) {
			PadFsLogger.log(LogLevel.ERROR, "IO: " +e.getMessage());
			return null;
		}
		
		URL website;
		try {
			website = new URL(url);
		
			ReadableByteChannel rbc = Channels.newChannel(website.openStream());
			FileOutputStream fos = new FileOutputStream(fileName);
			fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
			fos.close();
			return fileName;
		
		} catch (Exception e) {
			PadFsLogger.log(LogLevel.ERROR, e.getMessage());
			
		}
	    return null;
	}


	
	public static RestIsPresent isPresent(Server s, MetaInfo file) {
		String url = RestInterface.IsPresent.generateUrl(s.getIp(), s.getPort(), file.getIdOwner(), file.getChecksum(), file.getPath());
		
		RestTemplate restTemplate = SystemEnvironment.generateRestTemplate();
		ResponseEntity<RestIsPresent> response;
		try{
			response = restTemplate.exchange(url, HttpMethod.GET, null, RestIsPresent.class);
			if(response!= null){
				return response.getBody();
			}
			else{
				PadFsLogger.log(LogLevel.ERROR, "response is null");
				return null;
			}
		}catch(Exception e){
			PadFsLogger.log(LogLevel.WARNING, "can't communicate with server "+s.getId());
			return null;
		}
		
	}

	public static RestIsManaged isManaged(Server s, HostedFile file) {
		
		ResponseEntity<RestIsManaged> resp = null;
	
		String url = RestInterface.IsManaged.generateUrl(s.getIp(), s.getPort(), file.getIdOwner(),file.getChecksum(),file.getLogicalPath()); 
		
		PadFsLogger.log(LogLevel.TRACE, "url:"+url); 
		try{
			RestTemplate rt = SystemEnvironment.generateRestTemplate();	
			resp = rt.exchange(url, HttpMethod.GET, null, RestIsManaged.class);
			if(resp != null)
				return resp.getBody();
			else{
				PadFsLogger.log(LogLevel.ERROR, "response is null");
			}
		}
		catch(Exception e){
			PadFsLogger.log(LogLevel.WARNING, "failed communicate with server "+s.getId());
		}
		
		return null;
	}



	/**
	 * Retrieve the permission and the idConsensusRun of the group managing the required permission from the specified server
	 * 
	 * @param s			the server to communicate with
	 * @param idUser	the user involved in the permission
	 * @param idOwner	the owner of the path
	 * @param path		the path involved in the permission
	 * @return
	 */
	public static RestGetPermission getPermission(Server s, Integer idUser, Integer idOwner, String path) {
		ResponseEntity<RestGetPermission> resp = null;
		
		String url = RestInterface.GetPermission.generateUrl(s.getIp(), s.getPort(), idUser, idOwner, path);
		PadFsLogger.log(LogLevel.TRACE, "url:"+url); 
		try{
			RestTemplate rt = SystemEnvironment.generateRestTemplate();	
			resp = rt.exchange(url, HttpMethod.GET, null, RestGetPermission.class);
			if(resp != null )
				return resp.getBody();
			else{
				PadFsLogger.log(LogLevel.ERROR, "response is null");
			}
		}
		catch(Exception e){
			PadFsLogger.log(LogLevel.ERROR, "failed communicate with server "+s.getId());
		}
		
		return null;
	}



	/**
	 * check if this server is synchronized in the consensusServerGroup.
	 * this method return null if the idConsRun of this server is not less than the one of all the other (reachable) servers in the group.
	 * otherwise it is returned the reachable Server with higher consRunId in the group.
	 * 
	 * @param group the consensusServerGroup to check server synchronization
	 * @return null if this server is considered upToDate
	 * @return a Server that is reachable and upToDate in this group
	 */
	public static Server checkGroupSynchronization(ConsensusServerGroup group) {
		Server ret = null;
		
		Iterator<Server> it = group.iterator();
		Long myIdConsRun = Variables.consensusVariableManager.getConsVariables(group.getConsensusGroupId()).getIdConsRun();
		Long maxConsRunId = myIdConsRun;
		boolean atLeastOnAlive = false;
		while(it.hasNext()){
			Server s = it.next();
			
			/* check that I'm not trying to communicate with myself */
			if(s.getId() != Variables.getServerId()){
				
				RestPong pong = RestServer.pingGroup(s.getIp(), s.getPort(), group.getConsensusGroupId());
				if(pong != null){
					atLeastOnAlive = true;
					if(Long.compareUnsigned(pong.getIdConsRun(),maxConsRunId) > 0 ){
						ret = s;
					}
				}
			}
			
		}
		
		if(atLeastOnAlive == false){
			PadFsLogger.log(LogLevel.WARNING, "no other servers alive in this group:" + group.getConsensusGroupId());
		}
		else{	
			if(ret != null){
				PadFsLogger.log(LogLevel.DEBUG, "this server is out of sync in the consensusGroup " + group.getConsensusGroupId() );
			}
			else{
				PadFsLogger.log(LogLevel.TRACE, "this server is synchronized in the consensusGroup " + group.getConsensusGroupId() );
			}
		}
		
		return ret;
	}

	public static List<Server> fixReceivedServerList(List<Server> serverList, String creatorIp, String creatorPort, String creatorId){
		List<Server> ret = new LinkedList<Server>();
		
		if(serverList == null || creatorIp == null || creatorPort == null || creatorId == null){
			PadFsLogger.log(LogLevel.ERROR, "missing parameters");
			return null;
		}
		
		Iterator<Server> i = serverList.iterator();
		while(i.hasNext()){
			Server s = i.next();
			if(Constants.localhostAddresses.contains(s.getIp())){
				String port = s.getPort();
				
				if(s.getId() != null && creatorId != null && s.getId().equals(creatorId))
					port = creatorPort;
	
				s = new Server(s.getId(), creatorIp, port , null , null , s.getStatus(), null , null, s.getGroupId(), s.getLabel());
				
			}
			
			
			if(Variables.getMyInterfaceIpList().contains(s.getIp())){
				String port = s.getPort();
								
				if(s.getId() != null && Variables.getServerId() != null && s.getId().equals(Variables.getServerId())) // it is possible to have different servers on the same machine
					port = Variables.getServerPort(); // this can be usefull if we will differentiate external and internal serverPort in a NAT scenario
	
				s = new Server(s.getId(), Constants.localhost, port , null , null , s.getStatus(), null , null, s.getGroupId(), s.getLabel());
			}
			
			ret.add(s);
			
		}
		return ret;
	}

	public static List<Server> fixReceivedServerList(List<Server> serverList,List<String> creatorIpList,String creatorPort, String creatorId){	
		String creatorIp;
		
		if(serverList == null || creatorIpList == null || creatorPort == null || creatorId == null){
			PadFsLogger.log(LogLevel.ERROR, "missing parameters");
			return null;
		}
		
		creatorIpList.add("localhost");
		creatorIp = RestServer.findServerIp(creatorIpList,creatorPort);
		if(creatorIp == null){
			//PadFsLogger.log(LogLevel.FATAL, "failed to retrieve the server ip to fix localhost entries");
			PadFsLogger.log(LogLevel.ERROR, "failed to retrieve the server ip to fix localhost entries. discard message.");
			return null;
		}
		
		return fixReceivedServerList(serverList, creatorIp, creatorPort, creatorId);	
	}

	/**
	 * Check if the parameter serverIP correspond to the current server ip and change it with localhost
	 * @param serverIP  the ip to check
	 * @return String with the ip 
	 */
	public static String fixReceivedServerLocalHost(String serverIP){
		if(serverIP == null){
			PadFsLogger.log(LogLevel.ERROR, "missing parameters");
			return null;
		}
		
		if(Constants.localhostAddresses.contains(serverIP)){
			//return null;
			return Constants.localhost;
		}
		
		if(Variables.getMyInterfaceIpList().contains(serverIP))
		{
			return Constants.localhost;
		}
		return serverIP;
	}

	/**
	 * find one ip address reachable at a given port from this server performing a series of ping.
	 * The first reachable ip address found is returned without checking all the list.
	 * 
	 * @param ipList the list of ipAddresses to check
	 * @param port the listening port 
	 * @return one reachable ip address
	 */
	private static String findServerIp(List<String> ipList,String port) {
		Iterator<String> i = ipList.iterator();
		while(i.hasNext()){
			String ip = i.next();
			if(ping(ip, port) != null)
				return ip;
		}
		PadFsLogger.log(LogLevel.WARNING, "no reachable ip found in the ipList: "+ipList.toString());
		return null;
	}
	
	



	
	
}
