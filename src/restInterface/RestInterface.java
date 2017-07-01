package restInterface;

import java.net.URLEncoder;

import org.springframework.core.io.FileSystemResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import jobManagement.jobOperation.JobOperation;
import restInterface.consensus.ProposalNumber;
import system.SystemEnvironment;
import system.SystemEnvironment.Constants.Permission;
import system.SystemEnvironment.Constants.ServerStatus;
import system.SystemEnvironment.Variables;
import system.containers.Server;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;

public class RestInterface {
	public static class Chmod {
		public static final String path = "/chmod/{user}/{password}/{usernameTarget}/{permission}/{usernameOwner}/**";
		public static String generateUrl(String ip, String port, String username, String password, String usernameTarget, Permission permission, String usernameOwnerPath, String path){
			String url = null;
			try{
				 url = urlGenerator("chmod", ip, port, 
						new String[]{username,password,usernameTarget,String.valueOf(permission),usernameOwnerPath,path}
						);
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.ERROR, "cannot generate 'chmod' Url: "+e.getMessage() );
			}
			return url;
		}
	}
	
	public static class Get{
		public static final String path = "/get/{user}/{password}/{usernameOwner}/**";
		public static String generateUrl(String ip, String port, String username, String password, String usernameOwnerPath, String path){
			String  url = null;
			try{
				url = urlGenerator("get",ip,port,
					new String[]{username,password,usernameOwnerPath,path}); 
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.ERROR, "cannot generate 'get' Url: "+e.getMessage() );
			}
			return url;
		}
					
	}
	
	public static class Mkdir{
		public static final String path = "/mkdir/{username}/{userPass}/{usernameOwner}/**";
		public static String generateUrl(String ip, String port, String username, String password, String usernameOwnerPath, String path){
			String  url = null;
			try{
				url = urlGenerator("mkdir",ip,port,
					new String[]{username,password,usernameOwnerPath,path}); 
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.ERROR, "cannot generate 'mkdir' Url: "+e.getMessage() );
			}
			return url;
		}
					
	}
	
	public static class Remove{
		public static final String path = "/remove/{user}/{password}/{usernameOwner}/**";
		public static String generateUrl(String ip, String port, String username, String password, String usernameOwnerPath, String path){
			String  url = null;
			try{
				url = urlGenerator("remove",ip,port,
					new String[]{username,password,usernameOwnerPath,path}); 
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.ERROR, "cannot generate 'remove' Url: "+e.getMessage() );
			}
			return url;
		}
					
	}
	
	public static class Put{
		public static final String path = "/putAction";
		public static String generateUrl(String ip, String port){
			String  url = null;
			try{
				url = urlGenerator("putAction",ip,port,null);
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.ERROR, "cannot generate 'putAction' Url: "+e.getMessage() );
			}
			return url;
		}
		
		public static MultiValueMap<String,Object> generatePostParameters(String username, String password, String usernameOwner,
																		  String parentPath, String name, FileSystemResource file){
			MultiValueMap<String,Object> postParam = new LinkedMultiValueMap<String,Object>();
			postParam.add("user", 			username);
			postParam.add("password", 		password);
			postParam.add("usernameOwner",  usernameOwner);							
			postParam.add("path", 			parentPath);
			postParam.add("name", 			name);
			postParam.add("file", 			file );
			
			return postParam;
		}
					
	}
	
	public static class Transfer{
		public static final String path = "/transfer";
		public static String generateUrl(String ip, String port){
			String  url = null;
			try{
				url = urlGenerator("transfer",ip,port,null);
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.ERROR, "cannot generate 'transfer' Url: "+e.getMessage() );
			}
			return url;
		}
		
		public static MultiValueMap<String, Object> generatePostParameters(Integer idUser, String newPath,
				FileSystemResource file, long label, String checksum) {
			MultiValueMap<String,Object> postParam = new LinkedMultiValueMap<String,Object>();
			postParam.add("owner", idUser);
			postParam.add("path" , newPath);
			postParam.add("file" , file);
			postParam.add("label" , label);
			postParam.add("checksum" , checksum);
			postParam.add("password", Variables.getServerPassword());
		    
			return postParam;
		}

	
					
	}
		
	public static class DelDir{
		public static final String path = "/deldir/{username}/{userPass}/{usernameOwner}/**";
		public static String generateUrl(String ip, String port, String username, String password, String usernameOwnerPath, String path){
			String  url = null;
			try{
				url = urlGenerator("deldir",ip,port,
					new String[]{username,password,usernameOwnerPath,path}); 
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.ERROR, "cannot generate 'deldir' Url: "+e.getMessage() );
			}
			return url;
		}
					
	}
	
	public static class GetFile{
		public static final String path = "/getFile/{serverPassword}/{idOwner}/{checksum}/**";
		public static String generateUrl(String ip, String port, Integer idOwner, String checksum, String path){
			String  url = null;
			try{
				url = urlGenerator("getFile",ip,port,
					new String[]{String.valueOf(idOwner),checksum,path}); 
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.ERROR, "cannot generate 'getFile' Url: "+e.getMessage() );
			}
			return url;
		}
					
	}
	
	public static class GetFileInfo{
		public static final String path = "/getFileInfo/{user}/{password}/{usernameOwner}/**";
		public static String generateUrl(String ip, String port, String username, String password, String usernameOwnerPath, String path){
			String  url = null;
			try{
				url = urlGenerator("getFileInfo",ip,port,
					new String[]{username,password,usernameOwnerPath,path}); 
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.ERROR, "cannot generate 'getFileInfo' Url: "+e.getMessage() );
			}
			return url;
		}
					
	}
	
	public static class List{
		public static final String path1 = "/list/{user}/{password}/{usernameOwner}";
		public static final String path2 = "/list/{user}/{password}/{usernameOwner}/**";
		public static String generateUrl(String ip, String port, String username, String password, String usernameOwnerPath, String path){
			String  url = null;
			try{
				url = urlGenerator("list",ip,port,
					new String[]{username,password,usernameOwnerPath,path}); 
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.ERROR, "cannot generate 'list' Url: "+e.getMessage() );
			}
			return url;
		}
					
	}
	
	public static class GetPermission{
		public static final String path = "/getPermission/{serverPassword}/{idUser}/{idOwner}/**";
		public static String generateUrl(String ip, String port, Integer idUser, Integer idOwner, String path){
			String  url = null;
			try{
				url = urlGenerator("getPermission",ip,port,
					new String[]{String.valueOf(idUser),String.valueOf(idOwner),path}); 
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.ERROR, "cannot generate 'getPermission' Url: "+e.getMessage() );
			}
			return url;
		}
					
	}
	
	public static class IsPresent{
		public static final String path = "/isPresent/{serverPassword}/{user}/{checksum}/**";
		public static String generateUrl(String ip, String port, Integer idOwner, String checksum, String path){
			String  url = null;
			try{
				url = urlGenerator("isPresent",ip,port,
					new String[]{String.valueOf(idOwner),checksum,path}); 
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.ERROR, "cannot generate 'isPresent' Url: "+e.getMessage() );
			}
			return url;
		}
					
	}
	
	public static class IsManaged{
		public static final String path = "/isManaged/{serverPassword}/{user}/{checksum}/**";
		public static String generateUrl(String ip, String port, Integer idOwner, String checksum, String logicalPath){
			String  url = null;
			try{
				url = urlGenerator("isManaged",ip,port,
					new String[]{String.valueOf(idOwner),checksum,logicalPath}); 
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.ERROR, "cannot generate 'isManaged' Url: "+e.getMessage() );
			}
			return url;
		}
					
	}
	
	public static class NotifyDeleteFile{
		public static final String path = "/notifyDeleteFile/{serverPassword}/{idOwner}/{isDirectory}/**";
		public static String generateUrl(String ip, String port, Integer idOwner, Boolean isDir, String path){
			String  url = null;
			try{
				url = urlGenerator("notifyDeleteFile",ip,port,
					new String[]{String.valueOf(idOwner),String.valueOf(isDir),path}); 
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.ERROR, "cannot generate 'notifyDeleteFile' Url: "+e.getMessage() );
			}
			return url;
		}
					
	}
	
	public static class NotifyPutFile{
		public static final String path = "/notifyPutFile/{serverPassword}/{idOwner}/{size}/{dateTime}/{isDirectory}/{idParentDir}/**";
		public static String generateUrl(String ip, String port, Integer idOwner, String size, String dateTime, Boolean isDir, String parentId, String path){
			String  url = null;
			try{
				url = urlGenerator("notifyPutFile",ip,port,
						new String[]{String.valueOf(idOwner),size,dateTime,String.valueOf(isDir),parentId,path});
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.ERROR, "cannot generate 'notifyPutFile' Url: "+e.getMessage() );
			}
			return url;
		}
					
	}
	
	public static class ExistsDir{
		public static final String path = "/existsDir/{serverPassword}/{user}/{uniqueId}/**";
		public static String generateUrl(String ip, String port, Integer idOwner, String uniqueId, String path){
			String  url = null;
			try{
				url = urlGenerator("existsDir",ip,port,
					new String[]{String.valueOf(idOwner),uniqueId,path}); 
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.ERROR, "cannot generate 'existsDir' Url: "+e.getMessage() );
			}
			return url;
		}
					
	}

	public static class ExistsFile{
		public static final String path = "/existsFile/{serverPassword}/{user}/{uniqueId}/**";
		public static String generateUrl(String ip, String port, Integer idOwner, String uniqueId, String path){
			String  url = null;
			try{
				url = urlGenerator("existsFile",ip,port,
					new String[]{String.valueOf(idOwner),uniqueId,path}); 
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.ERROR, "cannot generate 'existsFile' Url: "+e.getMessage() );
			}
			return url;
		}
					
	}
	
	public static class GetDirUniqueId{
		public static final String path = "/getDirUniqueId/{serverPassword}/{user}/**";
		public static String generateUrl(String ip, String port, Integer idOwner, String path){
			String  url = null;
			try{
				url = urlGenerator("getDirUniqueId",ip,port,
					new String[]{String.valueOf(idOwner),path}); 
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.ERROR, "cannot generate 'getDirUniqueId' Url: "+e.getMessage() );
			}
			return url;
		}
					
	}
	
	public static class GetMetaInfo{
		public static final String path = "/getMetaInfo/{password}/{labelStart}/{labelEnd}";
		public static String generateUrl(String ip, String port, Long labelStart, Long labelEnd){
			String  url = null;
			try{
				url = urlGenerator("getMetaInfo",ip,port,
					new String[]{Long.toUnsignedString(labelStart),Long.toUnsignedString(labelEnd)}); 
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.ERROR, "cannot generate 'getMetaInfo' Url: "+e.getMessage() );
			}
			return url;
		}
					
	}
	
	public static class GetDirectoryListing{
		public static final String path = "/getDirectoryListing/{password}/{labelStart}/{labelEnd}";
		public static String generateUrl(String ip, String port, Long labelStart, Long labelEnd){
			String  url = null;
			try{
				url = urlGenerator("getDirectoryListing",ip,port,
					new String[]{Long.toUnsignedString(labelStart),Long.toUnsignedString(labelEnd)}); 
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.ERROR, "cannot generate 'getDirectoryListing' Url: "+e.getMessage() );
			}
			return url;
		}
					
	}
	
	public static class GetLowerTree{
		public static final String path = "/getLowerTree/{password}/{labelStart}/{labelEnd}";
		public static String generateUrl(String ip, String port, Long minValue, Long maxValue){
			String  url = null;
			try{
				url = urlGenerator("getLowerTree",ip,port,
					new String[]{Long.toUnsignedString(minValue),Long.toUnsignedString(maxValue)}); 
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.ERROR, "cannot generate 'getLowerTree' Url: "+e.getMessage() );
			}
			return url;
		}
					
	}
	
	public static class GetUpperTree{
		public static final String path = "/getUpperTree/{password}";
		public static String generateUrl(String ip, String port){
			String  url = null;
			try{
				url = urlGenerator("getUpperTree",ip,port,null); 
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.ERROR, "cannot generate 'getUpperTree' Url: "+e.getMessage() );
			}
			return url;
		}
					
	}
	
	public static class GlobalSynchRequest{
		public static final String path = "/globalSynchRequest/{password}/{idCallerServer}";
		public static String generateUrl(String ip, String port, Long serverId){
			String  url = null;
			try{
				url = urlGenerator("globalSynchRequest",ip,port,new String[]{Long.toUnsignedString(serverId)}); 
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.ERROR, "cannot generate 'globalSynchRequest' Url: "+e.getMessage() );
			}
			return url;
		}
					
	}
	
	public static class GroupSynchRequest{
		public static final String path = "/groupSynchRequest/{password}/{idCallerServer}/{serverIdList}";
		public static String generateUrl(String ip, String port, Long serverId, java.util.List<Server> serverList){
			String  url = null;

			String listServerId = (serverList!=null)?createListParameter(serverList):null;
			try{
				url = urlGenerator("groupSynchRequest",ip,port,new String[]{Long.toUnsignedString(serverId),listServerId}); 
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.ERROR, "cannot generate 'groupSynchRequest' Url: "+e.getMessage() );
			}
			return url;
		}
					
	}
	
	public static class Ping{
		public static final String path = "/ping/{password}/{idConsRun}/{idServer}/{sourceServerPort}";
		public static String generateUrl(String ip, String port, Long idGlobalConsRun, String serverId, String serverPort){
			String  url = null;
			try{
				url = urlGenerator("ping",ip,port,new String[]{
						Long.toUnsignedString(idGlobalConsRun),
						serverId,
						Variables.getServerPort()
						});
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.ERROR, "cannot generate 'ping' Url: "+e.getMessage() );
			}
			return url;
		}
					
	}
	
	public static class PingExtraInfos{
		public static final String path = "/pingExtraInfos/{password}/{idConsRun}/{idServer}/{sourceServerPort}/{aviableSpace}/{totalSpace}/{status}";
		public static String generateUrl(String ip, String port, Long idGlobalConsRun, String serverId, String serverPort, 
										 String availableSpace,String totalSpace, ServerStatus serverStatus){
			String  url = null;
			try{
				url = urlGenerator("pingExtraInfos",ip,port,new String[]{
						Long.toUnsignedString(idGlobalConsRun),
						serverId,
						Variables.getServerPort(),
						availableSpace,
						totalSpace,
						serverStatus.toString()
						});
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.ERROR, "cannot generate 'pingExtraInfos' Url: "+e.getMessage() );
			}
			return url;
		}
					
	}
	
	public static class PingGroup{
		public static final String path = "/pingGroup/{password}/{idConsRun}/{idServer}/{sourceServerPort}/{serverIdList}";
		public static String generateUrl(String ip, String port, Long idConsRun, String serverId, String serverPort, java.util.List<Server> serverIdList){
			String  url = null;
			String listServerId = (serverIdList!=null)?createListParameter(serverIdList):null;
			try{
				url = urlGenerator("pingGroup",ip,port,new String[]{
						Long.toUnsignedString(idConsRun),
						serverId,
						Variables.getServerPort(),
						listServerId
						});
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.ERROR, "cannot generate 'pingGroup' Url: "+e.getMessage() );
			}
			return url;
		}
					
	}
	
	public static class AddServer{
		public static final String path = "/addServer/{password}/{idServer}/{ipList}/{port}";
		public static String generateUrl(String ip, String port, String myId, String myPort, String myIpList){
			String  url = null;
			try{
				url = urlGenerator("addServer",ip,port,new String[]{
						myId,
						myIpList,
						myPort
				});
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.ERROR, "cannot generate 'addServer' Url: "+e.getMessage() );
			}
			return url;
		}
					
	}
	
	
	
	
	
	
	public static class Prepare {
		public static final String path = "/prepare/{password}/{op}/{opType}/{proposalNumber}/{idConsensusRun}";
		public static String generateUrl(String ip, String port, 
										 JobOperation operation, 
										 ProposalNumber proposalNumber,
										 Long   idConsRun){
			String url = null;
			try{
				 url = urlGenerator("prepare", ip, port, 
						 new String[]{
									URLEncoder.encode(operation.toJSON(), "UTF-8"),
									URLEncoder.encode(operation.getClass().getName().toString(), "UTF-8"),
									URLEncoder.encode(proposalNumber.toJSON(), "UTF-8"),
									URLEncoder.encode(String.valueOf(idConsRun), "UTF-8")
							}
						);
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.ERROR, "cannot generate 'prepare' Url: "+e.getMessage() );
			}
			return url;
		}
	}
	
	public static class Propose {
		public static final String path = "/propose/{password}/{op}/{opType}/{proposalNumber}/{idConsensusRun}";
		public static String generateUrl(String ip, String port, 
										 JobOperation operation, 
										 ProposalNumber proposalNumber,
										 Long   idConsRun){
			String url = null;
			try{
				 url = urlGenerator("propose", ip, port, 
						 new String[]{
									URLEncoder.encode(operation.toJSON(), "UTF-8"),
									URLEncoder.encode(operation.getClass().getName().toString(), "UTF-8"),
									URLEncoder.encode(proposalNumber.toJSON(), "UTF-8"),
									URLEncoder.encode(String.valueOf(idConsRun), "UTF-8")
							}
						);
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.ERROR, "cannot generate 'propose' Url: "+e.getMessage() );
			}
			return url;
		}
	}
	
	public static class Accept {
		public static final String path = "/accept/{password}/{op}/{opType}/{proposalNumber}/{idConsensusRun}";
		public static String generateUrl(String ip, String port, 
										 JobOperation operation, 
										 ProposalNumber proposalNumber,
										 Long   idConsRun){
			String url = null;
			try{
				 url = urlGenerator("accept", ip, port, 
						 new String[]{
									URLEncoder.encode(operation.toJSON(), "UTF-8"),
									URLEncoder.encode(operation.getClass().getName().toString(), "UTF-8"),
									URLEncoder.encode(proposalNumber.toJSON(), "UTF-8"),
									URLEncoder.encode(String.valueOf(idConsRun), "UTF-8")
							}
						);
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.ERROR, "cannot generate 'accept' Url: "+e.getMessage() );
			}
			return url;
		}
	}

	public static class GoToMaintenance{
		public static final String path = "/goToMaintenance/{password}";
		public static String generateUrl(String ip, String port){
			String url = null;
			try{
				 url = urlGenerator("goToMaintenance", ip, port, null);
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.ERROR, "cannot generate 'goToMaintenance' Url: "+e.getMessage() );
			}
			return url;
		}
	}
	
	public static class ExitMaintenance{
		public static final String path = "/exitMaintenance/{password}";
		public static String generateUrl(String ip, String port){
			String url = null;
			try{
				 url = urlGenerator("exitMaintenance", ip, port, null);
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.ERROR, "cannot generate 'exitMaintenance' Url: "+e.getMessage() );
			}
			return url;
		}
	}


	public static class UserList{
		public static final String path = "/userList/{password}";
		public static String generateUrl(String ip, String port){
			String  url = null;
			try{
				url = urlGenerator("userList",ip,port,null);
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.ERROR, "cannot generate 'userList' Url: "+e.getMessage() );
			}
			return url;
		}

	}


	public static class GetSharedFile{
		public static final String path = "/getSharedFile/{username}/{password}";
		public static String generateUrl(String ip, String port, String username, String password){
			String  url = null;
			try{
				url = urlGenerator("getSharedFile",ip,port,new String[]{username, password});
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.ERROR, "cannot generate 'userList' Url: "+e.getMessage() );
			}
			return url;
		}

	}

	public static class GetListSharedWith{
		public static final String path = "/getListSharedWith/{serverPassword}/{username}";
		public static String generateUrl(String ip, String port, String username){
			String  url = null;
			try{
				url = urlGenerator("getListSharedWith",ip,port,new String[]{username});
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.ERROR, "cannot generate 'userList' Url: "+e.getMessage() );
			}
			return url;
		}

	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	/**
	 * The method generate the url for the rest function needed
	 * Examples:
	 * 		<protocol>://<serverIp>:<serverPort>/<param1>/<param2>/.../<password>
	 * 		<protocol>://<serverIp>:<serverPort>/<param1>/<param2>/.../
	 * 		<protocol>://<serverIp>:<serverPort>/
	 * @param type			the name of the rest function to call
	 * @param serverIp		String
	 * @param serverPort	String
	 * @param param			String array of the parameters for the rest function call NO PASSWORD
	 *                      respect the order in witch the array is made
	 * @return String	url
	 *         null 	otherwise
	 */
	private static String urlGenerator(
			String type,
			String serverIp,
			String serverPort,
			String [] param
	){
		StringBuilder retStr = new StringBuilder();
		String params = "", urlInit = "";

		//create the init url <protocol>://<serverIp>:<serverPort>
		urlInit = initUrlGenerator(serverIp,serverPort);
		if(urlInit == null) {
			PadFsLogger.log(LogLevel.ERROR,"Server IP or Server port not correct check it: (IP: "+serverIp+","+serverPort+")");
			return null;
		}

		//add rest function name and it's parameters
		params = concatenateParameterToUrl(type, param);
		if (params == null) {
			PadFsLogger.log(LogLevel.ERROR, "PARAMETERS MISMATCH");
			return null;
		}

		//made the url string
		retStr.append(urlInit);
		retStr.append(params);

		
		
		return normalizeUrl(retStr.toString());
	}
	
	/**
	 * Url generator generate a custom string url
	 * @param serverIp   String server ip
	 * @param serverPort Strign server port
     * @return String it argument are correct
	 * 		   null otherwise
     */
	private static String initUrlGenerator(String serverIp, String serverPort){
		if(	serverIp == null || serverPort == null ||
			serverIp.equals("") || serverPort.equals("") ) {
			return null;
		}
		return Variables.getProtocol()+"://"+serverIp+":"+serverPort+"/";
	}

	private static String concatenateParameterToUrl(String type, String [] parameters){
		StringBuilder s = new StringBuilder();

		//ADD THE NAME OF THE METHOD
		if(type!=null)
			s.append(type+"/");
		else
			return  null;

	
		/* add the server password if needed */
		s.append(concatenatePasswordToUrl(type));
		
		//ADD THE PARAMETERS
		if(parameters!=null) {
			for (String p : parameters) {
				s.append(p+"/");
			}
		}
		
		return SystemEnvironment.removeEndingSlashes(s.toString())+"/";
	}
	
	/**
	 * Choose witch password send with the type of request
	 * @param type the type of the request
	 * @return	String with the password to send
	 * 			null otherwise
     */
	private static String concatenatePasswordToUrl(String type){
		String ret = null;
		switch (type){
			case "getUpperTree":
			case "getLowerTree":
			case "globalSynchRequest":
			case "groupSynchRequest":
			case "getMetaInfo":
			case "pingExtraInfos":
			case "ping":
			case "pingGroup":
			case "prepare":
			case "propose":
			case "accept":
			case "addServer":
			case "getFile":
			case "isPresent":
			case "existsDir":
			case "existsFile":
			case "isManaged":
			case "getDirUniqueId":
			case "notifyPutFile":
			case "notifyDeleteFile":
			case "getPermission":
			case "getDirectoryListing":
			case "goToMaintenance":
			case "getListSharedWith":
				ret = Variables.getServerPassword()+"/";
				break;
			default:
				ret = "";
		}
		return ret;
	}

	private static String normalizeUrl(String s) {
		if(s == null)
			return null;
		
		final String protocolSeparator = "://";
		
		int ind = s.indexOf(protocolSeparator);
		if(ind >= 0){
			String ret = s.substring(0, ind) + 
						protocolSeparator + 
						 s.substring(ind + protocolSeparator.length()).replaceAll("/+", "/");
			return ret;
		}
		else{
			PadFsLogger.log(LogLevel.ERROR, "Wrong URL generated");
			return null;
		}
		
	}


		private static String createListParameter(java.util.List<Server> list){
			if(list == null || list.size()==0){
				return null;
			}
			StringBuilder str = new StringBuilder();
			for (int i=0;i<list.size();i++){
				str.append(Long.toUnsignedString(list.get(i).getId()));
				if((i+1)<list.size())
					str.append(",");
			}
			return str.toString();
		}
}
