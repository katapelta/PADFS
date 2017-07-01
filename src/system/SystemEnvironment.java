package system;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import jobManagement.jobOperation.JobOperation;
import jobManagement.jobOperation.manageOp.ExitOperation;
import jobManagement.jobOperation.manageOp.NullOperation;
import padfsThreads.Padfs;
import padfsThreads.StoppableThread;
import restInterface.RestInterface;
import restInterface.RestServer;
import restInterface.manageOp.RestGetPermission;
import restInterface.manageOp.RestIsDirManaged;
import restInterface.manageOp.RestIsManaged;
import system.SystemEnvironment.Constants.Permission;
import system.SystemEnvironment.Constants.Rest;
import system.SystemEnvironment.Constants.ServerStatus;
import system.consensus.ConsensusServerGroup;
import system.consensus.ConsensusVariablesManager;
import system.containers.Server;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;
import system.managers.ConfigurationManager;
import system.managers.SqlManager;
import system.merkleTree.MerkleTree;

public class SystemEnvironment {
	
	

	/**
	 * check if the file exists
	 * @param path of the file
	 * @param username of the owner of the file
	 * @return true if it exists
	 * @return false if it do not exists
	 * @return null if there is at least one server that do not answer AND no other servers answer that the file exists
	 */
	public static Boolean checkFileExists(String path,String username){
		return checkMetaInfoExists(path, username, false, null);
	}	
	
	/**
	 * check if the dir exists
	 * 
	 * if the uniqueId is not null, it will check for the directory with the uniqueId specified
	 * 
	 * @param path of the directory
	 * @param username of the owner of the directory
	 * @return true if it exists
	 * @return false if it do not exists
	 * @return null if there is at least one server that do not answer AND no other servers answer that the directory exists
	 */
	public static Boolean checkDirExists(String path,String username, String uniqueId){
		return checkMetaInfoExists(path, username, true, uniqueId);
	}
	
	/**
	 * check if the dir exists
	 * 
	 * if uniqueId is not null, it will check if the directory with that uniqueId is still managed. 
	 * otherwise it will check only for a directory with that path and owner 
	 * 
	 * @param path of the directory
	 * @param username of the owner of the directory
	 * @return true if it exists
	 * @return false if it do not exists
	 * @return null if there is at least one server that do not answer AND no other servers answer that the directory exists
	 */
	private static Boolean checkMetaInfoExists(String path,String username, boolean isDir, String uniqueId){
		
		
		
		Long serverLabel = getLabel(username,path);
		long[] serverIds = SqlManager.getIdFromConsensusLabel(serverLabel);
			
		Iterator<Server> it = (new ConsensusServerGroup(serverIds)).iterator();
		boolean atLeastOneDown = false;
		int numServers = 0;
		while(it != null && it.hasNext() ){
			Server s = it.next();
			if(s == null) continue;
			if(Variables.getExitingState()){
				PadFsLogger.log(LogLevel.DEBUG, "stop because of exit is requested");
			}
			numServers++;
			String request;
			if(isDir)
				request = RestInterface.ExistsDir.generateUrl(s.getIp(),s.getPort(), SqlManager.getIdUser(username), uniqueId, path);
			else
				request = RestInterface.ExistsFile.generateUrl(s.getIp(),s.getPort(), SqlManager.getIdUser(username), uniqueId, path);

			PadFsLogger.log(LogLevel.TRACE,"checkMetaInfoExists - "+ request);
			
			
			try{
				ResponseEntity<RestIsManaged> reply;
				RestTemplate restTemplate  = SystemEnvironment.generateRestTemplate();
				reply = restTemplate.exchange(request, HttpMethod.GET,null, RestIsManaged.class);
				
				if(reply != null && reply.getBody() != null && reply.getBody().getStatus() == Rest.status.ack){
					PadFsLogger.log(LogLevel.DEBUG,"metaInfo exists - "+ request);
					return true;
				}
				else{
					if(reply == null) 
						PadFsLogger.log(LogLevel.DEBUG,"reply is null -"+ request);
					if(reply.getBody() == null) 
						PadFsLogger.log(LogLevel.DEBUG,"reply body is null -"+ request);
					if(reply.getBody().getStatus() == Rest.status.error) 
						PadFsLogger.log(LogLevel.DEBUG,"reply contains error -"+ request + " "+ reply.getBody().getError() );
					if(reply.getBody().getStatus() == Rest.status.nack) 
						PadFsLogger.log(LogLevel.DEBUG,"check metaInfo, metaInfo do not exists for "+ request );
					
				}

			}catch(RestClientException e){
				
				PadFsLogger.log(LogLevel.DEBUG,"pingRequest - server not reachable: "+ request);
				atLeastOneDown = true;
			}
				
		}
		
		if(numServers <= 0 || atLeastOneDown)
			return null;
	
		PadFsLogger.log(LogLevel.DEBUG, "metaInfo does not exists");
		return false;
	
		
	}
	

	/**
	 * check if the dir exists and return its uniqueId
	 * @param path of the directory
	 * @param username of the owner of the directory
	 * @return the uniqueId if the directory exists
	 * @return the empty string if the directory do not exists
	 * @return null if there is at least one server that do not answer AND no other servers answer that the directory exists
	 */
	public static String getDirUniqueId(String path,String username){
		
				
		Long serverLabel = getLabel(username,path);
		long[] serverIds = SqlManager.getIdFromConsensusLabel(serverLabel);
			
		Iterator<Server> it = (new ConsensusServerGroup(serverIds)).iterator();
		boolean atLeastOneDown = false;
		int numServers = 0;
		while(it != null && it.hasNext()){
			Server s = it.next();
			if(s == null) continue;
			numServers++;
			String request = RestInterface.GetDirUniqueId.generateUrl(s.getIp(),s.getPort(),SqlManager.getIdUser(username),	path);
		
			PadFsLogger.log(LogLevel.DEBUG,"checkMetaInfoExists - "+ request);
			
			
			try{
				ResponseEntity<RestIsDirManaged> reply;
				RestTemplate restTemplate  = SystemEnvironment.generateRestTemplate();
				reply = restTemplate.exchange(request, HttpMethod.GET,null, RestIsDirManaged.class);
				
				if(reply != null && reply.getBody() != null && reply.getBody().getStatus() == Rest.status.ack){
					PadFsLogger.log(LogLevel.DEBUG,"dir exists - "+ request);
					return reply.getBody().getUniqueId();
				}
				else{
					if(reply == null) 
						PadFsLogger.log(LogLevel.DEBUG,"reply is null -"+ request);
					if(reply.getBody() == null) 
						PadFsLogger.log(LogLevel.DEBUG,"reply body is null -"+ request);
					if(reply.getBody().getStatus() == Rest.status.error) 
						PadFsLogger.log(LogLevel.DEBUG,"reply contains error -"+ request + " "+ reply.getBody().getError() );
					if(reply.getBody().getStatus() == Rest.status.nack) 
						PadFsLogger.log(LogLevel.DEBUG,"check metaInfo, metaInfo do not exists for "+ request );
					
				}

			}catch(RestClientException e){
				
				PadFsLogger.log(LogLevel.DEBUG,"pingRequest - server not reachable: "+ request);
				atLeastOneDown = true;
			}
				
		}
		
		if(numServers <= 0 || atLeastOneDown)
			return null;
	
		return "";
	
		
	}
	
