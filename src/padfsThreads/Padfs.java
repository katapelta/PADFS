package padfsThreads;


import java.io.File;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;

import jobManagement.consensus.JobConsMsg;
import jobManagement.jobOperation.JobOperation;
import jobManagement.jobOperation.JobOperation.PriorityQueueComparator;
import restInterface.RestServer;
import system.SystemEnvironment;
import system.SystemEnvironment.Constants;
import system.SystemEnvironment.Variables;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;
import system.managers.ConfigurationManager;
import system.managers.SqlManager;

public class Padfs {
	private static BlockingQueue<JobConsMsg<?>> inConsMsg;
	private static PriorityBlockingQueue<JobOperation> inConsOp;
	public static PriorityBlockingQueue<JobOperation> inOp;
	private static BlockingQueue<JobOperation> outConsOp;

	static PriorityQueueComparator pqc = new PriorityQueueComparator();



	/**
	 * The method init all the queue and the threads of the server
	 * @param confPath the path of the configuration file
	 */
	public static void init(String confPath){
		PadFsLogger p = new PadFsLogger();
		p.setName("PadFsLogger");
		p.start();

		// READ ConfigFile

		if(!ConfigurationManager.setSystemEnviroment(confPath)){
			PadFsLogger.log(LogLevel.FATAL, "missing some mandatory configuration");
			return;
		}

		//set my space
		Variables.setAvailableSpace(SystemEnvironment.getCurrentServerAvailableSpace());
		Variables.setTotalSpace(SystemEnvironment.getCurrentServerTotalSpace());

		system.logger.PadFsLogger.log(LogLevel.CONFIG, "*********   INIT PHASE   *********");

		//DB START
		if(!SqlManager.open()){
			system.logger.PadFsLogger.log(LogLevel.FATAL, "failed initializing DB");
		}




		//INIT BlockingQueue
		inConsMsg = new LinkedBlockingQueue<JobConsMsg<?>>(Constants.queueCapacity);
		inConsOp  = new PriorityBlockingQueue<JobOperation>(10,pqc);
		inOp 	  = new PriorityBlockingQueue<JobOperation>(10,pqc);
		outConsOp = new LinkedBlockingQueue<JobOperation>();



		Variables.setCompleteOpQueue(outConsOp);
		Variables.setPrepareOpQueue(inOp);

		System.setProperty("http.maxConnections", Constants.maxConnections);

		//INIT REST IN
		system.logger.PadFsLogger.log(LogLevel.CONFIG, "- INIT REST");
		try {
			RestServer.restServerStart(inOp, inConsMsg, Variables.getServerPort(), Variables.getConfigServerIP(), Variables.getLogLevel());
		} catch ( Exception e) {
			system.logger.PadFsLogger.log(LogLevel.ERROR, "SERVER TOMCAT START - PORT NOT FREE - "+ e.getClass().getName() + ": " + e.getMessage());
			System.exit(-2);
		}


		if(Variables.getServerId() == null && SqlManager.getServerList().size() > 0){
			/* retrieve the id from the DB only if the "servers" table is not empty and we have not an id specified in the config file */
			Long oldServerId = SqlManager.retrieveOldIdFromDB();
			if(oldServerId == null){
				PadFsLogger.log(LogLevel.FATAL,"cannot start without an id. please check the config file or database");
			}
			Variables.setServerId(oldServerId);
		}

		//create the Merkle Tree
		Variables.setMerkleTree(SqlManager.generateMerkleTreeFromDB());


		//PREPARE OP. TH
		system.logger.PadFsLogger.log(LogLevel.DEBUG, "- PREPARE OP. TH");
		PrepareOp po = new PrepareOp( inOp, inConsOp );
		po.setName("PrepareOp");

		//CONSENSUS
		system.logger.PadFsLogger.log(LogLevel.DEBUG, "- CONSENSUS OP. TH");
		Consensus cn = new Consensus( inConsOp, inConsMsg, outConsOp );
		cn.setName("Consensus");

		//COMPLETEOP
		system.logger.PadFsLogger.log(LogLevel.DEBUG, "- COMPLETEOP TH");
		CompleteOp co = new CompleteOp( outConsOp );
		co.setName("CompleteOp");

		system.logger.PadFsLogger.log(LogLevel.DEBUG, "- HEARTBEAT TH");
		Heartbeat hb = new Heartbeat();
		hb.setName("Heartbeat");


		system.logger.PadFsLogger.log(LogLevel.DEBUG, "- FileManager TH");
		FileManager fm = new FileManager(inOp);
		fm.setName("FileManager");


		system.logger.PadFsLogger.log(LogLevel.DEBUG, "- GarbageCollector TH");
		GarbageCollector gc = new GarbageCollector();
		gc.setName("GarbageCollector");

		Shutdown shutdown = new Shutdown();
		shutdown.setName("Shutdown");


		//add exit hook
		Runtime.getRuntime().addShutdownHook(shutdown);

		system.logger.PadFsLogger.log(LogLevel.INFO, "- START INITIAL THREADS");
		po.start();
		cn.start();
		co.start();


		boolean result = false;
		int tries = 0;
		while(!	Variables.getIAmInTheNet() && Variables.getExitingState() == false){
			tries++;

			//try to start the network
			PadFsLogger.log(LogLevel.INFO, "BootNet attempt number "+(tries), "WHITE", "YELLOW", true);

			result = RestServer.bootNet(Variables.getServerList());
			if(result == false){
				system.logger.PadFsLogger.log(LogLevel.INFO, "Bootnet failed. waiting...");
				//break;
			}


			//wait before retry
			do{
				try {
					system.logger.PadFsLogger.log(LogLevel.DEBUG, "Waiting...");
					Thread.sleep(Variables.getWaitMillisecondsBeforeRetry());
				} catch (InterruptedException e) {
					PadFsLogger.log(LogLevel.ERROR, e.getMessage());
				}
			}while(Variables.isNetworkStarting());

		}


		if(Variables.getIAmInTheNet()){

			system.logger.PadFsLogger.log(LogLevel.DEBUG, "- STARTING INDEPENDENT THREADS");

			Variables.addOutOfFLowThread(hb);
			hb.start();//start the hearbit thread after i'm in the net

			Variables.addOutOfFLowThread(fm);
			fm.start();

			Variables.addOutOfFLowThread(gc);
			gc.start();


			system.logger.PadFsLogger.log(LogLevel.CONFIG, "**********************************");
			system.logger.PadFsLogger.log(LogLevel.CONFIG, "******* JOINED THE NET ***********");
			system.logger.PadFsLogger.log(LogLevel.CONFIG, "**********************************");
		}
		else{
			system.logger.PadFsLogger.log(LogLevel.CONFIG, "**********************************");
		}
	}

