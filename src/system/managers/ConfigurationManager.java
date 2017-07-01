package system.managers;

import java.io.File;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.NodeList;

import system.SystemEnvironment;
import system.SystemEnvironment.Constants;
import system.SystemEnvironment.Variables;
import system.containers.Server;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;

public class ConfigurationManager {
	

	
	static private String fileName;


	static public boolean setSystemEnviroment(String fileName){		
				
		/* TODO mettere un try catch per ogni impostazione del file di config.  
		 * nel catch mettere un log FATAL (o ERROR per impostazioni opzionali) per segnalare all'utente che 
		 * l'impostazione non e' stata letta.
		 * NECESSARIO per catchare eccezioni lanciate da Long.valueOf e simili
		 */ 
		 
		
		/* TODO validare il file.xml prima di aprirlo con XmlManager */
		
		ConfigurationManager.fileName = fileName;
		XmlManager x = new XmlManager(ConfigurationManager.fileName);
		
		// Read path for logging
		String TMP_logPath = x.readXML("/configuration/log/path");
		if( TMP_logPath == null || TMP_logPath.equals("none") ){ //IF NO LOG FILE
			TMP_logPath = null;
		}else{
			TMP_logPath = (TMP_logPath.equals(""))?"padFs.log":TMP_logPath;
			TMP_logPath = slashCheck(TMP_logPath);
		}
		
		if( !Variables.setLogPath(TMP_logPath)){
			PadFsLogger.log(LogLevel.ERROR, "setLogPath FAILED: '"+TMP_logPath+"'");
			return false;
		}
			
		
		
		// Read if overwriteLog
		Boolean TMP_owLog = Boolean.valueOf(x.readXML("/configuration/log/overwrite"));
		if( !Variables.setOwLog(TMP_owLog)){
			PadFsLogger.log(LogLevel.ERROR, "setOwLog FAILED: '"+TMP_owLog+"'");
			return false;
		}
		
		// Read if colouredOutput
		Boolean TMP_colouredOutput = Boolean.valueOf(x.readXML("/configuration/log/colouredOutput"));
		if( TMP_colouredOutput== null || !Variables.setColouredOutput(TMP_colouredOutput)){
			PadFsLogger.log(LogLevel.WARNING, "setColouredOutput FAILED: '"+TMP_colouredOutput+"'");
			//return false; optional parameter: do not return false
		}
		
		
		
		
		//Read Debug configuration
		String TMP_deb = x.readXML("/configuration/log/level");
		LogLevel TMP_logLevel;
		if(TMP_deb == null || TMP_deb.equals("")){
			TMP_logLevel = LogLevel.WARNING;
		}else{
			switch(TMP_deb){
				case "FATAL":
					TMP_logLevel = LogLevel.FATAL;
					break;
				case "DEBUG":
					TMP_logLevel = LogLevel.DEBUG;
					break;
				case "TRACE":
					TMP_logLevel = LogLevel.TRACE;
					break;
				case "INFO":
					TMP_logLevel = LogLevel.INFO;
					break;
				case "WARNING":
					TMP_logLevel = LogLevel.WARNING;
					break;
				case "CONFIG":
					TMP_logLevel = LogLevel.CONFIG;
					break;
				case "NONE":
					TMP_logLevel = LogLevel.NONE;
					break;
				default:
					TMP_logLevel = LogLevel.NONE;
					break;
			}
		}
		if( !Variables.setLogLevel(TMP_logLevel)){
			PadFsLogger.log(LogLevel.ERROR, "setLogLevel FAILED: '"+TMP_logLevel+"'");
			return false;
		}
		
		PadFsLogger.createLog(Variables.getLogPath());
		
		// Read retry number
		String TMP_retryNumber = x.readXML("/configuration/system/retryNumber");
		TMP_retryNumber = (TMP_retryNumber == null || TMP_retryNumber.equals(""))?"3":TMP_retryNumber;
		if( !Variables.setRetryNumber(Integer.parseInt(TMP_retryNumber)) ){
			PadFsLogger.log(LogLevel.WARNING, "setRetryNumber FAILED: '"+TMP_retryNumber+"'");
			//return false; optional parameter: do not return false
		}
		
		
		// Read sleepTime_CheckReplicasAlive
		String TMP_sleepTime = x.readXML("/configuration/system/sleepTime_CheckReplicasAlive");
		Long TMP_sleepTimeL = (TMP_sleepTime == null || TMP_sleepTime.equals(""))?Constants.sleepTime_CheckReplicasAliveDefault:Long.valueOf(TMP_sleepTime);
		if( !Variables.setSleepTime_CheckReplicasAlive(TMP_sleepTimeL)){
			PadFsLogger.log(LogLevel.WARNING, "setSleepTime_CheckReplicasAlive FAILED: '"+TMP_sleepTime+"'");
			//return false; optional parameter: do not return false
		}

		// Read Wait Milliseconds Before Retry
		String TMP_waitMillisecondsBeforeRetry = x.readXML("/configuration/system/waitMillisecondsBeforeRetry");
		TMP_waitMillisecondsBeforeRetry = (TMP_waitMillisecondsBeforeRetry == null || TMP_waitMillisecondsBeforeRetry.equals(""))?Constants.waitMillisecondsBeforeRetry:TMP_waitMillisecondsBeforeRetry;
		if( !Variables.setWaitMillisecondsBeforeRetry(Integer.parseInt(TMP_waitMillisecondsBeforeRetry)) ){
			PadFsLogger.log(LogLevel.WARNING, "setWaitMillisecondsBeforeRetry FAILED: '"+TMP_waitMillisecondsBeforeRetry+"'");
			return false; //optional parameter but must be setted
		}
		
		// Read Wait Milliseconds Before Retry
		String TMP_waitBeforeSynch= x.readXML("/configuration/system/waitBeforeSynch");
		TMP_waitBeforeSynch = (TMP_waitBeforeSynch == null || TMP_waitBeforeSynch.equals(""))?Constants.waitBeforeSynch:TMP_waitBeforeSynch;
		if( !Variables.setWaitBeforeSynch(Integer.parseInt(TMP_waitBeforeSynch)) ){
			PadFsLogger.log(LogLevel.WARNING, "setWaitBeforeSynch FAILED: '"+TMP_waitBeforeSynch+"'");
			return false; // optional parameter but must be setted
		}

		// Read Wait Milliseconds Heart Beat
		String TMP_waitMillisecondsHeartbeat = x.readXML("/configuration/system/waitMillisecondsHeartBeat");
		Integer TMP2_waitMillisecondsHeartbeat; 
		TMP2_waitMillisecondsHeartbeat = (TMP_waitMillisecondsHeartbeat == null || TMP_waitMillisecondsHeartbeat.equals(""))?Constants.waitMillisecondsHeartbeat:Integer.parseInt(TMP_waitMillisecondsHeartbeat);
		if( !Variables.setWaitMillisecondsHeartbeat(TMP2_waitMillisecondsHeartbeat) ){
			PadFsLogger.log(LogLevel.WARNING, "setWaitMillisecondsBeforeRetry FAILED: '"+TMP_waitMillisecondsHeartbeat+"'");
			//return false; optional parameter: do not return false
		}
		
		// Read Wait Milliseconds Heart Beat
		String TMP_maxTimeMantainFlaggedFile = x.readXML("/configuration/system/maxTimeMantainUploadingFlag");
		Long TMP2_maxTimeMantainFlaggedFile; 
		TMP2_maxTimeMantainFlaggedFile = (TMP_maxTimeMantainFlaggedFile.equals(""))?Constants.maxTimeMantainUploadingFlag:Integer.parseInt(TMP_maxTimeMantainFlaggedFile);
		if( !Variables.setMaxTimeMantainUploadingFlag(TMP2_maxTimeMantainFlaggedFile) ){
			PadFsLogger.log(LogLevel.WARNING, "setMaxTimeMantainUploadingFlag FAILED: '"+TMP_maxTimeMantainFlaggedFile+"'");
			//return false; optional parameter: do not return false
		}
		
		// Read Server Id
		String TMP_serverId = x.readXML("/configuration/server/id");
		TMP_serverId = (TMP_serverId == null || TMP_serverId.equals(""))?null:TMP_serverId;
		{	Long serverId;
			if(TMP_serverId == null)
				serverId = null;
			else 
				serverId = Long.valueOf(TMP_serverId);
			if( !Variables.setServerId(serverId) ){
				PadFsLogger.log(LogLevel.WARNING, "setServerId FAILED: '"+TMP_serverId+"'");
				return false;
			}
		}

		// Read Server IP
		String TMP_serverIP = x.readXML("/configuration/server/ip");
		TMP_serverIP = (TMP_serverIP == null || TMP_serverIP.equals(""))?null:TMP_serverIP;
		if( !Variables.setConfigServerIP(TMP_serverIP)){
			PadFsLogger.log(LogLevel.WARNING, "setServerIP FAILED: '"+TMP_serverIP+"'");
			return false;
		}
		
		// Read Server Port
		String TMP_serverPort = x.readXML("/configuration/server/port");
		TMP_serverPort = (TMP_serverPort == null || TMP_serverPort.equals(""))?"8080":TMP_serverPort;
		if( !Variables.setServerPort(TMP_serverPort)){
			PadFsLogger.log(LogLevel.WARNING, "setServerPort FAILED: '"+TMP_serverPort+"'");
			return false;
		}


		//PROTOCOL SELECTION
		String TMP_proto = x.readXML("/configuration/server/protocol");
		if(TMP_proto == null || TMP_proto.equals("")){
			PadFsLogger.log(LogLevel.WARNING,"PROTOCOL not set, default value: "+Constants.defaultProtocol);
			TMP_proto = Constants.defaultProtocol;
		}
		if( !Variables.setProtocol(TMP_proto)){
			PadFsLogger.log(LogLevel.FATAL, "setProtocol FAILED: '"+TMP_proto+"'");
			return false;
		}

		//IF (HTTPS){ set keyfile AND PASSWORD }
		String TMP_certfile = x.readXML("/configuration/server/fileCertPath");
		if( TMP_certfile == null || TMP_certfile.equals("") && Variables.getProtocol().equals("https") ){
			PadFsLogger.log(LogLevel.FATAL,"FILE CERTIFICATION not set: protocol choosen is HTTPS certification file path is not set!");
		}else {
			if(Variables.getProtocol().equals("https")) {
				if (!checkFileExists(TMP_certfile))
					PadFsLogger.log(LogLevel.FATAL, "FILE CERTIFICATION: check the fileCertPath (PATH ERROR)");

				if (!Variables.setFileCertPath(TMP_certfile)) {
					PadFsLogger.log(LogLevel.FATAL, "setFileCertPath FAILED: '" + TMP_certfile + "'");
					return false;
				}
			}
		}

		//IF (HTTPS){ set keyfile AND PASSWORD }
		String TMP_certpassword = x.readXML("/configuration/server/fileCertPassword");
		if( TMP_certpassword == null || TMP_certpassword.equals("") && Variables.getProtocol().equals("https") ){
			PadFsLogger.log(LogLevel.FATAL,"PASSWORD FILE CERTIFICATION not set: protocol choosen is HTTPS password certification file needed!");
		}else {
			if(Variables.getProtocol().equals("https")) {
				if (!Variables.setFileCertPassword(TMP_certpassword)) {
					PadFsLogger.log(LogLevel.FATAL, "setFileCertPassword FAILED: '" + TMP_certpassword + "'");
					return false;
				}
			}
		}

		
		// Read fileSystemPath
		String TMP_fileSystemPath = x.readXML("/configuration/fileSystem/Path");
		TMP_fileSystemPath = (TMP_fileSystemPath == null || TMP_fileSystemPath.equals(""))?"FS":TMP_fileSystemPath;
		TMP_fileSystemPath = slashCheck(TMP_fileSystemPath);
		if( !Variables.setFileSystemPath(TMP_fileSystemPath)){
			PadFsLogger.log(LogLevel.FATAL, "setFileSystemPath FAILED: '"+TMP_fileSystemPath+"'");
			return false;
		}
		if(!createPath(TMP_fileSystemPath)){
			PadFsLogger.log(LogLevel.FATAL, "createPath FAILED: '"+TMP_fileSystemPath+"'");
			return false;
		}
		
		// Read fileSystemTMPPath
		String TMP_fileSystemTMPPath = x.readXML("/configuration/fileSystem/TMPPath");
		TMP_fileSystemTMPPath = (TMP_fileSystemTMPPath == null || TMP_fileSystemTMPPath.equals(""))?TMP_fileSystemPath+Variables.getOSFileSeparator()+"temp":TMP_fileSystemTMPPath;
		TMP_fileSystemTMPPath = slashCheck(TMP_fileSystemTMPPath);
		if( !Variables.setFileSystemTMPPath(TMP_fileSystemTMPPath)){
			PadFsLogger.log(LogLevel.FATAL, "setFileSystemTMPPath FAILED: '"+TMP_fileSystemTMPPath+"'");
			return false;
		}
		if(!createPath(TMP_fileSystemTMPPath) ){
			PadFsLogger.log(LogLevel.FATAL, "createTMPPath FAILED: '"+TMP_fileSystemTMPPath+"'");
			return false;
		}

		String totalSpace = SystemEnvironment.getCurrentServerTotalSpace();
		if(totalSpace.compareTo("0")!=0) {
			Variables.setTotalSpace(totalSpace);
		}

		String availableSpace = SystemEnvironment.getCurrentServerAvailableSpace();
		if(!availableSpace.equals("0")) {
			Variables.setAvailableSpace(availableSpace);
		}


		// Read serverPassword
		String TMP_serverPassword = x.readXML("/configuration/password/server");
		if(TMP_serverPassword == null || TMP_serverPassword.equals("")){
			PadFsLogger.log(LogLevel.WARNING,"The server password is not set, default value: "+Constants.defaultServerPassword);
			TMP_serverPassword = Constants.defaultServerPassword;
		}
		if( !Variables.setServerPassword(TMP_serverPassword)){
			PadFsLogger.log(LogLevel.FATAL, "setServerPassword FAILED: '"+TMP_serverPassword+"'");
			return false;
		}

		// Read panelPassword
		String TMP_controlPanelPassword = x.readXML("/configuration/password/controlPanel");
		if(TMP_controlPanelPassword == null || TMP_controlPanelPassword.equals("")){
			PadFsLogger.log(LogLevel.WARNING,"The control panel password is not set, default value: "+Constants.defaultPanelPassowrd);
			TMP_controlPanelPassword = Constants.defaultPanelPassowrd;
		}
		if( !Variables.setPanelPassword(TMP_controlPanelPassword)){
			PadFsLogger.log(LogLevel.FATAL, "setServerPassword FAILED: '"+TMP_controlPanelPassword+"'");
			return false;
		}


		//SERVER LIST
		NodeList serverListXML = x.readXMLList("/configuration/serverList/server");
		List<Server> TMP_servList = XmlManager.traverseServerList(serverListXML);
		if(TMP_serverIP != null && TMP_serverPort != null && TMP_servList != null){
			Server me = new Server(TMP_serverIP,TMP_serverPort);
			TMP_servList.add(me);
		}
		if(TMP_servList == null ||  !Variables.setServerList(TMP_servList)){
			PadFsLogger.log(LogLevel.FATAL, "retrieve ServerList FAILED");
			return false;
		}
		if(Variables.getServerList().size()<=0){
			PadFsLogger.log(LogLevel.FATAL, "ServerList is EMPTY");
			return false;
		}
		if(Variables.getServerList().size()< Constants.replicaNumber){
			PadFsLogger.log(LogLevel.FATAL, "ServerList has not enough servers. Put at least replicaNumber="+Constants.replicaNumber+" servers");
			return false;
		}



		
		return true;
	}
	