	public static String normalizePath(String path) {
		if(path != null){			
			path = SystemEnvironment.removeEndingSlashes(path);
			path = SystemEnvironment.removeStartingSlashes(path);
			if(path != null){
				return "/"+path;
			}
		}
		return null;
	}


	/**
     * Return a formatted String with the date time
     * @return String with the current "yyyy-MM-dd HH:mm:ss"
     */
    public static String getDateTime() {
            SimpleDateFormat dateFormat = new SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss", Locale.getDefault()); //TODO  TRY  to substitute with Constants.DB_dateFormat   and TEST IT
            Date date = new Date();
            return dateFormat.format(date);
    }

	
	public static String getParentPath(String path){
		if(path == null || path.equals("") || path.equals("/"))
			return null;
		
		String ret = path.replaceAll("/[^/]*$", "");
		if(ret == null || ret.equals(path) || ret.equals(""))
			return "/";
		
		return ret;
	}



	public static class Constants {

		/**
		 *  permissions can be associated to each pair < user , file > and each pair < user , directory >
		 * 
		 *  readOnly,		 can only read the file and list the directory 
		 *	readWrite,		 can read and write on the file, can list and store in the directory 
		 *	fullAccess, 	 can also change permission. The owner of the file/directory can only have fullAccess 
		 *	none 			 can do nothing 
		 * 
		 */
		public static enum Permission{
			readOnly (1),		/* can only read the file and list the directory */ 
			readWrite (2),		/* can read and write on the file, can list and store in the directory */
			fullAccess (3), 	/* can also change permission. The owner of the file/directory can only have fullAccess */
			none (0),			/* can do nothing */
			unset(-1);
			
			private int numVal;

			Permission(Integer numVal) {
				if(numVal == null)
					numVal = 0;
	            this.numVal = numVal;
	        }

	        public int getNumVal() {
	            return numVal;
	        }
	        
	        public static Permission convert(int x){
	        	switch(x){
	        			
	        		case 0: 
	        			return none;
	        	
	        		case 1: 
	        			return readOnly;
	        			
	        		case 2: 
	        			return readWrite;
	        			
	        		case 3: 
	        			return fullAccess;
	        		
	        		default:
	        			return unset;
        			
	        	}
	        }
	        
		}

		public static class Rest {
			public static enum status {
				 ok , error , ack, nack
			}

			public static class message {
				public static final String ok		= "Done!";
			}

			public static enum errors {
				wrongRequest 			( "wrong request"),
				missingName 			( "missing \"name\""),
				networkDown				( "network is down"),
				addServerFailed 		( "addServer to the existing network failed"),
				globalSynchFailed 		( "synchronization of the global status failed"),
				groupSynchFailed 		( "synchronization of the GROUP status failed"),

				serverAlreadyInTheNet	( "server is already in the net"),
				
				wrongPanelPassword		( "wrong panel password"),
				wrongServerPassword		( "wrong server password"),
				wrongUserPassword 		( "wrong user password"),
				fileNotFound			( "file not found"),
				networkError 			( "network error"),
				permissionDenied		( "permission denied"),

				addUserFailed 			( "addUser FAILED"),
				delUserFailed 			( "delUser FAILED"),


				parameterError			( "Input parameter WRONG check it!"),
				error        			( "ERROR check it!"),
				fileAlreadyHosted 		( "file already hosted"),
				isDirectory				( "this is a directory"),
				directoryNotFound   	( "directory not found"),
				addUserOKButNoDirCreated( "the user is created successfully BUT his root directory is not created."),
				userAlreadyPresent 		( "user already present"), 
				directoryAlreadyPresent ( "directory already present"),
				parentDirectoryDoesNotExists ("parent directory does not exists"), 
				failedUploadOnOtherServer ("failed to upload the file on the minimum number of servers required"), 
				deleteFailed			( "delete failed"), 
				prepareOpFail			( "operation initialization is failed"), 
				completeOpFail			( "an error occured during the operation execution"), 
				internalError			( "an unexpected error occured. retry in a while"), 
				consensusNotReached		( "consensus not reached: retry after some time. padfs can be overloaded or some problem can be occured."),
				userDoNotExists			( "this username does not exists"),
				failedDownloadingFile	( "the file cannot be downloaded. retry after some time."), 
				labelNotManaged			( "this label is not managed by this server"), 
				maintenanceMode			( "the server is in Maintenace Mode. We're working for you, please retry after some minutes."),
				serverNotReady			( "this server is temporary unavailable, try with another one or retry after a little time."), 
				notAllServersInMaintenanceMode ("not all servers are in maintenance mode. Please check manually their status."), 
				cannotCollectAnswers	( "cannot collect all the answer needed. some server is unreachable."), 
				maintenanceModeNeeded	( "you must enter Mainentance mode if you want do this operation");
				
				

			    private final String name;       

			    private errors(String s) {
			        name = s;
			    }

			    public boolean equalsName(String otherName) {
			        return (otherName == null) ? false : name.equals(otherName);
			    }

			    public String toString() {
			       return this.name;
			    }
			}
			
			

			public static final class Put {
				public static final String fieldNameFileUpload = "file";
			}
		}

		public static enum OperationPriority{
			EXIT_OPERATION(30),
			EXIT_MAINTENANCE(25),
			MAINTENANCE_REQUESTED(25),
			BOOTNET(20),
			ADDSERVER(15),
			SYNCHRONIZATION_COMPLETED(15),
	    	REMOVESERVER(15),
			ADDUSER(12),
			DELUSER(12),
			UPDATE_META_INFO(11),
	    	PUT(10), 
	    	REMOVE(10), 
	    	GET(10),
	    	GET_FILE_INFO(10),
	    	GET_USER_FILE(10),
	    	CHMOD(10), 
	    	LIST(10), 
	    	MKDIR(10), 
	    	DELDIR(10),
	    	SHARE(10),
	    	NULL_OPERATION(2),
			MAINTENANCE_FINALIZE(1)   /* this must be the lower one */
			; 
	    	