	/**
	 * Check if the server is in the database and add it
	 * @param ip		the ip address
	 * @param port		the port number
	 * @param idServer	the identifier read from config file
	 */
	public static void  addServerToDb(String ip, String port, long idServer){
		if(!SqlManager.checkServerExists(ip, port)){
			if(!SqlManager.addServerToDB_id(ip, port,idServer)){
        		system.logger.PadFsLogger.log(LogLevel.WARNING, "IP or PORT from Config file not correct - IP: "+ip+"- PORT: "+port);
        	}
		}
	}

	/**
	 * Method that check if a file passed exists or not
	 * @param confPath The file with the path
	 * @return boolean true if file exists
	 * @return boolean false if file NOT exists
	 */
	private static boolean checkConfFile( String confPath ){
		File f = new File(confPath);
		if(f.exists() && !f.isDirectory()) {
			return true;
		}else{
			return false;
		}
	}

	/**
	 * Check if the array of strings as parameter fill the condiction of validity
	 * @param str Array of string elements to check
	 * @return true if its ok
	 * @return false otherwise
	 */
	public static boolean validateString( String [] str ){
		if( str == null ){
			return false;
		}
		for (String s : str) {
			if( s == null || s.equals("") || s.equals(null) ){
	    		return false;
	    	}
		}
		return true;
	}

	/**
	 * The method that allow to terminate the program
	 */
	public static void harakiri() {
		System.exit(1);
	}

	public static void main(String[] args) {
		System.out.println(" - - -  PADFS  - - -");
		if (args.length > 0) {
			if(checkConfFile(args[0])){
				init(args[0]);
			}else{
				System.err.println("CONFIG FILE NOT FOUND");
			}
		}else {
			System.err.println("NO ARGUMENT CONFIG FILE");
		}
	}


}