	private static boolean createPath(String path){
		/* creazione albero directory */
		try{
			File fc = new File(path);
			if( fc.isDirectory() ) 
				return true;
			//File directoryPath = fc.getParentFile();
			return fc.mkdirs();
			
		} catch(Exception e){
			return false;
		}
		
		
	}
	
	/**
	 * Replace the slash in the string s as parameter.
	 * Check the correct slash of the system and replace 
	 * @param s String with the path
	 * @return s with the correct slash|path
	 */
	static private String slashCheck(String s){
		Pattern p;
		String REGEX;
		String REPLACE;
		Matcher m;
		
		REPLACE = Matcher.quoteReplacement(Variables.getOSFileSeparator());
		if(Variables.getOSFileSeparator().equals("\\")){
			REGEX = "/";
		}else{
			REGEX = "\\\\";
		}
		
		p = Pattern.compile(REGEX);
		m = p.matcher(s); 
		s = m.replaceAll(REPLACE);
	
		return s;
	}
	
	static public String slashCheck(String s,String separator){
		Pattern p;
		String REGEX;
		String REPLACE;
		Matcher m;
		
		if (separator == null){
			REPLACE = Matcher.quoteReplacement(Variables.getOSFileSeparator());
			if(Variables.getOSFileSeparator().equals("\\")){
				REGEX = "/";
			}else{
				REGEX = "\\\\";
			}
		}
		else{
			REPLACE = Matcher.quoteReplacement( separator );
			if( separator.equals("/") ){
				REGEX = "\\\\";
			}else REGEX = "/";
		}
		
		if(REGEX!=null && REPLACE != null){
			p = Pattern.compile(REGEX);
			m = p.matcher(s); 
			s = m.replaceAll(REPLACE);
		}
		return s;
	}


	/**
	 * Check if the filePath exists as file
	 * @param filePath the path of the file
	 * @return <code>true</code> if the file exists
	 * 		   <code>false</code> otherwise
     */
	static public boolean checkFileExists(String filePath){
		if(filePath == null || filePath.equals(""))
			return false;

		try {
			File f = new File(filePath);
			if (f.exists() && !f.isDirectory()) {
				return true;
			}
		}catch (Exception e){
			return false;
		}

		return false;
	}

}