	    	private int numVal;

	    	OperationPriority(int numVal) {
	            this.numVal = numVal;
	        }

	        public int getNumVal() {
	            return numVal;
	        }
	    }


		public static enum ServerStatus{
			/* lower integer value is, more bad situation for the server */
			UNKNOWN(-1),
			STARTING(0),
	    	OUT_OF_SYNC(1),
	    	GLOBAL_SYNCHING(2),
	    	GROUP_SYNCHING(3),
	    	READY(4), 
	    	MAINTENANCE_REQUESTED(5), 
	    	MAINTENANCE_STATE(6);
	    	
	    	private int numVal;

	    	ServerStatus(int numVal) {
	            this.numVal = numVal;
	        }

	        public int getNumVal() {
	            return numVal;
	        }
	        
	        public String getStringVal(){
	        	int x = numVal;
	        	return String.valueOf(x);
	        }

			public String toString(){
				switch (this.numVal){
					case -1:
						return "UNKNOWN";
					case 0:
						return "STARTING";
					case 1:
						return "OUT_OF_SYNC";
					case 2:
						return "GLOBAL_SYNCHING";
					case 3:
						return "GROUP_SYNCHING";
					case 4:
						return "READY";
					case 5:
						return "MAINTENANCE_REQUESTED";
					case 6:
						return "MAINTENANCE_STATE";
					default:
						return "UNKNOWN";
				}
			}
	        
	        public static ServerStatus convert(int x){
	        	switch(x){
	        		case -1: 
	        			return UNKNOWN;
	        			
	        		case 0: 
	        			return STARTING;
	        			
	        		case 1: 
	        			return OUT_OF_SYNC;
	        			
	        		case 2: 
	        			return GLOBAL_SYNCHING;
	        			
	        		case 3: 
	        			return GROUP_SYNCHING;
	        		
	        		case 4:
	        			return READY;
	        			
	        		case 5:
						return MAINTENANCE_REQUESTED;
						
					case 6:
						return MAINTENANCE_STATE;
        			
	        	}
	        	return UNKNOWN;
	        }
	    }

		public static final LogLevel sqlMainFunctionsLogLevel = LogLevel.TRACE;

		public static final int replicaNumber 		= 3;
		public static final int replicaSearchFactor = 10;
		public static final long numLabels 			= Long.parseUnsignedLong("9223372036854775808");  // 2^63 
		public static final long maxLabel 			= numLabels-1;  // 2^63 -1
		
		public static final long globalConsensusGroupId = 1;
		public static final String defaultAdminUsername = "admin";
		public static final String defaultAdminPassword = "admin";
		
		public static final String charset 				= "UTF-8";
		public static final String fileSeparator 		= "/";
		public static final HashFunction hashFunction	= new HashFunction();

		//TODO mettere i controlli sulla lunghezza massima dell'input utente 
		public static final int maxUsernameLength		= 255;
		public static final int maxPasswordLength		= 255;
		public static final int maxPathLength			= 255;
		
		public static final long waitProposerTimeout = 35;			//seconds
		public static final long waitBeforeRetryConsensus = 1000;   //milli-seconds
		public static final int timeoutHttpConnection = 30;			//seconds
		
		public static final List<String> localhostAddresses = new ArrayList<String>() {
			private static final long serialVersionUID = 1L;
			{add("localhost");add("127.0.0.1");add("127.0.1.1");}
		};
		public static final String localhost = "localhost";
		public static final long sleepTime_CheckReplicasAliveDefault = 1*60*1000;//milliseconds
		public static final String waitMillisecondsBeforeRetry = "1000";
		public static final Integer waitMillisecondsHeartbeat = 30000;
		public static final Long maxTimeMantainUploadingFlag = 15*60*1000L; // 15 minutes

		public static final int maxRetryNumber = 10;

		public static final String defaultServerPassword = "yep";
		public static final String defaultPanelPassowrd = "pippo";

		public static final String defaultProtocol	= "http";

		public static final String wrongOperationDataErrorIpList = "cannot find a reachable ip in the creatorIpList";


		public static final int numberOfOperationForGroupSynchToMaintain = 3;

		public static final String UTF8 = "UTF-8";

		public static final String waitBeforeSynch = "3000";


		public static final String treeViewRootLabel = "PAD_FS";

		public static final String rootDirectory = "/";

		public static final String uniqueIdAdminRootDirectory = "0.0.0"; //this Id must be never used in any other folder except of the admin root directory

		public static final long waitBeforeCheckMaintenanceState = 500;

		public static final String maxConnections = "50";

		public static final int queueCapacity = 10;


		
		public static class RequiredPermissions{
			public static final Permission Get 		= Permission.readOnly;
			public static final Permission List		= Permission.readOnly;
			public static final Permission Put 		= Permission.readWrite;
			public static final Permission Mkdir	= Permission.readWrite;
			public static final Permission Remove 	= Permission.fullAccess;
			public static final Permission Deldir 	= Permission.fullAccess;
			public static final Permission Chmod 	= Permission.fullAccess;
			
		}
		

		public static String checksum(File file) {
			byte b[] = null;
			String result = "";
			try {
				
				InputStream is = new FileInputStream(file);
				
				byte[] buffer = new byte[1024];
			    MessageDigest msgDigest = MessageDigest.getInstance("MD5");
			    int numRead;

			    do {
			    	numRead = is.read(buffer);
			        if (numRead > 0) {
			        	msgDigest.update(buffer, 0, numRead);
			        }
			    } while (numRead != -1);

			    is.close();
			    b = msgDigest.digest();
			       
			} catch (NoSuchAlgorithmException | IOException e) {
				PadFsLogger.log(LogLevel.ERROR,"failed computing checksum of the file");
				return null;
			}
			
			if(b != null){
				for (int i=0; i < b.length; i++) {
			           result += Integer.toString( ( b[i] & 0xff ) + 0x100, 16).substring( 1 );
				}
			}
			return result;
			
		}

	
	}

	public static class Variables {
		
		public static ConsensusVariablesManager consensusVariableManager = new ConsensusVariablesManager(); 

		private static Integer numberOfServer = 0;
		private static List<String> myInterfaceIpList = null;
		
		/* generated by Config */
		/** SERVER VAR **/
		private static String 	serverPort 	= null;
		private static String 	configServerIP = null;
		private static Long   	serverId 	= null;
		private static Long 	labelStart 	= null;
		private static Long 	labelEnd	= null;
		private static Integer 	groupId		= null;
		private static String 	totalSpace  = null;
		private static String   availableSpace = null;

		private static String 	panelPassword = null;
		private static String 	serverPassword= null;
		
		private static boolean 	networkUp 	= false; 
		
		private static MerkleTree merkleTree = null;
		
		private static String logPath = null;
		private static Boolean owLog = null;
		private static LogLevel logLevel;

		private static String fileSystemPath;
		private static String fileSystemTMPPath;

		private static List<Server> serverList;
			
		private static Integer waitMillisecondsBeforeRetry = null; 
		private static Integer waitMillisecondsHeartbeat   = null; 
		private static Long    maxTimeMantainUploadingFlag   = null;

		private static Integer retryNumber = null;
		private static Integer globalSynchRetryNumber = 1; //TODO  create setter and put it in the config

		private static Boolean synchronizationStatus = false;
		private static Lock synchronizationLock = new ReentrantLock();

		private static Boolean colouredOutput = null;

		private static Long sleepTime_CheckReplicasAlive = null;

		private static boolean  isNetworkStarting = false;

		private static boolean iAmInTheNet = false;

		private static ServerStatus serverStatus = ServerStatus.STARTING;

		private static String protocol = null;
		private static String fileCertPath = null;
		private static String fileCertPassword = null;

		private static Queue<JobOperation> completeOpQueue;

		private static Queue<JobOperation> prepareOpQueue;

		private static boolean needToGlobalSync;

		public static List<StoppableThread> outOfFLowThreads;

		private static boolean exitingState;

		private static String fileSeparator = null ;



		public static List<StoppableThread> getOutOfFLowThreads(){
			return outOfFLowThreads;
		}


		public static void addOutOfFLowThread(StoppableThread t){
			if(outOfFLowThreads == null)
				outOfFLowThreads = new LinkedList<StoppableThread>();
			
			outOfFLowThreads.add(t);
		}


		private static Map<Long,List<JobOperation>> lastGroupOperation;

		private static Integer waitBeforeSynch = null;

		private static ServerStatus serverStatusBeforeMaintenance = null;

		/**
		 * Retrieve the list of last X operation of the group
		 * @param groupId Long id of the group
         * @return  List<JobOperation> list of operation
         */
		public static List<JobOperation> getJobListGroup(Long groupId){
			if(groupId == null) return null;
			if(lastGroupOperation == null || lastGroupOperation.size()<=0) return null;

			return lastGroupOperation.get(groupId);
		}

		/**
		 * Add a joboperation to the grouplist and check to maintain only X operation as set in the constants
		 * @param groupId
		 * @param j			Joboperation
         * @return  		true if ok false otherwise
         */
		public static boolean addJobOperationToGroupList(Long groupId, JobOperation j){
			if(groupId == null || j == null) return false;
			if(lastGroupOperation == null)
				lastGroupOperation = new HashMap<Long,List<JobOperation>>();
				
			if(lastGroupOperation.get(groupId) == null) {
				lastGroupOperation.put(groupId, new LinkedList<>());
			}
			lastGroupOperation.get(groupId).add(j);
			if (lastGroupOperation.get(groupId).size() > Constants.numberOfOperationForGroupSynchToMaintain) {
				if(lastGroupOperation.get(groupId) != null)
						lastGroupOperation.get(groupId).remove(0);
			}
			return true;
		}



		/*public static class GlobalConsensusVariables implements ConsVariable{
			private ProposalNumber hpn,hapn;
			private JobOperation hapv;
			private Integer idConsRun;

			public  GlobalConsensusVariables(){
				HashMap<String,String> h;
				h = SqlManager.getConsVar(Constants.globalConsensusGroupId);
				idConsRun = Integer.parseInt(h.get("idConsensusRun"));
				hapv = JobOperation.createFromJson(h.get("hapv"), h.get("hapvType"));
				hpn = ProposalNumber.createFromJson(h.get("hpn"));
				hapn = ProposalNumber.createFromJson(h.get("hapn"));

			}
			
			public synchronized int getIdConsRun() {
				return idConsRun;
			}

			public synchronized ProposalNumber getHpn() {
				return hpn;
			}

			public synchronized JobOperation getHapv() {
				return hapv;
			}

			public synchronized ProposalNumber getHapn() {
				return hapn;
			}
			


			public synchronized void setHPN(ProposalNumber hpn) {
				GlobalConsensusVariables.hpn = hpn;		
				setValues();
			}
			
			public synchronized void setHapn(ProposalNumber hapn) {
				GlobalConsensusVariables.hapn = hapn;			
			}
			
			public synchronized void setHapv(JobOperation hapv) {
				GlobalConsensusVariables.hapv = hapv;			
				setValues();
			}
			
			public synchronized void incIdConsRun() {
				GlobalConsensusVariables.idConsRun++;
				setValues();
			}
			
			public synchronized void setIdConsRun(Integer idConsRun) {
				GlobalConsensusVariables.idConsRun = idConsRun;
				setValues();
			}
			
			
			
			
			
			private static boolean setValues(){
				HashMap<String,String> hm = new HashMap<>(4);
				
				if(hpn!=null)
					hm.put("hpn", hpn.toJSON());
				else
					hm.put("hpn", "{\"number\":\"0\",\"nodeId\":0}");
				
				if(hapn!=null)
					hm.put("hapn", hapn.toJSON());
				else
					hm.put("hapn", "{\"number\":\"0\",\"nodeId\":0}");
				
				if(hapv!=null)
					hm.put("hapv", hapv.toJSON());
				else
					hm.put("hapv", null);
				
				if(hapv!=null)
					hm.put("hapvType", hapv.getClass().getName().toString());
				else
					hm.put("hapvType", null);
				
				if(idConsRun!=null)
					hm.put("idConsensusRun", idConsRun.toString());
				else
					hm.put("idConsensusRun", "0");

				return SqlManager.setConsVars(Constants.globalConsensusGroupId,hm);
				
			}

	

		
		 	
			
			
		}*/

		/**
		 * UPDATE SINGLE SERVER IN THE SERVER LIST
		 * @param serverId
		 * @param keepAlive
		 * @param keepAliveTime
		 * @param availableSpace
         * @param totalSpace
         * @return
         */
		public static synchronized boolean updateServerList(Long serverId, String keepAlive,
															String keepAliveTime, String availableSpace,
															String totalSpace,
															ServerStatus serverStatus){
			if(!Padfs.validateString(new String[]{
					keepAlive,
					keepAliveTime,
					availableSpace,
					totalSpace
			}) || serverId==null){
				return false;
			}

			if(serverList==null){
				return false;
			}

			Iterator<Server> i = Variables.serverList.iterator();

			while (i.hasNext()){
				Server server = i.next();
				if(Long.compareUnsigned(server.getId(),serverId)==0){
					server.setTotalSpace(totalSpace);
					server.setAvailableSpace(availableSpace);
					server.setKeepAlive(keepAlive);
					server.setKeepAliveTime(keepAliveTime);
					
					/*
					 * update the serverStatus only if the server is going in a more bad state.
					 * if the state is going to be more good, it will wait a serverOp to be completed
					 * 
					 * perché?
					 */
					//if(server.getStatus().getNumVal() > serverStatus.getNumVal()){
						server.setStatus(serverStatus);
					//}
					return true;
				}
			}
			return false;
		}

				
////////////////	GETTER  //////////////////////////


		public static int getNumberOfServer() {
			return numberOfServer;
		}

		public static synchronized List<String> getMyInterfaceIpList(){
			return myInterfaceIpList;
		}
		
		public static synchronized String getMyInterfaceIpListToString(){
			StringBuilder s = new StringBuilder();
			Iterator<String> it = myInterfaceIpList.iterator();
			while(it.hasNext()){
				s.append(it.next());
				if(it.hasNext())
					s.append(";");
			}
			
			return s.toString();
		}



		public static String getServerPort() {
			return serverPort;
		}

		public static Long getServerId() {
			return serverId;
		}

		public static String getConfigServerIP() {
			return configServerIP;
		}

		public static String getLogPath() {
			return logPath;
		}

		public static boolean isOwLog() {
			return owLog;
		}

		public static LogLevel getLogLevel() {
			return logLevel;
		}

		public static String getFileSystemPath() {
			return fileSystemPath;
		}

		public static String getFileSystemTMPPath() {
			return fileSystemTMPPath;
		}

		public static List<Server> getServerList() {
			return serverList;
		}

		public static String getOSFileSeparator() {
			if(fileSeparator == null) {
				URL f = Thread.currentThread().getContextClassLoader().getResource("padfs"+File.separator+"Padfs.class");

				if (f == null) { //file not found
					if (File.separator.equals("\\")) { //test if the opposite of default works
						fileSeparator =  "/";
					} else {
						fileSeparator =  "\\";
					}

					f = Thread.currentThread().getContextClassLoader().getResource("padfs"+fileSeparator+"Padfs.class");
					if (f == null) {
						fileSeparator = File.separator;
					}
				}else { //file found => separator is correct
					fileSeparator = File.separator;
				}
			}
			return fileSeparator;
		}

		public static String getServerPassword() { return serverPassword; 	}

		public static String getPanelPassword(){ return panelPassword; 	}

		public static String getProtocol(){ 
			if(protocol != null)
				return protocol; 
			else
				return Constants.defaultProtocol;
		}

		public static String getFileCertPath(){ return fileCertPath; }

		public static String getFileCertPassword(){ return fileCertPassword; }

		public static int getRetryNumber() {
			return retryNumber;
		}
		
		public static int getGlobalSynchRetryNumber() {
			return globalSynchRetryNumber;
		}
		
		public static int getWaitMillisecondsBeforeRetry() {
			return waitMillisecondsBeforeRetry;
		}

		public static int getWaitMillisecondsHeartbeat() {
			return waitMillisecondsHeartbeat;
		}
		
		public static synchronized boolean isNetworkUp(){
			return networkUp;
		}
	

		public static MerkleTree getMerkleTree() {
			return merkleTree;
		}

		public static Long getLabelStart() {
			return labelStart;
		}

		public static Long getLabelEnd() {
			return labelEnd;
		}

		public static Integer getGroupId() {
			return groupId;
		}

		public static String getTotalSpace() { return totalSpace; }

		public static String getAvailableSpace() { return availableSpace; }

		public static Boolean getColouredOutput() {
			if(Variables.colouredOutput != null && Variables.colouredOutput )
				return true;
			return false;
		}
		
		public static long getSleepTime_CheckReplicasAlive() {
			if(sleepTime_CheckReplicasAlive == null)
				return Constants.sleepTime_CheckReplicasAliveDefault ;
			else
				return sleepTime_CheckReplicasAlive;
		}
		
		
		public static boolean isNetworkStarting() {
			
			return isNetworkStarting ;
		}

		public static boolean getIAmInTheNet() {
	
			return iAmInTheNet ;
		}
		
//////////////// SETTER  //////////////////////////
		
	
		
		public static boolean setServerStatus(ServerStatus status) {
			boolean result = false;
			if(serverStatus.getNumVal() <= status.getNumVal()){
				result = SqlManager.updateServerStatus(getServerId(), status);
				if(result){
					result = SystemEnvironment.updateServerStatus(Variables.getServerId(), ServerStatus.MAINTENANCE_REQUESTED);
					if(result){
						serverStatus = status;
					}
				}
			}
			if(!result)
				PadFsLogger.log(LogLevel.WARNING, "cannot set serverStatus to "+status);
			return result;
		}
		
		public static boolean downgradeServerStatus(ServerStatus status) {
			return downgradeServerStatus(status,false);
		}
		
		public static boolean downgradeServerStatusFromMaintenance(ServerStatus status) {
			return downgradeServerStatus(status,true);
		}
		
		private static boolean downgradeServerStatus(ServerStatus status,boolean forced) {
			boolean result = false;
			//if(forced || serverStatus.getNumVal() < ServerStatus.MAINTENANCE_REQUESTED.getNumVal()){
				result = SqlManager.updateServerStatus(getServerId(), status);
				if(result)
					serverStatus = status;
				
			//}
			return result;			
		}
		
		public static void setIAmInTheNet(boolean b) {
			iAmInTheNet  = b;
			
		}

		public static void setIsNetworkStarting(boolean b){
			isNetworkStarting = b;
		}
		
		public static boolean setSleepTime_CheckReplicasAlive(long sleepTime){
			if(Variables.sleepTime_CheckReplicasAlive  == null ){
				Variables.sleepTime_CheckReplicasAlive = sleepTime;
				return true;
			}
			return false;
		}

		public static boolean setColouredOutput(Boolean colouredOutput){
			if(Variables.colouredOutput == null && colouredOutput != null){
				Variables.colouredOutput = colouredOutput;
				return true;
			}
			return false;
		}
		
		private static boolean setLabelStart(Long labelStart) {
			if(labelStart != null){
				Variables.labelStart = labelStart;
				return true;
			}
			return false;
		}
		
		private static boolean setLabelEnd(Long labelEnd) {
			if(labelEnd != null){
				Variables.labelEnd = labelEnd;
				return true;
			}
			return false;
		}
		
		private static boolean setGroupId(Integer groupId) {
			if(groupId != null){
				Variables.groupId = groupId;
				return true;
			}			
			return false;
		}

		public static boolean setTotalSpace(String totalSpace) {
			if(totalSpace != null){
				Variables.totalSpace = totalSpace;
				return true;
			}
			return false;
		}

		public static boolean setAvailableSpace(String availableSpace) {
			if(availableSpace != null){
				Variables.availableSpace = availableSpace;
				return true;
			}
			return false;
		}


		public static boolean setMerkleTree(MerkleTree merkleTree) {
			if(merkleTree != null){
				Variables.merkleTree = merkleTree;
				return true;
			}
			return false;
		}
		
		public static synchronized void setNetworkUp(boolean netUp){
			networkUp=netUp;
		}
		
		public static synchronized void setMyInterfaceIpList(List<String> myInterfaceIpList){
			Variables.myInterfaceIpList = myInterfaceIpList;
			return;
		}
		
		public static boolean setNumberOfServer(int numberOfServer) {
			Variables.numberOfServer = numberOfServer;
			return true;
		}


		public static boolean setConfigServerIP(String serverIP) {
			if (Variables.configServerIP == null) {
				Variables.configServerIP = serverIP;
				return true;
			}
			return false;
		}

		public static boolean setServerPort(String serverPort) {
			if (Variables.serverPort == null) {
				Variables.serverPort = serverPort;
				return true;
			}
			return false;
		}


		public static boolean setServerId(Long serverId) {
			Variables.serverId = serverId;
			return true;
		}

		public static boolean setLogPath(String logPath) {
			if (Variables.logPath == null) {
				Variables.logPath = logPath;
				return true;
			}
			return false;

		}

		public static boolean setOwLog(boolean owLog) {
			if (Variables.owLog == null) {
				Variables.owLog = owLog;
				return true;
			}
			return false;
		}

		public static boolean setLogLevel(LogLevel logLevel) {
			if (Variables.logLevel == null) {
				Variables.logLevel = logLevel;
				return true;
			}
			return false;
		}

		public static boolean setFileSystemPath(String fileSystemPath) {
			if (Variables.fileSystemPath == null) {
				Variables.fileSystemPath = fileSystemPath;
				return true;
			}
			return false;
		}

		public static boolean setFileSystemTMPPath(String fileSystemTMPPath) {
			if (Variables.fileSystemTMPPath == null) {
				Variables.fileSystemTMPPath = fileSystemTMPPath;
				return true;
			}
			return false;
		}

		public static boolean setServerList(List<Server> serverList) {
			Variables.serverList = serverList;
			if(serverList != null)
				Variables.numberOfServer = serverList.size();
			else
				Variables.numberOfServer = 0;
			return true;
		}
		
		public static boolean setRetryNumber(int retryNumber) {
			Variables.retryNumber = retryNumber;
			return true;
		}
		
		public static boolean setWaitMillisecondsBeforeRetry(int waitMillisecondsBeforeRetry) {
			if(Variables.waitMillisecondsBeforeRetry == null){
				Variables.waitMillisecondsBeforeRetry = waitMillisecondsBeforeRetry;
				return true;
			}
			return false;
		}
		public static boolean setWaitBeforeSynch(int waitBeforeSynch) {
			if(Variables.waitBeforeSynch == null){
				Variables.waitBeforeSynch = waitBeforeSynch;
				return true;
			}
			return false;
		}
		

		public static boolean setWaitMillisecondsHeartbeat(int waitMillisecondsHeartbeat) {
			if(Variables.waitMillisecondsHeartbeat == null){
				Variables.waitMillisecondsHeartbeat = waitMillisecondsHeartbeat;
				return true;
			}
			return false;
		}

		public static boolean setMaxTimeMantainUploadingFlag(long maxTimeMantainFlaggedFile) {
			if(Variables.maxTimeMantainUploadingFlag == null){
				Variables.maxTimeMantainUploadingFlag = maxTimeMantainFlaggedFile;
				return true;
			}
			return false;
		}
		

		public static boolean setPanelPassword(String password) {
			if(panelPassword == null){
				panelPassword = password;
				return true;
			}
			return false;
		}

		public static boolean setServerPassword(String password) {
			if(serverPassword == null){
				serverPassword = password;
				return true;
			}
			return false;
		}

		public static boolean setProtocol(String proto){
			if(protocol==null){
				switch (proto){
					case "http":
						protocol="http";
						break;
					case "https":
						protocol="https";
						break;
					default:
						protocol="http";
				}
				return true;
			}
			return false;
		}


		public static boolean setFileCertPath(String filePath){
			if(fileCertPath == null){
				fileCertPath = filePath;
				return true;
			}
			return false;
		}


		public static boolean setFileCertPassword(String filePassword){
			if(fileCertPassword == null){
				fileCertPassword = filePassword;
				return true;
			}
			return false;
		}

		



		public static boolean testAndSetSynchronizationState() {
			synchronizationLock.lock();
			if(synchronizationStatus == false){
				synchronizationStatus = true;
				synchronizationLock.unlock();
				return false;
			}
			else{
				synchronizationLock.unlock();
				return true;
			}
		}
		

		public static void unsetSynchronizationState() {
			synchronizationLock.lock();
			synchronizationStatus = false;
			synchronizationLock.unlock();
		}


		public static ServerStatus getServerStatus() {
			return serverStatus;
			
		}


		public static void setCompleteOpQueue(Queue<JobOperation> outConsOp) {
			completeOpQueue = outConsOp;
			
		}
		public static Queue<JobOperation> getCompleteOpQueue(){
			return completeOpQueue;
		}


		public static void setPrepareOpQueue(Queue<JobOperation> queue) {
			prepareOpQueue = queue;
			
		}
		public static Queue<JobOperation> getPrepareOpQueue(){
			return prepareOpQueue;
		}


		public static void setNeedToGlobalSync(boolean b) {
			needToGlobalSync = b;
		}
		
		public static boolean getNeedToGlobalSync() {
			return needToGlobalSync;
		}


		public static long getMaxTimeMantainUploadingFlag() {
			return maxTimeMantainUploadingFlag;
		}


		public static void setExitigState() {
			exitingState = true;	
		}

		public static boolean getExitingState(){
			return exitingState;
		}


		public static int getWaitBeforeSynch() {
			return waitBeforeSynch;
		}


		public static void setStateBeforeMaintenance(ServerStatus status) {
			serverStatusBeforeMaintenance  = status;
			
		}
		
		public static ServerStatus getStateBeforeMaintenance() {
			return serverStatusBeforeMaintenance;
			
		}

		public static void deleteStateBeforeMaintenance() {
			serverStatusBeforeMaintenance = null;
			
		}

		

	}


	public static String getCurrentServerAvailableSpace(){

		long availableSpaceLong = 0L;
		try {
			File file = new File("/");
			availableSpaceLong = file.getFreeSpace();//bytes
			//CONVERSION DISK SPACE MEASURE BYTE -> MB
			//1048576L = 1024*1024
			availableSpaceLong = Long.divideUnsigned(availableSpaceLong,1048576L); //MB
		}catch (Exception e){
			return "0";
		}
		return Long.toString(availableSpaceLong);
	}

	public static String getCurrentServerTotalSpace(){
		long totalSpaceLong = 0L;
		try {
			File file = new File("/");
			totalSpaceLong = file.getTotalSpace();//bytes
			//CONVERSION DISK SPACE MEASURE BYTE -> MB
			//1048576L = 1024*1024
			totalSpaceLong = Long.divideUnsigned(totalSpaceLong,1048576L); //MB
		}catch (Exception e){
			return "0";
		}
		return Long.toString(totalSpaceLong);
	}
	
	public static boolean updateVariables(List<Server> serverTable) {
		long myId = Variables.getServerId();
		long myLabelEnd = 0,myLabelStart;
		int myGroupId = 0;
				
		Iterator<Server> iter =  serverTable.iterator();
		
		boolean finded = false;
		while(iter.hasNext() && !finded){
			Server s = iter.next();
			if(s.getId()==myId){
				myLabelEnd = s.getLabel();
				myGroupId = s.getGroupId();
				finded = true;
			}
		}
		if(!finded){
			PadFsLogger.log(LogLevel.ERROR, "impossible to find my serverId in the serverList");
			return false;
		}
		
		
		long greaterServerLabelLowerThanMine=0;
		iter =  serverTable.iterator();
		finded = false;
		while(iter.hasNext() ){
			Server s = iter.next();
			if(s.getGroupId()==myGroupId){
				if(Long.compareUnsigned(greaterServerLabelLowerThanMine,s.getLabel()) < 0
					&&
					Long.compareUnsigned(s.getLabel(),myLabelEnd) < 0){
						greaterServerLabelLowerThanMine = s.getLabel();
						finded = true;
				}
			}
		}
		if(finded)
			myLabelStart = greaterServerLabelLowerThanMine +1;
		else
			myLabelStart = 0;
		
		
		if(!Variables.setGroupId(myGroupId)){
			PadFsLogger.log(LogLevel.ERROR, "setting serverGroupId failed");
			return false;
		}
		if(!Variables.setLabelStart(myLabelStart)){
			PadFsLogger.log(LogLevel.ERROR, "setting serverGroupId failed");
			return false;
		}
		if(!Variables.setLabelEnd(myLabelEnd)){
			PadFsLogger.log(LogLevel.ERROR, "setting serverGroupId failed");
			return false;
		}
		
		PadFsLogger.log(LogLevel.TRACE, "groupId: " + myGroupId + " myLabelStart: " + myLabelStart+ " myLabelEnd: " + myLabelEnd); 
				
		return true;
		
		
	}

	
	
	
	/* must be readOnly except for CompleteOp thread*/
	private static boolean isCompleteOpPaused = false;
	
	/* must be readOnly except for the thread running SynchGlobal */
	private static boolean requestedPauseCompleteOpThread = false;

	private static Object waitSynchThread = new Object();
	private static Object waitExit = new Object();
	
	
	
	
	/**
	 * wait until the completeOp thread has completed to execute all its operations
	 * 
	 * must be used only inside the thread running the SynchGlobal
	 */
	public static boolean waitStopCompleteOpThread() {	
		synchronized (waitSynchThread) {
	         while (isCompleteOpPaused == false){
				try {
					requestedPauseCompleteOpThread = true;
					Variables.getCompleteOpQueue().add(new NullOperation()); //create a fake operation to make sure that the CompleteOpThread will not be stacked
					waitSynchThread.wait();
				} catch (InterruptedException e) {
					PadFsLogger.log(LogLevel.ERROR, e.getMessage());
					return false;
				}
	         }
	         
	     }
		return true;
		
	}
	
	/**
	 * wait until the ExitOperation flows to the completeOp Thread
	 * 
	 * must be used only inside the Shutdown Thread
	 */
	public static boolean waitExit() {	
		synchronized (waitExit) {
	         	try {
	         		Variables.getPrepareOpQueue().add(new ExitOperation());
					waitExit.wait();
				} catch (InterruptedException e) {
					PadFsLogger.log(LogLevel.ERROR, e.getMessage());
					return false;
				}
	     }
		return true;
		
	}
	
	/**
	 * signal that the ExitOperation reached the completeOp Thread
	 * 
	 * must be used only inside the Shutdown Thread
	 */
	public static boolean signalExitCompleted() {	
		synchronized (waitExit) {
			waitExit.notify();
		}		
		return true;
		
	}
	
	
	public static void restartCompleteOpThread(){
		synchronized (waitSynchThread) {
			requestedPauseCompleteOpThread = false;
			waitSynchThread.notify();
			
		}
	}

	/**
	 * wait until the synch will send a notify
	 * must be used only inide the completeOp thread
	 */
	public static boolean sleepingUntilExitSynchState() {
		synchronized (waitSynchThread) {
			waitSynchThread.notify();
			while (requestedPauseCompleteOpThread == true){
				try {
					isCompleteOpPaused = true;
					waitSynchThread.wait();
				} catch (InterruptedException e) {
					PadFsLogger.log(LogLevel.ERROR, e.getMessage());
					return false;
				}
	         }
			isCompleteOpPaused = false;
			
		}
		return true;
	}

	public static boolean updateServerStatus(Long idServer, ServerStatus serverStatus) {
		if(idServer == null){
			PadFsLogger.log(LogLevel.ERROR, "idServer cannot be null");
			return false;
		}
		
		Iterator<Server> it = Variables.getServerList().iterator();
		while(it.hasNext()){
			Server s= it.next();
			if(s.getId().equals(idServer)){
				s.setStatus(serverStatus);
				return true;
			}
		}
		
		PadFsLogger.log(LogLevel.ERROR, "cannot find the server with id "+idServer);
		return false;
		
	}

	public static String getLogicalPath( String path) {
		if(path == null)
			return null;
		return normalizePath(ConfigurationManager.slashCheck(path, Constants.fileSeparator));
	}
	
	public static String getLogicalPath( String path, String fileName) {
		if(fileName == null){
			PadFsLogger.log(LogLevel.ERROR, "fileName cannot be null");
			return null;
		}
		
		if(path == null || path.equals(""))
			return Constants.fileSeparator + fileName;
		
		String x = ConfigurationManager.slashCheck(path, Constants.fileSeparator);
		x = removeEndingSlashes(x);
		x = removeStartingSlashes(x);
		
		if(x == null || x.equals(""))
			return Constants.fileSeparator + fileName;

		return Constants.fileSeparator + x + Constants.fileSeparator + fileName;
	
	}

	

	public synchronized  static RestTemplate generateRestTemplate(){
		RestTemplate restTemplate = new RestTemplate(clientHttpRequestFactory(Constants.timeoutHttpConnection));
		try {
			//if https is enabled overwrite the verify function for the hostname = localhost problem
			if (Variables.getProtocol().equals("https")) {
				restTemplate.setRequestFactory(new SimpleClientHttpRequestFactory() {
					@Override
					protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
						HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
							public boolean verify(String hostname, SSLSession session) {
								return true;
							}
						});
						super.prepareConnection(connection, httpMethod);
					}
				});
			}
		}catch (Exception e){
			return restTemplate;
		}
		return restTemplate;
	}

	public synchronized static AsyncRestTemplate generateAsyncRestTemplate(){
		return new AsyncRestTemplate();
	}
	
	
	/**
	 * 
	 * @param timeout timeout value in seconds
	 * @return ClientHttpRequestFactory
	 */
	private static ClientHttpRequestFactory clientHttpRequestFactory(int timeout){
		HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
		factory.setReadTimeout(timeout*1000); 
		factory.setConnectTimeout(timeout*1000);
		factory.setConnectionRequestTimeout(timeout*1000);
		return factory;
	}
	
	
	public static long[] idListToArray(String serverIdList){
				if(serverIdList == null)
					return null;
		
				if(serverIdList.equals("")){
					return new long[]{};
				}
		
				String[] TMP_serverIdGroup = serverIdList.split(",");
				long[] serversIdGroup =  new long[TMP_serverIdGroup.length];
				for(int i=0;i<TMP_serverIdGroup.length;i++){
					serversIdGroup[i] = Long.parseUnsignedLong(TMP_serverIdGroup[i]);
				}
				
				return serversIdGroup;
	}

	static public String removeEndingSlashes(String path){
		if(path != null)
			return path.replaceAll("\\\\*$", "").replaceAll("/*$", "");
		return null;
	}
	
	public static String removeStartingSlashes(String path) {
		if(path != null)
			return path.replaceAll("^/*", "").replaceAll("^\\\\*", "");
		return null;
	}

	public static Long getLabel(Integer idOwner, String path) {
		String username = SqlManager.getUsername(idOwner);
		return getLabel(username,path);
	}
	
	public static Long getLabel(String username, String path) {
		if(username != null && path != null)
			return Constants.hashFunction.evaluate(username+path);
		return null;
	}
	
	
		
	/**
	 * given idOwner and path, it select the servers managing the parentPath asking the permission of idUser in the parentPath.
	 * Collect the responses. Discards the one with a lower globalConsRunId and return the one with higher groupConsRunId.
	 * Actually if it receives a mojority of responses with the same groupConsRunId, it does not do RestRequest to other servers.
	 * 
	 * If no permission are specified in the parentPath, the permission in the parentPath is resolved recursively by the server. (calling other servers)
	 * 
	 * @param idUser
	 * @param idOwner
	 * @param path
	 * @return null if no server responses are arrived or if no server responses are valid
	 * @return Permission of idUser in <idOwner,parentPath(path)> otherwise
	 */
	public static Permission getParentPermission(Integer idUser, Integer idOwner, String path) {

		String parentPath = SystemEnvironment.getParentPath(path);
		PadFsLogger.log(LogLevel.TRACE, "getParentPermission start - idUser:"+idUser+" idOwner:"+idOwner+" path:"+path+" parentPath:"+parentPath,"red");
		if(path.equals(Constants.rootDirectory)){
			return Permission.none;
		}
		
		/* retrieve the group managing the parentDirectory */
		Long serverLabel;
		serverLabel = SystemEnvironment.getLabel(idOwner, parentPath);
		
		long[] serverIds = SqlManager.getIdFromConsensusLabel(serverLabel);
		
		ConsensusServerGroup parentGroup = new ConsensusServerGroup(serverIds);
		Iterator<Server> it = parentGroup.iterator();
		
		
		/* ask for permission on parentDirectory. it will be resolved recursively */
		Permission p = null;
		int numberOfMaximumIdConsRunSeen = 0;
		Long  maximumIdConsRunSeen = null;
		
		boolean majorityReached = false;
		while(it.hasNext() && !majorityReached){
			Server s = it.next();
			RestGetPermission temp;
			temp = RestServer.getPermission(s,idUser,idOwner,parentPath);
			if(temp != null && temp.getStatus() != Rest.status.error){
				/* if the server is alive, and if it has answered correctly */
				
				if(temp.getGlobalConsRunId() == null){
					PadFsLogger.log(LogLevel.DEBUG, "globalConsRunId of RestGetPermission is null");
					continue;
				}
				
				Long myConsRunId = Variables.consensusVariableManager.getConsVariables(Constants.globalConsensusGroupId).getIdConsRun();
				if(temp.getGlobalConsRunId() > myConsRunId){
					PadFsLogger.log(LogLevel.DEBUG, "globalConsRunId of RestGetPermission is greater than mine. GlobalSynch needed");
					Variables.setNeedToGlobalSync(true);
					return null; //this may cause an internalServerEror
				}
				else if(temp.getGlobalConsRunId() < myConsRunId){
					PadFsLogger.log(LogLevel.DEBUG, "globalConsRunId of RestGetPermission is lower than mine. Ignore it.");
					continue;
				}
				
				
				/*
				 *  store the permission associated with the higher idConsRun of the messages. 
				 *  Count the number of times the higher idConsRun is received. 
				 *  If we receive a majority, we can avoid other Rest request to other servers
				 *  
				 *  TODO implement request asynchronously
				 */
				if(maximumIdConsRunSeen != null){
					if(temp.getGroupConsRunId() == maximumIdConsRunSeen){
						PadFsLogger.log(LogLevel.TRACE, "increment getPermission counter");
						numberOfMaximumIdConsRunSeen++;
					}
					else if(temp.getGroupConsRunId() > maximumIdConsRunSeen){
						PadFsLogger.log(LogLevel.TRACE, "getPermission succeded founding an higher idConsRun");
						p = temp.getPermission();
						maximumIdConsRunSeen = temp.getGroupConsRunId();
						numberOfMaximumIdConsRunSeen = 1;
					}
				}
				else{
					PadFsLogger.log(LogLevel.TRACE, "first getPermission succeded");
					p = temp.getPermission();
					maximumIdConsRunSeen = temp.getGroupConsRunId();
					numberOfMaximumIdConsRunSeen = 1;
				}
				
			
			
				if(numberOfMaximumIdConsRunSeen > Constants.replicaNumber/2){
					majorityReached = true;
				}
			}
			
		}
		return p;
		
	}

	
	
}
