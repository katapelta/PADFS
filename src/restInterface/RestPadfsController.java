package restInterface;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ErrorAttributes;
import org.springframework.boot.autoconfigure.web.ErrorController;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import com.fasterxml.jackson.databind.ObjectMapper;

import jobManagement.consensus.Accept;
import jobManagement.consensus.JobConsMsg;
import jobManagement.consensus.Prepare;
import jobManagement.consensus.Propose;
import jobManagement.jobOperation.JobOperation;
import jobManagement.jobOperation.clientOp.Chmod;
import jobManagement.jobOperation.clientOp.Deldir;
import jobManagement.jobOperation.clientOp.Get;
import jobManagement.jobOperation.clientOp.GetFileInfo;
import jobManagement.jobOperation.clientOp.JobClientOp;
import jobManagement.jobOperation.clientOp.Mkdir;
import jobManagement.jobOperation.clientOp.Put;
import jobManagement.jobOperation.clientOp.Remove;
import jobManagement.jobOperation.serverOp.AddServer;
import jobManagement.jobOperation.serverOp.AddUser;
import jobManagement.jobOperation.serverOp.DelUser;
import jobManagement.jobOperation.serverOp.ExitMaintenance;
import jobManagement.jobOperation.serverOp.MaintenanceRequested;
import restInterface.consensus.ProposalNumber;
import restInterface.consensus.RestAcceptResponse;
import restInterface.consensus.RestProposeResponse;
import restInterface.consensus.RestReply;
import restInterface.manageOp.RestAddServerResponse;
import restInterface.manageOp.RestAddUserResponse;
import restInterface.manageOp.RestDelUserResponse;
import restInterface.manageOp.RestGetDirectoryListing;
import restInterface.manageOp.RestGetLowerTreeResponse;
import restInterface.manageOp.RestGetMetaInfo;
import restInterface.manageOp.RestGetPermission;
import restInterface.manageOp.RestGetUpperTreeResponse;
import restInterface.manageOp.RestGetUsers;
import restInterface.manageOp.RestGlobalSynchResponse;
import restInterface.manageOp.RestGroupSynchResponse;
import restInterface.manageOp.RestIsDirManaged;
import restInterface.manageOp.RestIsManaged;
import restInterface.manageOp.RestIsPresent;
import restInterface.manageOp.RestNotifyDeleteFileResponse;
import restInterface.manageOp.RestNotifyPutFileResponse;
import restInterface.manageOp.RestPong;
import restInterface.manageOp.RestPongExtraInfo;
import restInterface.manageOp.RestTransfer;
import restInterface.op.*;
import system.SystemEnvironment;
import system.SystemEnvironment.Constants;
import system.SystemEnvironment.Constants.Permission;
import system.SystemEnvironment.Constants.Rest;
import system.SystemEnvironment.Constants.ServerStatus;
import system.SystemEnvironment.Variables;
import system.consensus.ConsensusServerGroup;
import system.containers.DirectoryListingItem;
import system.containers.MetaInfo;
import system.containers.Server;
import system.containers.User;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;
import system.managementOp.Transfer;
import system.managers.LocalFsManager;
import system.managers.SqlManager;
 
@RestController
public class RestPadfsController {
	private static BlockingQueue<JobConsMsg<?>> inCons;
	private static PriorityBlockingQueue<JobOperation> inOp;
	private static boolean init=false;


	/**
	 * This function check if the remote server sent the correct password
	 * @param sentPassword String with the sent password
	 * @return true if corresponds
	 * @return false otherwise
	 */
	private static boolean checkServerPassword(String sentPassword) {
		return sentPassword != null &&
				!sentPassword.equals("") &&
				Variables.getServerPassword().equals(sentPassword);
	}

	/**
	 * This function check if the super user sent the correct password
	 * @param sentPassword String with the sent password
	 * @return true if corresponds
	 * @return false otherwise
	 */
	private static boolean checkPanelPassword(String sentPassword) {
		return sentPassword != null &&
				!sentPassword.equals("") &&
				Variables.getPanelPassword().equals(sentPassword);
	}


	public static void init(BlockingQueue<JobConsMsg<?>> inConsMsg, PriorityBlockingQueue<JobOperation> inOp){
		if(init==false){
			RestPadfsController.inCons = inConsMsg;
			RestPadfsController.inOp = inOp;
			//sql = Padfs.getDB();

			init=true;
		}
	}

	/**
	 * Create the webpage header
	 * @return String with the html header
	 */
	private static String headerHtml(String title){
		StringBuilder s = new StringBuilder();
		s.append("<!DOCTYPE html>\n" +
					"<html>\n" +
						"<meta name=\"viewport\" content=\"width=1000, initial-scale=1\">\n" +
						"<head>\n" +
							"<link rel=\"stylesheet\" href=\"web/bootstrap/css/bootstrap.min.css\" >\n" +
							"<link rel=\"stylesheet\" href=\"web/bootstrap/css/bootstrap-theme.min.css\">\n" +
							"<link rel=\"stylesheet\" type=\"text/css\" href=\"web/css/progressBar.css\">\n" +
							"<link rel=\"stylesheet\" type=\"text/css\" href=\"web/css/fileTree.css\">\n" +
							"<link rel=\"stylesheet\" type=\"text/css\" href=\"web/css/style.css\">\n" +
							"<script type=\"text/javascript\" src=\"web/js/jquery.js\"></script>\n" +
							"<script src=\"web/bootstrap/js/bootstrap.min.js\" ></script>\n" +
							"<script type=\"text/javascript\" src=\"web/js/main.js\"></script>\n" +
							"<script type=\"text/javascript\" src=\"web/js/fsTree.js\"></script>\n" +
							"<title>"+title+"</title>\n" +
						"</head>\n" +
					"<body>\n");
		return s.toString();
	}

	/**
	 * Create a string with the html footer
	 * @return String with the html footer
	 */
	private static String footerHtml(){
		StringBuilder s = new StringBuilder();
		s.append("</body>\n</html>");
		return s.toString();
	}

	/**
	 * Expect ArrayList with an HashMap for each row
	 * @param toPrint ArrayList a list of db table rows
	 * @return Html table
	 * @return null otherwise
	 */
	private static String createHtmlTableString(ArrayList<?> toPrint){
		StringBuilder s = new StringBuilder();
		//String key, value;
		Object[] arr = null;

		//s.append(headerHtml());

		s.append("<table border='1' class='htmlTable'>");
		for(int i = 0; i<toPrint.size(); i++){
			HashMap<?,?> ret = (HashMap<?,?>) toPrint.get(i);

			//print head of the table
			if(i==0){//todo only for the first line
				arr = ret.keySet().toArray();
				if(arr!=null && arr.length>0) {
					s.append("<tr>\n");
					for (int j = 0; j < arr.length; j++) {
						s.append("<th>" + arr[j] + "</th>\n");
					}
					s.append("</tr>\n");
				}
			}

			//print the row of the table
			s.append("<tr>\n");
			for(int j = 0; j<ret.size(); j++) {
				s.append("<td align='left'>"+String.valueOf(ret.get(arr[j]))+"</td>\n");
			}
			s.append("</tr>\n");

		}

		s.append("</table>");
		//s.append(footerHtml());
		return s.toString();
	}

	private static String createHtmlTableString(HashMap<String, String> toPrint){
		StringBuilder s = new StringBuilder();
		//s.append(headerHtml());
		s.append("<table border='1' class='htmlTable'>");
		String key, value;
		for (Map.Entry<String, String> row : toPrint.entrySet()) {
			key = row.getKey();
			value = row.getValue();
			s.append("<tr>");
			s.append("<th align='left'>"+key+"</th>" +
					"<td align='left'>"+value+"</td>");
			s.append("</tr>");
		}
		s.append("</table>");
		//s.append(footerHtml());
		return s.toString();
	}

	private static String createHtmlTableStringMessage(String msg, HashMap<String, Object> toPrint){
		StringBuilder s = new StringBuilder();
		s.append(headerHtml("ERROR PAGE"));
		s.append("<center>"+msg+"</center>");
		s.append("<table border='1' class='htmlTable'>");
		String key, value;
		for (Map.Entry<String, Object> row : toPrint.entrySet()) {
			key = row.getKey();
			value = String.valueOf(row.getValue());
			s.append("<tr>");
			s.append("<th align='left'>"+key+"</th>" +
					"<td align='left'>"+value+"</td>");
			s.append("</tr>");
		}
		s.append("</table>");
		s.append(footerHtml());
		return s.toString();
	}


	/**
	 * Convert a list of server to an arrayList of hashmap of servers
	 * @param serverList a list<server>
	 * @return ArrayList<HashMap<column,value>>
	 */
	private synchronized static ArrayList<?> serverListToArrayList(List<Server> serverList){
		ArrayList<HashMap<String,String>> ret 			 = null;
		String[] column = new String[]{
				"id",
				"ip",
				"port",
				"status",
				"group id",
				"label",
				"available space",
				"total space",
				"keep alive",
				"keep alive time"
		};
		int num 	= 0;

		try {
			Iterator<Server> r = serverList.iterator();
			ret 	= new ArrayList<>(num+1);

			while (r.hasNext()){
				Server s = (Server)r.next();
				HashMap<String,String> row = null;
				if(column.length>0) {
					row = new HashMap<String,String>(column.length);
					row.put(column[0], String.valueOf(s.getId()));
					row.put(column[1], s.getIp());
					row.put(column[2], s.getPort());
					row.put(column[3], String.valueOf(s.getStatus()));
					row.put(column[4], String.valueOf(s.getGroupId()));
					row.put(column[5], String.valueOf(s.getLabel()));
					row.put(column[6], s.getAvailableSpace());
					row.put(column[7], s.getTotalSpace());
					row.put(column[8], s.getKeepAlive());
					row.put(column[9], s.getKeepAliveTime());
					ret.add(row);
				}
			}
		}catch (Exception e){
			return null;
		}

		return ret;
	}

	/***************************************************************************************
	 *  TOOLS FUNCTIONS
	 ***************************************************************************************/

	@RequestMapping(
			value={
					"/systemVariables/{password}/",
					"/systemVariables/{password}"
			},
			method = RequestMethod.GET,
			produces = MediaType.TEXT_HTML_VALUE
	)
	public static String systemVariables(@PathVariable("password") String password,
										 HttpServletRequest request) throws Exception {

		if(!checkPanelPassword(password)){ return generateErrorPage(Constants.Rest.errors.wrongPanelPassword);	}

		final Class<Variables> o = SystemEnvironment.Variables.class;
		String methodName, varName, methodValue=null;
		HashMap<String, String> constantVar = new HashMap<>();
		List<String> classNotToPrint = Arrays.asList("getClass");

		for (Method m : o.getMethods()) {
			if (	m.getName().startsWith("get") &&
					m.getParameterTypes().length == 0 &&
					!classNotToPrint.contains(m.getName())
					) {
				methodName 	= m.getName();
				varName 	= methodName.substring(3,methodName.length());
				methodValue = String.valueOf(m.invoke(o));
				switch (methodName){
					case "getServerList":
						methodValue = methodValue.replace(",","").replace(";"," ").replace("]","").replace("[","").replace("\n","\n<br>");
						break;
					case "getMerkleTree":
						methodValue = methodValue.replace("\n","\n&emsp;&emsp;\n").replace("\n","\n<br>");
						break;
					default:
				}
				constantVar.put(varName,methodValue);
			}
		}
		String s = createHtmlTableString(constantVar);

		return s;
	}

	/**
	 * Return the server list in the server in VARIABLES
	 * @return List<Server>
	 * @return error message otherwise
	 */
	@RequestMapping(
			value={
					"/variablesServerList/{password}/",
					"/variablesServerList/{password}"
			},
			method = RequestMethod.GET,
			produces = MediaType.TEXT_HTML_VALUE
	)
	public static String readVariablesServerList(@PathVariable String password,
												 HttpServletRequest request) {

		if(!checkPanelPassword(password)){ return generateErrorPage(Constants.Rest.errors.wrongPanelPassword);	}


		List<Server> servers = Variables.getServerList();
		StringBuilder s = new StringBuilder();
		//s.append(headerHtml());
		if(servers!=null){
			s.append("Variables SERVER LIST of: ("+Variables.getServerId()+") "+Variables.getConfigServerIP()+":"+Variables.getServerPort());
			s.append("<br><br>\n\n");
			for(int i=0; i<servers.size(); i++){
				s.append(servers.get(i).toString());
				s.append("<br>\n");
			}
			//s.append(footerHtml());
			return s.toString();
		}else{
			return "SERVER LIST EMPTY";
		}
	}

	/**
	 * Return the server list in the server in DB
	 * @return List<Server>
	 * @return error message otherwise
	 */
	@RequestMapping(
			value={
					"/dbTable/{tableName}/{password}/",
					"/dbTable/{tableName}/{password}"
			},
			method = RequestMethod.GET,
			produces = MediaType.TEXT_HTML_VALUE
	)
	public static String printDbTable(@PathVariable String tableName,
									  @PathVariable String password,
									  HttpServletRequest request) {
		if(!checkPanelPassword(password)){ return generateErrorPage(Constants.Rest.errors.wrongPanelPassword);	}

		if(tableName == null || tableName.compareTo("") == 0){
			return "redirect:/"+BAD_REQUEST;
		}
		ArrayList<?> rowResults = null;
		try {
			rowResults = SqlManager.selectTable(tableName);
		}catch (Exception e){
			return "<center><h2>ERROR RETRIEVE DATA</h2></center>"+footerHtml();
		}
		if(rowResults!=null)
			return createHtmlTableString(rowResults);
		else
			return"<center><h2>TABLE EMPTY</h2></center>"+footerHtml();
	}

	/**
	 * Return the server list in the Variables
	 * @return List<Server>
	 * @return error message otherwise
	 */
	@RequestMapping(
			value={
					"/varServerList/{password}/",
					"/varServerList/{password}"
			},
			method = RequestMethod.GET,
			produces = MediaType.TEXT_HTML_VALUE
	)
	public static String readVarServerList(
			@PathVariable String password,
			HttpServletRequest request) {
		if(!checkPanelPassword(password)){ return generateErrorPage(Constants.Rest.errors.wrongPanelPassword);	}

		List<Server> servers = Variables.getServerList();
		ArrayList<?> rowResults = serverListToArrayList(servers);
		if(rowResults!=null)
			return createHtmlTableString(rowResults);
		else
			return "<center><h2>EMPTY</h2></center>"+footerHtml();
	}

	/***************************************************************************************
	 *  ERROR MANAGER
	 ***************************************************************************************/
	private static String generateErrorPage(Rest.errors msg){
		StringBuilder s = new StringBuilder();
		s.append(headerHtml("ERROR PAGE"));
		s.append("<error><p class='errorMessage'>ERROR MESSAGE: "+msg+"</p></error>");
		s.append(footerHtml());
		return s.toString();
	}


	@RestController
	@RequestMapping("/error")
	public class SimpleErrorController implements ErrorController {

		private final ErrorAttributes errorAttributes;

		@Autowired
		public SimpleErrorController(ErrorAttributes errorAttributes) {
			Assert.notNull(errorAttributes, "ErrorAttributes must not be null");
			this.errorAttributes = errorAttributes;
		}



		@Override
		public String getErrorPath() {
			return "/error";
		}

		/**
		 timestamp - The time that the errors were extracted
		 status - The status code
		 error - The error reason
		 exception - The class name of the root exception
		 message - The exception message
		 errors - Any ObjectErrors from a BindingResult exception
		 trace - The exception stack trace
		 path - The URL path when the exception was raised
		 */
		public  Map<String, Object> generatePersonalError(
				String errorStatus,
				String errorTitle,
				String errorMessage,
				String errorException){
			Map<String, Object> ret = new HashMap<>();
			Date date = new Date();
			ret.put("timestamp", new Timestamp(date.getTime()));
			ret.put("status",	errorStatus);
			ret.put("error",	errorTitle);
			ret.put("exception",errorException);
			ret.put("message",	errorMessage);
			ret.put("errors",	"errors");
			ret.put("trace",	"trace");
			ret.put("path",		"/error");

			return ret;
		}

		public Map<String, Object> error2(HttpServletRequest aRequest){
			Map<String, Object> body = getErrorAttributes(aRequest,	getTraceParameter(aRequest));
			return body;
		}

		public String errorPersonal(Map<String, Object> errorMap){
			HashMap<String, Object> copy = new HashMap<String, Object>(errorMap);
			return createHtmlTableStringMessage("<h2>ERROR PAGE</h2>",copy);

		}

		@RequestMapping
		public String error(HttpServletRequest aRequest){
			Map<String, Object> map = error2(aRequest);
			HashMap<String, Object> copy = new HashMap<String, Object>(map);
			return createHtmlTableStringMessage("<h2>ERROR PAGE</h2>",copy);

		}
		private boolean getTraceParameter(HttpServletRequest request) {
			String parameter = request.getParameter("trace");
			if (parameter == null) {
				return false;
			}
			return !"false".equals(parameter.toLowerCase());
		}


		/**
		 timestamp - The time that the errors were extracted
		 status - The status code
		 error - The error reason
		 exception - The class name of the root exception
		 message - The exception message
		 errors - Any ObjectErrors from a BindingResult exception
		 trace - The exception stack trace
		 path - The URL path when the exception was raised
		 */
		private Map<String, Object> getErrorAttributes(HttpServletRequest aRequest, boolean includeStackTrace) {
			RequestAttributes requestAttributes = new ServletRequestAttributes(aRequest);
			return errorAttributes.getErrorAttributes(requestAttributes, includeStackTrace);
		}
	}

	
	
		
	
/************************************************************************************************
 *  -- SERVER OPERATIONS -
 ************************************************************************************************/

	
	@RequestMapping(value={
			RestInterface.Ping.path,
			RestInterface.Ping.path+"/"	
	}, method = RequestMethod.GET)
	public static RestPong ping(
			@PathVariable String password,
			@PathVariable String idConsRun,
			@PathVariable String idServer,
			@PathVariable String sourceServerPort,
			HttpServletRequest request) {

		if(!checkServerPassword(password)){
			PadFsLogger.log(LogLevel.ERROR,Rest.errors.wrongServerPassword.toString());
			return new RestPong(Rest.status.error, null, Rest.errors.wrongServerPassword);
		}

		String ipServer = request.getRemoteAddr();
		if(Constants.localhostAddresses.contains(ipServer)){
			ipServer = Constants.localhost;
		}

		long idGlobalConsRun = Variables.consensusVariableManager.getConsVariables(Constants.globalConsensusGroupId).getIdConsRun();

		PadFsLogger.log(LogLevel.DEBUG, " =>> PING RECEIVED - FROM: ["+idServer+"] => "+ipServer+":"+sourceServerPort+" - idConsRun: "+idConsRun + "  myIdGlobalConsRun: "+idGlobalConsRun);

		Long idServerL = Long.parseUnsignedLong(idServer);
		if(idServerL > 0 && Variables.getIAmInTheNet()){
			SqlManager.setUPServer(idServerL);
		}

		return new RestPong(Rest.status.ok, idGlobalConsRun, null);
	}
	
	
	


	@RequestMapping(value={
			RestInterface.GetPermission.path,
			RestInterface.GetPermission.path+"/"
	}, method = RequestMethod.GET)
	public static RestGetPermission getPermission(
			@PathVariable String serverPassword,
			@PathVariable Integer idUser,
			@PathVariable Integer idOwner,
		    HttpServletRequest request) {


		String path = getPathFromRequest(request,4);

		/* check server password */
		if(!checkServerPassword(serverPassword)){
			PadFsLogger.log(LogLevel.ERROR,Rest.errors.wrongServerPassword.toString());
			return new RestGetPermission(Rest.errors.wrongServerPassword, null,null, null);
		}

		/* check that the permission is really managed by this server */
		Long label = SystemEnvironment.getLabel(idOwner,path);
		if(Variables.getLabelStart() > label || Variables.getLabelEnd() < label){
			return new RestGetPermission(Rest.errors.labelNotManaged, null,null, null);
		}
		
		
		
		/* get ConsensusGroup */
		long[] serverIds = SqlManager.getIdFromConsensusLabel(label);
		ConsensusServerGroup group = new ConsensusServerGroup(serverIds);	
		
		/* get idConsRun (group and global) */
		Long globalConsRunId;
		Long groupConsRunId;
		
		globalConsRunId = Variables.consensusVariableManager.getConsVariables(Constants.globalConsensusGroupId).getIdConsRun();
		groupConsRunId  = Variables.consensusVariableManager.getConsVariables(group.getConsensusGroupId()).getIdConsRun();
		
		/* get permission */
		Permission perm = null;
		Rest.errors error = null;
		
		{	
			/* get the permission for this path */
			Integer idFile = SqlManager.getIdFile(path, idOwner);
			if(idFile != null && idUser == idOwner){
				perm = Permission.fullAccess;
			}
			else{
				perm = SqlManager.getPermission(idUser, idFile);
				if(perm == null){
					error = Rest.errors.internalError; 
				}
				else{
					if(perm == Permission.unset){
						perm = SystemEnvironment.getParentPermission(idUser, idOwner, path);
						if(perm == null){
							error = Rest.errors.internalError; 
						}
					}
				}
			}
		
		}
		return new RestGetPermission(error,perm,globalConsRunId,groupConsRunId);
	}

	@RequestMapping(value={
			RestInterface.PingGroup.path,
			RestInterface.PingGroup.path+"/"
	}, method = RequestMethod.GET)
	public static RestPong pingGroup(
			@PathVariable String idConsRun,
			@PathVariable String idServer,
			@PathVariable String sourceServerPort,
			@PathVariable String serverIdList,
			@PathVariable String password,
			HttpServletRequest request) {

		if(!checkServerPassword(password)){
			PadFsLogger.log(LogLevel.ERROR,Rest.errors.wrongServerPassword.toString());
			return new RestPong(Rest.status.error, null, Rest.errors.wrongServerPassword);
		}

		String ipServer = request.getRemoteAddr();
		if(Constants.localhostAddresses.contains(ipServer)){
			ipServer = Constants.localhost;
		}

		long[] serversIdGroup = SystemEnvironment.idListToArray(serverIdList);

		long idGroup = SqlManager.getConsGroupId(serversIdGroup); //Retrieve the id of the group in the partecipate table
		long idGroupConsRun = Variables.consensusVariableManager.getConsVariables(idGroup).getIdConsRun();


		PadFsLogger.log(LogLevel.DEBUG, " =>> PING-GROUP RECEIVED - FROM: ["+idServer+"] => "+ipServer+":"+sourceServerPort+" - idConsRun: "+idConsRun + "  myIdGroupConsRun: "+idGroupConsRun);

		if(Variables.getIAmInTheNet()){
			SqlManager.setUPServer(Long.parseUnsignedLong(idServer));
		}

		return new RestPong(Rest.status.ok,idGroupConsRun,null);
	}

	@RequestMapping(value={
			RestInterface.PingExtraInfos.path,
			RestInterface.PingExtraInfos.path+"/"
		}, method = RequestMethod.GET)
	public static RestPongExtraInfo pingExtraInfos(
			@PathVariable String idConsRun,
			@PathVariable String idServer,
			@PathVariable String sourceServerPort,
			@PathVariable String aviableSpace,
			@PathVariable String totalSpace,
			@PathVariable String password,
			@PathVariable ServerStatus status,
			HttpServletRequest request) {

		if(!checkServerPassword(password)){
			PadFsLogger.log(LogLevel.ERROR,Rest.errors.wrongServerPassword.toString());
			return null;
		}

		String ipServer = request.getRemoteAddr();
		if(Constants.localhostAddresses.contains(ipServer)){
			ipServer = Constants.localhost;
		}

		long idGlobalConsRun = Variables.consensusVariableManager.getConsVariables(Constants.globalConsensusGroupId).getIdConsRun();

		PadFsLogger.log(LogLevel.DEBUG, " =>> PING RECEIVED - FROM: ["+idServer+"] => "+ipServer+":"+sourceServerPort+" - idConsRun: "+idConsRun + "  myIdGlobalConsRun: "+idGlobalConsRun);

		if(Variables.getIAmInTheNet()){
			SqlManager.updateServerExtraInfos(Long.parseUnsignedLong(idServer), aviableSpace, totalSpace,status);
			Variables.updateServerList(Long.parseUnsignedLong(idServer), "1", SystemEnvironment.getDateTime(), aviableSpace,totalSpace,status); //UPDATE THE VARIABLES SERVER LIST
		}

		return new RestPongExtraInfo(idGlobalConsRun);
	}


	/**
	 * The method show a web page for the upload of the file to the system
	 * @return HTML
	 */
	@RequestMapping(value={
			"/put/",
			"/put",
	}, produces = MediaType.TEXT_HTML_VALUE)
	public @ResponseBody String put(HttpServletRequest request) {

		StringBuilder s = new StringBuilder();
		//s.append(headerHtml());

		if(!Variables.isNetworkUp()){
			s.append(Constants.Rest.errors.networkDown);
			s.append(footerHtml());
			return s.toString();
		}

		PadFsLogger.log(LogLevel.INFO, "+++ HTML UPLOAD FILE +++", "white", "yellow", true);

		s.append("<center><form action=\"#\" method=\"POST\" onSubmit='return submitLocalJSON(\"/putAction\",\"0,1,0,1,1\")' enctype=\"multipart/form-data\" >"
				+ createLinkTitleForm("Upload interface")
				+ createBootstrapInputForm("file", "File", Constants.Rest.Put.fieldNameFileUpload, Constants.Rest.Put.fieldNameFileUpload)
				+ createBootstrapInputForm("text","* User owner","usernameOwner","usernameOwner")
				+ createBootstrapInputForm("text","Path","path","path")
				+ createBootstrapInputForm("text","* Name","name","name")
				+ createBootstrapInputForm("text","Username","user","user")
				+ createBootstrapInputForm("password","Password","password","password")
				+ "* : optional elements"
				+ createButtonForm("success","UPLOAD")
				+ "</form></center>\n");

		//s.append(footerHtml());
		return s.toString();
	}


	@RequestMapping(value={
			RestInterface.IsPresent.path,
			RestInterface.IsPresent.path+"/"
	}, method = RequestMethod.GET)
	public RestIsPresent  isPresent(

		    @PathVariable("user") Integer user, 
		    @PathVariable("serverPassword") String serverPassword, 
		    @PathVariable("checksum") String checksum,
		    HttpServletRequest request,
	    HttpServletResponse response) {


		String path = getPathFromRequest(request,4);
				
		if(checkServerPassword(serverPassword)){
			if(LocalFsManager.isHosted(user, path, checksum)){
				return new RestIsPresent(Rest.status.ok, null);
			}
			else{
				return new RestIsPresent(Rest.status.error, null);
			}

		}

		return new RestIsPresent(Rest.status.error, Constants.Rest.errors.wrongServerPassword);
	}

	@RequestMapping(value={
			RestInterface.GetFile.path,
			RestInterface.GetFile.path+"/"
	}, method = RequestMethod.GET)
	public FileSystemResource  getFile(
		    @PathVariable("idOwner") Integer idUser, 
		    @PathVariable("serverPassword") String serverPassword,  
		    @PathVariable("checksum") String checksum, 
		    HttpServletRequest request,
		    HttpServletResponse response) {

			String path = getPathFromRequest(request,4);

			if(!checkServerPassword(serverPassword)){
				PadFsLogger.log(LogLevel.ERROR, "wrong serverPassword");
				return null;
			}
			

			String physicalPath = LocalFsManager.getPhysicalFile(idUser,path,checksum);

			try{
				return new FileSystemResource(physicalPath);
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.ERROR, "impossible to create a file system resource: "+e.getMessage());
				return null;
			}


	}

	@RequestMapping(value={
			RestInterface.IsManaged.path,
			RestInterface.IsManaged.path+"/"
	}, method = RequestMethod.GET)
	public RestIsManaged  isManaged(
		    @PathVariable("user") Integer idUser, 
		    @PathVariable("serverPassword") String serverPassword,  
		    @PathVariable("checksum") String checksum, 
		    HttpServletRequest request,
		    HttpServletResponse response) {

			String path = getPathFromRequest(request,4);
			
			if(!checkServerPassword(serverPassword)){
				PadFsLogger.log(LogLevel.ERROR, "wrong serverPassword");
		    	return new RestIsManaged(Rest.status.error, null, Rest.errors.wrongServerPassword);
			}
			
			MetaInfo file = SqlManager.getManagedFile(idUser, path, null);
			
			if(file != null && file.getChecksum().equals(checksum)){
				PadFsLogger.log(LogLevel.DEBUG, "file is Managed: "+idUser+" "+path+" "+checksum);
				return new RestIsManaged(Rest.status.ack, file.getHostersId(), null);
			}

			PadFsLogger.log(LogLevel.DEBUG, "file is NOT Managed: "+idUser+" "+path+" "+checksum);
			return new RestIsManaged(Rest.status.nack, null, null); 

	}


	@RequestMapping(value={
			RestInterface.NotifyPutFile.path,
			RestInterface.NotifyPutFile.path+"/"
			
	}, method = RequestMethod.GET)
	public RestNotifyPutFileResponse  notifyPut(
		    @PathVariable("idOwner") Integer idOwner, 
		    @PathVariable("serverPassword") String serverPassword, 
		    @PathVariable("size") String size, 
		    @PathVariable("dateTime") String dateTime, 
		    @PathVariable("isDirectory") Boolean isDir, 
		    @PathVariable("idParentDir") String idParentDir,
		    HttpServletRequest request,
		    HttpServletResponse response) {


			if(!checkServerPassword(serverPassword)){
				PadFsLogger.log(LogLevel.ERROR, "wrong serverPassword");
		    	return new RestNotifyPutFileResponse(Rest.status.error, Rest.errors.wrongServerPassword);
			}
			
			String path = getPathFromRequest(request,7);
			if(path == null){
				PadFsLogger.log(LogLevel.WARNING, "Parameter error:" + request.getServletPath());
				return new RestNotifyPutFileResponse(Rest.status.error,  Rest.errors.parameterError); 
			}
			
			
			if(idParentDir != null && !idParentDir.equals("")){
				PadFsLogger.log(LogLevel.DEBUG, "add in directory");
				DirectoryListingItem item = new DirectoryListingItem(path, idOwner, size, dateTime, isDir, idParentDir);
				if(SqlManager.addInDirectoryListing(item)){
					return new RestNotifyPutFileResponse(Rest.status.ack,null);
				}
				return new RestNotifyPutFileResponse(Rest.status.error,Rest.errors.error);
			}
			
			
			return new RestNotifyPutFileResponse(Rest.status.error,Rest.errors.fileNotFound);
			
	}
	
	@RequestMapping(value={
			RestInterface.NotifyDeleteFile.path,
			RestInterface.NotifyDeleteFile.path+"/"
	}, method = RequestMethod.GET)
	public RestNotifyDeleteFileResponse  notifyDelete(
		    @PathVariable("idOwner") Integer idOwner, 
		    @PathVariable("serverPassword") String serverPassword, 
		    @PathVariable("isDirectory") Boolean isDir, 
		    HttpServletRequest request,
		    HttpServletResponse response) {
	   


			if(!checkServerPassword(serverPassword)){
				PadFsLogger.log(LogLevel.ERROR, "wrong serverPassword");
		    	return new RestNotifyDeleteFileResponse(Rest.status.error, Rest.errors.wrongServerPassword);
			}
			
			String path = getPathFromRequest(request,4);
			if(path == null){
				PadFsLogger.log(LogLevel.WARNING, "Parameter error:" + request.getServletPath());
				return new RestNotifyDeleteFileResponse(Rest.status.error,  Rest.errors.parameterError); 
			}
			
			
			String parentPath = SystemEnvironment.getParentPath(path);
			String uniqueIdParentDir = SqlManager.getUniqueIdDir(parentPath, idOwner);


			if(uniqueIdParentDir != null){ 
				PadFsLogger.log(LogLevel.DEBUG, "delete from directoryListing");
				
				DirectoryListingItem item = new DirectoryListingItem(path, idOwner, null,null, isDir, uniqueIdParentDir);
				
				if(SqlManager.removeFromDirectoryListing(item)){
					return new RestNotifyDeleteFileResponse(Rest.status.ack,null);
				}
				return new RestNotifyDeleteFileResponse(Rest.status.error,Rest.errors.error);
			}
			else{
				PadFsLogger.log(LogLevel.ERROR, "uniqueIdParentDir is null");
			}
			
			return new RestNotifyDeleteFileResponse(Rest.status.error,Rest.errors.fileNotFound);
			
	}
	
	@RequestMapping(value={
			RestInterface.ExistsFile.path,
			RestInterface.ExistsFile.path+"/"
	}, method = RequestMethod.GET)
	public RestIsManaged  existsFile(
		    @PathVariable("user") Integer idUser, 
		    @PathVariable("serverPassword") String serverPassword,  
		    @PathVariable("uniqueId") String uniqueId, 
		    HttpServletRequest request,
		    HttpServletResponse response) {

			String path = getPathFromRequest(request,4);
			
			if(!checkServerPassword(serverPassword)){
				PadFsLogger.log(LogLevel.ERROR, "wrong serverPassword");
		    	return new RestIsManaged(Rest.status.error, null, Rest.errors.wrongServerPassword);
			}
			
			MetaInfo file = SqlManager.getManagedFile(idUser, path, uniqueId);
			if(file != null){
				PadFsLogger.log(LogLevel.DEBUG, "file is Managed: "+idUser+" "+path);
				return new RestIsManaged(Rest.status.ack, null, null);
			}
			
			PadFsLogger.log(LogLevel.DEBUG, "file is NOT Managed: "+idUser+" "+path);
			return new RestIsManaged(Rest.status.nack, null, null); 


	}

	@RequestMapping(value={
			RestInterface.ExistsDir.path,
			RestInterface.ExistsDir.path+"/"
			
	}, method = RequestMethod.GET)

	public RestIsManaged  existsDir(
		    @PathVariable("user") Integer idUser, 
		    @PathVariable("serverPassword") String serverPassword, 
		    @PathVariable("uniqueId") String uniqueId, 
		    HttpServletRequest request,
		    HttpServletResponse response) {
	   

			if(!checkServerPassword(serverPassword)){
				PadFsLogger.log(LogLevel.ERROR, "wrong serverPassword");
		    	return new RestIsManaged(Rest.status.error, null, Rest.errors.wrongServerPassword);
			}
			
			String path = getPathFromRequest(request,4);
			if(path == null){
				PadFsLogger.log(LogLevel.WARNING, "Parameter error:" + request.getServletPath());
				return new RestIsManaged(Rest.status.error, null, Rest.errors.parameterError); 
			}
			
			if(SqlManager.checkDirExists(path, idUser,uniqueId) != null){
				PadFsLogger.log(LogLevel.DEBUG, "Directory is Managed: "+idUser+" "+path);
				return new RestIsManaged(Rest.status.ack, null, null);
			}

			PadFsLogger.log(LogLevel.DEBUG, "Directory is NOT Managed: "+idUser+" "+path);
			return new RestIsManaged(Rest.status.nack, null, null); 

	}
	
	
	@RequestMapping(value={
			RestInterface.GetDirUniqueId.path,
			RestInterface.GetDirUniqueId.path+"/"
			
	}, method = RequestMethod.GET)
	public RestIsDirManaged  getDirUniqueId(
		    @PathVariable("user") Integer idUser, 
		    @PathVariable("serverPassword") String serverPassword, 
		    HttpServletRequest request,
		    HttpServletResponse response) {
	   

			if(!checkServerPassword(serverPassword)){
				PadFsLogger.log(LogLevel.ERROR, "wrong serverPassword");
		    	return new RestIsDirManaged(Rest.status.error, null,null, Rest.errors.wrongServerPassword);
			}
			
			String path = getPathFromRequest(request,3);
			if(path == null){
				PadFsLogger.log(LogLevel.WARNING, "Parameter error:" + request.getServletPath());
				return new RestIsDirManaged(Rest.status.error, null,null, Rest.errors.parameterError); 
			}

			
			String uniqueId=null;
			if((uniqueId = SqlManager.checkDirExists(path, idUser,null)) != null){
				
				PadFsLogger.log(LogLevel.DEBUG, "Directory is Managed uid: "+uniqueId+"  -  "+idUser+" "+path);
				return new RestIsDirManaged(Rest.status.ack, null,uniqueId,  null);
			}

			PadFsLogger.log(LogLevel.ERROR, "CCC");
			PadFsLogger.log(LogLevel.DEBUG, "Directory is NOT Managed: "+idUser+" "+path);
			return new RestIsDirManaged(Rest.status.nack, null,null, null); 

	}


	@RequestMapping(value={
			RestInterface.Remove.path,
			RestInterface.Remove.path+"/"
	}, method = RequestMethod.GET)
    public static @ResponseBody DeferredResult<RestRemove> remove(
    		@PathVariable("user") String user,
    		@PathVariable("password") String password,
    		@PathVariable("usernameOwner") String usernameOwner,
    		HttpServletRequest request){

		String path = getPathFromRequest(request, 4);	

		DeferredResult<RestRemove> responseMessage = new DeferredResult<>();
		JobClientOp op = null;

		if(!Variables.isNetworkUp()){
			responseMessage.setResult(new RestRemove(Rest.status.error, Rest.errors.networkDown));
			return responseMessage;
		}
		op = new Remove(responseMessage,usernameOwner,path,user,password);

		PadFsLogger.log(LogLevel.DEBUG, "OPERAZIONE REMOVE AGGIUNTA IN CODA: "+op.getIdOp());
	 	inOp.add(op);
    	return responseMessage;
    }
	

	
	
	@RequestMapping(value={
			RestInterface.Chmod.path,
			RestInterface.Chmod.path+"/"
	}, method = RequestMethod.GET)
	 public static @ResponseBody DeferredResult<RestChmod> chmod(
	    		@PathVariable("user") String user,
	    		@PathVariable("password") String password,
	    		@PathVariable("usernameTarget") String usernameTarget,
	    		@PathVariable("permission") Constants.Permission permission,
	    		@PathVariable("usernameOwner") String usernameOwner,
	    		HttpServletRequest request){
			
			String path = getPathFromRequest(request, 6);	
			

			
		DeferredResult<RestChmod> responseMessage = new DeferredResult<>();
		JobClientOp op = null;


		if(!Variables.isNetworkUp()){
			responseMessage.setResult(new RestChmod(Rest.status.error, Rest.errors.networkDown));
			return responseMessage;
		}

		op = new Chmod(responseMessage,user,password,usernameOwner,path,usernameTarget,permission);
		
		PadFsLogger.log(LogLevel.DEBUG, "OPERAZIONE CHMOD AGGIUNTA IN CODA: "+op.getIdOp());
	 	inOp.add(op);
    	return responseMessage;
    }
	
	
	/**
	 * Reply to the put action of the upload
	 * @param file the file that is uploaded
	 * @return confirm of the upload or the error
	 */
	@RequestMapping(value={
			RestInterface.Put.path,
			RestInterface.Put.path+"/"
	}, 		method=RequestMethod.POST
			)//,produces = MediaType.TEXT_HTML_VALUE)
    public static @ResponseBody DeferredResult<RestPut> putAction(
    		@RequestParam("user") String user,
    		@RequestParam("password") String password,
    		@RequestParam("usernameOwner") String usernameOwner,
    		@RequestParam("path") String path ,
    		@RequestParam("name") String name ,
    		@RequestParam(Constants.Rest.Put.fieldNameFileUpload) MultipartFile file){


		DeferredResult<RestPut> responseMessage = new DeferredResult<>();
		JobClientOp put = null;


		path = SystemEnvironment.normalizePath(path);


		if(!Variables.isNetworkUp()){
			responseMessage.setResult(new RestPut(Rest.status.error, null, Rest.errors.networkDown));
			return responseMessage;
		}

		/* if the usernameOwner field is left blank */
		if(usernameOwner == null || usernameOwner.equals("")){
			usernameOwner = user;
		}
		
		if(name == null || name.equals(""))
			put = new Put(responseMessage, user,password,usernameOwner,path,file);
		else
			put = new Put(responseMessage, user,password,usernameOwner,path,name,file);

		PadFsLogger.log(LogLevel.DEBUG, "OPERAZIONE PUT AGGIUNTA IN CODA: "+put.getIdOp());
		inOp.add(put);
		return responseMessage;
	}



	

	/**
	 * Reply to the transfer
	 * @param file the file that is uploaded
	 * @return confirm of the upload or the error
	 */
	@RequestMapping(
			value={
					RestInterface.Transfer.path,
					RestInterface.Transfer.path+"/"
			},
			method=RequestMethod.POST
	)
    public static @ResponseBody RestTransfer transfer(
    		@RequestParam("owner") Integer idOwner,
    		@RequestParam("path")  String encodedPath ,
    		@RequestParam("label")  long label ,
			@RequestParam("password")  String password,
			@RequestParam("checksum")  String checksum,
			@RequestParam("file")  MultipartFile file,
			HttpServletRequest req, HttpServletResponse response){


		
		String decodedPath;
		try {
			decodedPath = URLDecoder.decode(encodedPath,Constants.UTF8);
			decodedPath = SystemEnvironment.normalizePath(decodedPath);
		} catch (UnsupportedEncodingException e) {
			PadFsLogger.log(LogLevel.WARNING, "impossible to decode the path:" +encodedPath);
			return new RestTransfer(Rest.status.error, Rest.errors.parameterError);
		}
		
		if(!checkServerPassword(password)){
			PadFsLogger.log(LogLevel.ERROR,Constants.Rest.errors.wrongServerPassword.toString());
			return new RestTransfer(Rest.status.error, Rest.errors.wrongServerPassword);
		}

		if(!Variables.getIAmInTheNet())
			return new RestTransfer(Rest.status.error, Rest.errors.networkDown);

		if(LocalFsManager.isHosted(idOwner, decodedPath, checksum)){
			PadFsLogger.log(LogLevel.DEBUG, "file is already hosted");
			return new RestTransfer(Rest.status.error, Rest.errors.fileAlreadyHosted);
		}

		PadFsLogger.log(LogLevel.DEBUG, "-- RECEIVED FILE - TRANSFER START --");
		Transfer trans = new Transfer(idOwner,decodedPath,file,label,checksum);
		PadFsLogger.log(LogLevel.DEBUG, "start operation HOSTING FILE USER: "+idOwner+" PATH:"+decodedPath);
 

		if(trans.execute()){
			PadFsLogger.log(LogLevel.DEBUG, "HOSTED FILE USER: "+idOwner+" PATH:"+decodedPath);
			PadFsLogger.log(LogLevel.DEBUG, "-- TRANSFER END --");
			return new RestTransfer(Rest.status.ack,null);
		}else{
			PadFsLogger.log(LogLevel.ERROR, "TRANSFER NOT SUCCEDED USER: "+idOwner+" PATH:"+decodedPath);
			PadFsLogger.log(LogLevel.DEBUG, "-- TRANSFER END --");
			return new RestTransfer(Rest.status.nack,null);
		}
	}


	@RequestMapping(value={
			RestInterface.Get.path,
			RestInterface.Get.path+"/"
			})
	public static DeferredResult<FileSystemResource> get(
			@PathVariable String user,
			@PathVariable String password,
			@PathVariable String usernameOwner,
			HttpServletRequest request,
			HttpServletResponse response
			){

		DeferredResult<FileSystemResource> ret = new DeferredResult<>();

		String path = getPathFromRequest(request, 4);

		JobClientOp op = new Get(ret,user,password,usernameOwner,path,response);
		inOp.add(op);

		return ret;
	}



	
	@RequestMapping(value={
			RestInterface.GetFileInfo.path,
			RestInterface.GetFileInfo.path+"/"
			})
	public static DeferredResult<RestGetFileInfo> getMetaInfo(
			@PathVariable String user,
			@PathVariable String password,
			@PathVariable String usernameOwner,
			HttpServletRequest request
			){
		DeferredResult<RestGetFileInfo> ret = new DeferredResult<>();

		String path = getPathFromRequest(request, 4);	
		
		if(!Variables.isNetworkUp()){
			ret.setResult(new RestGetFileInfo(Rest.status.error, null,null,0,null, Rest.errors.networkDown));
			return ret;
		}

		JobClientOp op = new GetFileInfo(ret,user,password,usernameOwner,path);
		inOp.add(op);
 
		return ret;
	}
	

	
	@RequestMapping(value={
			RestInterface.List.path1,
			})
	public static DeferredResult<RestList> list_short(
			@PathVariable String user,
			@PathVariable String password,
			@PathVariable String usernameOwner
			){
		return list(user,password,usernameOwner,Constants.rootDirectory);
	}
	
	@RequestMapping(value={
			RestInterface.List.path2,
			RestInterface.List.path2+"/"
			})
	public static DeferredResult<RestList> list_long(
			@PathVariable String user,
			@PathVariable String password,
			@PathVariable String usernameOwner,
			HttpServletRequest request
			){
		
		String path = getPathFromRequest(request, 4);	
	
		return list(user,password,usernameOwner,path);
	}
	private static DeferredResult<RestList> list(String user, String password, String usernameOwner, String path){
		DeferredResult<RestList> ret = new DeferredResult<>();

		if(Variables.isNetworkUp() == false){
			ret.setResult(new RestList(Rest.status.error, null,Rest.errors.networkDown, null));
			return ret;
		}
		
		JobClientOp op = new jobManagement.jobOperation.clientOp.List(ret,usernameOwner,path,user,password);
		inOp.add(op);
		
		return ret;
	}

	
	@RequestMapping(value={
			RestInterface.AddServer.path,
			RestInterface.AddServer.path+"/"
	})
	public static DeferredResult<RestAddServerResponse> addServer(
			@PathVariable String idServer,
			@PathVariable String ipList,
			@PathVariable String port,
			@PathVariable String password
	) {
		if(!checkServerPassword(password)){
			PadFsLogger.log(LogLevel.ERROR,Constants.Rest.errors.wrongServerPassword.toString());
			return null;
		}


		DeferredResult<RestAddServerResponse> responseMessage = new DeferredResult<>();
		Long id = null;
		if(idServer != null && !idServer.equals("") && !idServer.equals("null")){
			id=Long.parseUnsignedLong(idServer);
			if(id <= 0)
				id = null;
		}

		if(!Variables.getIAmInTheNet()){
			responseMessage.setResult(new RestAddServerResponse(Rest.status.error, null, id, Rest.errors.networkDown));
			return responseMessage;
		}

		try {
			ipList	= URLDecoder.decode(ipList,"UTF-8");
			port 	= URLDecoder.decode(port,"UTF-8");
		} catch (UnsupportedEncodingException e) {
			PadFsLogger.log(LogLevel.ERROR, "malformed request: /addServer/"+idServer+"/"+ipList+"/"+port+"/"+e.getMessage());
			responseMessage.setResult(new RestAddServerResponse(Rest.status.error, null, id, Rest.errors.parameterError));
			return responseMessage;
		}
		
		JobOperation addServer = new AddServer(id,ipList,Integer.parseInt(port),responseMessage);

		inOp.add(addServer);
		PadFsLogger.log(LogLevel.DEBUG, "OPERAZIONE addServer AGGIUNTA IN CODA: "+addServer.getIdOp());

		return responseMessage;
	}


	@RequestMapping(
			value={
					RestInterface.GlobalSynchRequest.path,
					RestInterface.GlobalSynchRequest.path+"/"
			}
	)
	public static RestGlobalSynchResponse globalSynchRequest(
			@PathVariable String password,@PathVariable long idCallerServer) {
		if(!checkServerPassword(password)){
			PadFsLogger.log(LogLevel.ERROR,Constants.Rest.errors.wrongServerPassword.toString());
			return null;
		}
		RestGlobalSynchResponse resp = new RestGlobalSynchResponse(	Rest.status.error, null,null,null,null,null,0, Rest.errors.globalSynchFailed);

		PadFsLogger.log(LogLevel.INFO, "GlobalSynch Requested by server "+idCallerServer);

		/*
		 * set the caller server in synching state
		 * it is not necessary to broadcast this information to the network. Heartbeat does it for us
		 */

		boolean res = SqlManager.updateServerStatus(idCallerServer, Constants.ServerStatus.GLOBAL_SYNCHING);
		if(!res){
			PadFsLogger.log(LogLevel.ERROR, "Failed setting synching state for server "+idCallerServer);
			return resp;
		}
		
		
		/*
		 * retrieve first the consensus idRun and later the SynchData.
		 * in this way if another global operation occur in the meanwhile, the client will need another synchronization and will not care about the inconsistent data
		 */

		long globalConsensusRun = Variables.consensusVariableManager.getConsVariables(Constants.globalConsensusGroupId).getIdConsRun();
		List<Server> serverList = SqlManager.getGlobalConsensusSyncDataServerList();
		List<User> 	userList 	= SqlManager.getGlobalConsensusSyncDataUserList();

		PadFsLogger.log(LogLevel.TRACE, "globalConsensusRunId = "+globalConsensusRun + " servers: "+serverList.toString() + " users: "+userList.toString());

		if(serverList != null && userList != null && globalConsensusRun != 0 )
			resp = new RestGlobalSynchResponse(	Rest.status.ok,
					serverList,
					userList,
					Variables.getMyInterfaceIpList(),
					Variables.getServerPort(),
					Long.toUnsignedString(Variables.getServerId()),
					globalConsensusRun,
					null);


		return resp;

	}

	@RequestMapping(
			value={
					RestInterface.GroupSynchRequest.path,
					RestInterface.GroupSynchRequest.path+"/"
			}
	)
	public static RestGroupSynchResponse groupSynchRequest(
			@PathVariable long idCallerServer,
			@PathVariable String serverIdList,
			@PathVariable String password
	) {

		PadFsLogger.log(LogLevel.INFO, "GroupSynch Requested by server "+idCallerServer);
		if(!checkServerPassword(password)){
			PadFsLogger.log(LogLevel.ERROR,Constants.Rest.errors.wrongServerPassword.toString());
			return new RestGroupSynchResponse(Rest.status.error, null, Rest.errors.wrongServerPassword);
		}
		RestGroupSynchResponse resp;


		long[] serversIdGroup = SystemEnvironment.idListToArray(serverIdList);


		long idGroup = SqlManager.getConsGroupId(serversIdGroup); 
		PadFsLogger.log(LogLevel.DEBUG, "getConsGroupId = "+idGroup);

		List<JobOperation> jobList = Variables.getJobListGroup(idGroup);

		

		if( jobList!=null )
			resp = new RestGroupSynchResponse(	jobList, Rest.status.ok, null );
		else
			resp = new RestGroupSynchResponse(	null, Rest.status.error, Rest.errors.groupSynchFailed );

		return resp;

	}


	/* -- consensus Messages - */
	@RequestMapping(
			value={
					RestInterface.Prepare.path,
					RestInterface.Prepare.path+"/"
			})
	public static DeferredResult<RestReply> prepare(
			@PathVariable String op,
			@PathVariable String opType,
			@PathVariable String proposalNumber,
			@PathVariable int idConsensusRun,
			@PathVariable String password) {
		if(!checkServerPassword(password)){
			PadFsLogger.log(LogLevel.ERROR,Constants.Rest.errors.wrongServerPassword.toString());
			return null;
		}

		DeferredResult<RestReply> responseMessage = new DeferredResult<>();

		ProposalNumber propNum = null;
		try {
			proposalNumber = URLDecoder.decode(proposalNumber,"UTF-8");
			propNum = new ObjectMapper().readValue(proposalNumber, ProposalNumber.class);
			op = URLDecoder.decode(op,"UTF-8");
			opType = URLDecoder.decode(opType,"UTF-8");
			password = URLDecoder.decode(password,"UTF-8");

		} catch (IOException e) {
			PadFsLogger.log(LogLevel.ERROR, "(PREPARE) ProposalNumber Decode <"+proposalNumber+">");
			return null;
		}

		// put in BlockingQueue
		JobConsMsg<?> job = new Prepare( responseMessage, op, propNum, password, idConsensusRun, opType);
		try{ inCons.add(job); } 
		catch(IllegalStateException e){ 
			PadFsLogger.log(LogLevel.WARNING, "Prepare rejected.  the queue is full");
			responseMessage.setErrorResult(null);
		}
		PadFsLogger.log(LogLevel.DEBUG, "(PREPARE) insert in queue: <"+job.getOp().getIdOp()+">");

		//Padfs.log(LogLevel.DEBUG, "[RestPadfsController][prepare] (PREPARE) OP: "+idOp);

		return responseMessage;
	}


	@RequestMapping(value={
			RestInterface.Propose.path,
			RestInterface.Propose.path+"/"
	})
	public static DeferredResult<RestProposeResponse> propose(
			@PathVariable String op,
			@PathVariable String opType,
			@PathVariable String proposalNumber,
			@PathVariable int idConsensusRun,
			@PathVariable String password) {
		if(!checkServerPassword(password)){
			PadFsLogger.log(LogLevel.ERROR,Constants.Rest.errors.wrongServerPassword.toString());
			return null;
		}

		DeferredResult<RestProposeResponse> responseMessage = new DeferredResult<>();

		ProposalNumber propNum = null;
		try {
			proposalNumber = URLDecoder.decode(proposalNumber,"UTF-8");
			propNum = new ObjectMapper().readValue(proposalNumber, ProposalNumber.class);
			op = URLDecoder.decode(op,"UTF-8");
			opType = URLDecoder.decode(opType,"UTF-8");
			password = URLDecoder.decode(password,"UTF-8");
		} catch (IOException e) {
			PadFsLogger.log(LogLevel.ERROR, "(PROPOSE) ProposalNumber Decode <"+proposalNumber+">");
			return null;
		}


		// put in BlockingQueue
		JobConsMsg<?> job = new Propose(  responseMessage, op, propNum, password, idConsensusRun, opType);
		try{ inCons.add(job); } 
		catch(IllegalStateException e){ 
			PadFsLogger.log(LogLevel.WARNING, "Propose rejected.  the queue is full");
			responseMessage.setErrorResult(null);
		}
		PadFsLogger.log(LogLevel.DEBUG, "(PROPOSE) insert in queue: <"+job.getOp().getIdOp()+">");


		return responseMessage;
	}

	@RequestMapping(value={
			RestInterface.Accept.path,
			RestInterface.Accept.path+"/"
	})
	public static DeferredResult<RestAcceptResponse> accept(
			@PathVariable String op,
			@PathVariable String opType,
			@PathVariable String proposalNumber,
			@PathVariable int idConsensusRun,
			@PathVariable String password) {

		if(!checkServerPassword(password)){
			PadFsLogger.log(LogLevel.ERROR,Constants.Rest.errors.wrongServerPassword.toString());
			return null;
		}

		DeferredResult<RestAcceptResponse> responseMessage = new DeferredResult<>();

		ProposalNumber propNum = null;
		try {
			proposalNumber = URLDecoder.decode(proposalNumber,"UTF-8");
			propNum = new ObjectMapper().readValue(proposalNumber, ProposalNumber.class);
			op = URLDecoder.decode(op,"UTF-8");
			opType = URLDecoder.decode(opType,"UTF-8");
			password = URLDecoder.decode(password,"UTF-8");
		} catch (IOException e) {
			PadFsLogger.log(LogLevel.ERROR, "(ACCEPT) ProposalNumber Decode <"+proposalNumber+">");
			return null;
		}

		// put in BlockingQueue
		JobConsMsg<?> job = new Accept(  responseMessage, op, propNum, password, idConsensusRun, opType);
		try{ inCons.add(job); } 
		catch(IllegalStateException e){ 
			PadFsLogger.log(LogLevel.WARNING, "Accept rejected.  the queue is full");
			responseMessage.setErrorResult(null);
		}
		PadFsLogger.log(LogLevel.DEBUG, "(ACCEPT) insert in queue: <"+job.getOp().getIdOp()+">");


		return responseMessage;
	}


	/**
	 * Get lower tree
	 * @param password
	 * @param labelStart
	 * @param labelEnd
	 * @return RestGetLowerTreeResponse
	 */
	@RequestMapping(value={
			RestInterface.GetLowerTree.path,
			RestInterface.GetLowerTree.path+"/"
	})
	public static RestGetLowerTreeResponse getLwTree(
			@PathVariable String password,
			@PathVariable Long labelStart,
			@PathVariable Long labelEnd) {
		if(!checkServerPassword(password)){
			PadFsLogger.log(LogLevel.ERROR,Constants.Rest.errors.wrongServerPassword.toString());
			return null;
		}

		RestGetLowerTreeResponse responseMessage = new RestGetLowerTreeResponse(labelStart,labelEnd);

		return responseMessage;
	}


	/**
	 * Get upper tree
	 * @param password
	 * @return RestGetUpperTreeResponse
	 */
	@RequestMapping(value={
			RestInterface.GetUpperTree.path,
			RestInterface.GetUpperTree.path+"/"
			
	})
	public static RestGetUpperTreeResponse getUpTree(@PathVariable String password) {
		if(!checkServerPassword(password)){
			PadFsLogger.log(LogLevel.ERROR,Constants.Rest.errors.wrongServerPassword.toString());
			return null;
		}

		RestGetUpperTreeResponse responseMessage = new RestGetUpperTreeResponse();

		return responseMessage;
	}



	@RequestMapping(value={
			RestInterface.GetMetaInfo.path,
			RestInterface.GetMetaInfo.path+"/"
	})
	public static RestGetMetaInfo getMetaInfo(@PathVariable String password,@PathVariable Long labelStart,@PathVariable Long labelEnd) {
		if(!checkServerPassword(password)){
			PadFsLogger.log(LogLevel.ERROR,Constants.Rest.errors.wrongServerPassword.toString());
			return null;
		}

		RestGetMetaInfo responseMessage = new RestGetMetaInfo(labelStart,labelEnd);

		return responseMessage;
	}
	
	@RequestMapping(value={
			RestInterface.GetDirectoryListing.path,
			RestInterface.GetDirectoryListing.path+"/"
	})
	public static RestGetDirectoryListing getDirectoryListing(@PathVariable String password,@PathVariable Long labelStart,@PathVariable Long labelEnd) {
		if(!checkServerPassword(password)){
			PadFsLogger.log(LogLevel.ERROR,Constants.Rest.errors.wrongServerPassword.toString());
			return null;
		}

		RestGetDirectoryListing responseMessage = new RestGetDirectoryListing(labelStart,labelEnd);

		return responseMessage;
	}



	/**
	 * The method show a web page for the execution of a query
	 * @return HTML
	 */
	@RequestMapping(value={
			"/query",
			"/query/",
	},
			produces = MediaType.TEXT_HTML_VALUE)
	public static @ResponseBody String query() {
		StringBuilder s = new StringBuilder();
		//s.append(headerHtml());


		PadFsLogger.log(LogLevel.INFO, "+++ QUERY PAGE REQUESTED +++", "white", "yellow", true);

		s.append("<form method=\"POST\" enctype=\"multipart/form-data\" action=\"/queryExecute\">" +
				"\t<center><h2>Insert QUERY</h2></center>\n" +
				"\tisSelect   true: <input type=\"radio\" name=\"isSelect\" value=\"true\"/>"
				+ "false: <input CHECKED type=\"radio\" name=\"isSelect\" value=\"false\"/><br>\n"+
				"\t<textarea name=\"query\" style=\"width: 100%; height:100px\"></textarea><br>\n"+
				"\t\t<input type=\"submit\" value=\"execute\" style=\"font-size: 30px;\"/>\n" +
				"</form>\n");

		//s.append(footerHtml());
		return s.toString();
	}


	@RequestMapping(
			value={"/queryExecute","/queryExecute/"},
			method=RequestMethod.POST,
			produces = MediaType.TEXT_HTML_VALUE
	)
    public static @ResponseBody String queryExecute(
    		@RequestParam("query") String query,
    		@RequestParam("isSelect") boolean isSelect){
		
		StringBuilder sb = new StringBuilder();
		String executeAnotherQuery = "<br><a href=\"/query/\">execute another query</a><br>";
		sb.append(executeAnotherQuery);

		if(isSelect){
			ResultSet r = SqlManager.executeQueryPublic(query, true);

			sb.append("<table border=\"1\">");
			try {
				ResultSetMetaData meta = r.getMetaData();
				int columnCount = meta.getColumnCount();

				while(r.next()){
					sb.append("<tr>");
					for(int i = 1; i <= columnCount; i++){
						sb.append("<td>" + r.getString(i) + "</td>");
					}
					sb.append("</tr>");
				}
			} catch (SQLException e) {
				StackTraceElement[] a = e.getStackTrace();
				sb.append("\n<br>"+e.getMessage()+"\n<br>");
				for(int i = 0; i< a.length;i++){
					sb.append("\n<br>"+a[i].toString());
				}
				PadFsLogger.log(LogLevel.WARNING, "SQL exception during a query executed from controlPanel: "+e.getMessage()+"  query:"+query);
			}
			sb.append("</table>");
		}
		else{
			Long result = SqlManager.executeQueryModifyPublic(query, true);
			sb.append("resultId: "+String.valueOf(result));
		}

		sb.append(executeAnotherQuery);

		return sb.toString();
	}

	/*
	 * ONLY FOR TESTING PURPOSES
	@RequestMapping(
			value={ "/setIdConsRun/{idConsGroup}/{idConsRun}",
					"/setIdConsRun/{idConsGroup}/{idConsRun}/"},
			method=RequestMethod.GET)
	public static @ResponseBody String setIdConsRun(@PathVariable Long idConsGroup, @PathVariable Long idConsRun){
		if(Variables.consensusVariableManager.getConsVariables(idConsGroup).setIdConsRun(idConsRun))
			return "OK";
		return "failed";
	}
	*/

	/***********************************************************
	 *  USER MANAGER
	 ***********************************************************/
	@RequestMapping(
			value={ "/addUser/{username}/{userPass}/{password}",
					"/addUser/{username}/{userPass}/{password}/"},
			method=RequestMethod.GET
	)
	public static @ResponseBody DeferredResult<RestAddUserResponse> addUserNoId(
			@PathVariable String username,
			@PathVariable String userPass,
			@PathVariable String password){

		DeferredResult<RestAddUserResponse> responseMessage = new DeferredResult<>();

		if(!checkPanelPassword(password)){
			PadFsLogger.log(LogLevel.ERROR, Rest.errors.wrongPanelPassword.toString());
			responseMessage.setResult(new RestAddUserResponse(Rest.status.error, null, Rest.errors.wrongPanelPassword));
		}
		else{
			if(username == null || userPass == null){
				PadFsLogger.log(LogLevel.ERROR, Rest.errors.parameterError.toString());
				responseMessage.setResult(new RestAddUserResponse(Rest.status.error, null, Rest.errors.parameterError));
			}
			else{
	
		
				JobOperation addUser = new AddUser(username,userPass,responseMessage);
		
				inOp.add(addUser);
				PadFsLogger.log(LogLevel.DEBUG, "OPERAZIONE AddUser AGGIUNTA IN CODA: "+addUser.getIdOp());
			}
		}
		return responseMessage;

	}


	@RequestMapping(
			value={ "/delUser/{username}/{userPass}/{password}",
					"/delUser/{username}/{userPass}/{password}/"},
			method=RequestMethod.GET
	)
	public static @ResponseBody DeferredResult<RestDelUserResponse> delUser(
			@PathVariable String username,
			@PathVariable String userPass,
			@PathVariable String password) {


		DeferredResult<RestDelUserResponse> responseMessage = new DeferredResult<>();

		if(!checkPanelPassword(password)){
			PadFsLogger.log(LogLevel.ERROR, Rest.errors.wrongPanelPassword.toString());
			responseMessage.setResult(new RestDelUserResponse(Rest.status.error, null, Rest.errors.wrongPanelPassword));
		}
		else{
	
			if(username == null || userPass == null){
				PadFsLogger.log(LogLevel.ERROR, Rest.errors.parameterError.toString());
				responseMessage.setResult(new RestDelUserResponse(Rest.status.error, null, Rest.errors.parameterError));
			}
			else{
				JobOperation delUser = new DelUser(username,userPass,responseMessage);
		
				inOp.add(delUser);
				PadFsLogger.log(LogLevel.DEBUG, "OPERAZIONE DelUser AGGIUNTA IN CODA: "+delUser.getIdOp());

			}
		}
		return responseMessage;

	}


	
	@RequestMapping(
			value={ RestInterface.Mkdir.path,
					RestInterface.Mkdir.path+"/"},
			method=RequestMethod.GET
	)
	public static @ResponseBody DeferredResult<RestMkdir> Mkdir(
			@PathVariable String username,
			@PathVariable String userPass,
			@PathVariable String usernameOwner,
			 HttpServletRequest request
			) {
		DeferredResult<RestMkdir> responseMessage = new DeferredResult<>();
		PadFsLogger.log(LogLevel.DEBUG, "OPERAZIONE Mkdir RICHIESTA");
		
		if(Variables.isNetworkUp() == false){
			responseMessage.setResult(new RestMkdir(Rest.status.error, null, Rest.errors.networkDown));
			return responseMessage;
		}
		
		String path = getPathFromRequest(request,4);
		if(path == null){
			responseMessage.setResult(new RestMkdir(Rest.status.error, null, Rest.errors.parameterError));
			return responseMessage;
		}
		
		if(username == null || userPass == null || usernameOwner == null){
			PadFsLogger.log(LogLevel.ERROR, Rest.errors.parameterError.toString());
			responseMessage.setResult(new RestMkdir(Rest.status.error, null, Rest.errors.parameterError));
			return responseMessage;
		}


		JobClientOp mkdir = new Mkdir(usernameOwner,path,username,userPass,responseMessage);

		inOp.add(mkdir);
		PadFsLogger.log(LogLevel.DEBUG, "OPERAZIONE Mkdir AGGIUNTA IN CODA: "+mkdir.getIdOp());

		return responseMessage;

	}

	
	
	
	private static String getPathFromRequest(HttpServletRequest request,int removingParameters) {
		String path = request.getServletPath();
		if(path == null){
			return null;
		}

		path = removeParameterFromPath(path,removingParameters);
		
		return SystemEnvironment.normalizePath(path);
		
	}

	/**
	 * given a 'path' of a REST request, it return a new string without the first 'i' parameters
	 * @param path
	 * @param i
	 * @return 
	 */
	private static String removeParameterFromPath(String path, int i) {
		String regex = "^(/[^/]*){"+i+"}/";
		return path.replaceAll(regex, "");
	}

	@RequestMapping(
			value={ RestInterface.DelDir.path,
					RestInterface.DelDir.path+"/"},
			method=RequestMethod.GET
	)
	public static @ResponseBody DeferredResult<RestDeldirResponse> Deldir(
			@PathVariable String username,
			@PathVariable String userPass,
			@PathVariable String usernameOwner,
			HttpServletRequest request) {

		String path = getPathFromRequest(request, 4);
		
		DeferredResult<RestDeldirResponse> responseMessage = new DeferredResult<>();

		if(!Variables.isNetworkUp()){
			responseMessage.setResult(new RestDeldirResponse(Rest.status.error, null, Rest.errors.networkDown));
			return responseMessage;
		}

		if(username == null || userPass == null){
			PadFsLogger.log(LogLevel.ERROR, Rest.errors.parameterError.toString());
			responseMessage.setResult(new RestDeldirResponse(Rest.status.error, null, Rest.errors.parameterError));
			return null;
		}


		JobClientOp deldir = new Deldir(usernameOwner,path,username,userPass,responseMessage);

		inOp.add(deldir);
		PadFsLogger.log(LogLevel.DEBUG, "OPERAZIONE Deldir AGGIUNTA IN CODA: "+deldir.getIdOp());

		return responseMessage;

	}


	/**
	 * Given a fileName return the extension of the file
	 * @param fileName
	 * @return String extension
     */
	private String getFileType(String fileName){
		int i = fileName.lastIndexOf('.');
		String extension = null;
		if (i > 0) {
			extension = fileName.substring(i+1);
		}
		return extension;
	}

	/**
	 * Starting from fileName return the HTML content-type to return to the webpage
	 * @param fileName the complete name of the file
	 * @return String MediaType
     */
	private String getMediaType(String fileName){
		String ft = getFileType(fileName);
		if(ft == null)
			return null;

		String ret = null;

		switch (ft){
			case "jpg":
			case "jpeg":
				ret = MediaType.IMAGE_JPEG_VALUE;
				break;
			case "png":
				ret = MediaType.IMAGE_PNG_VALUE;
				break;
			case "gif":
				ret = MediaType.IMAGE_GIF_VALUE;
				break;
			case "css":
				ret = "text/css";
				break;
			case "html":
			case "htm":
				ret = MediaType.TEXT_HTML_VALUE;
				break;
			case "js":
				ret = "text/javascript";
				break;
			default:
				ret = MediaType.TEXT_PLAIN_VALUE;
				break;
		}

		return ret;
	}

	/**
	 * SPECIAL FUNCTION that maange all the HTML request for the resources like css img ecc.
	 * @throws IOException
     */
	@RequestMapping(value = {
			"/web/**"
	}, method = RequestMethod.GET)
	public void getUrl(HttpServletResponse response,
					   HttpServletRequest request) throws IOException {
		String pathGenerated = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE); //TODO check the slash !?!?! if differ from getOSFileSeparator?
		if(pathGenerated == null || pathGenerated.equals("")){
			return;
		}

		if(pathGenerated.charAt(0) == '/')
			pathGenerated = pathGenerated.substring(1); //remove the initial /

		ClassLoader classloader = Thread.currentThread().getContextClassLoader();
		InputStream in = classloader.getResourceAsStream(pathGenerated);
		String mediaType = getMediaType(pathGenerated);
		//IOUtils.closeQuietly(in);

		if(in == null){
			PadFsLogger.log(LogLevel.ERROR, "The file requested is not reachable: '"+pathGenerated+"'","white","red",true);
		}else {
			response.setHeader("Content-Type", mediaType);
			IOUtils.copy(in, response.getOutputStream());
		}
	}

	/**
	 * Generate an HTML tag with the progress bar
	 * @param percentage	the percentage to represent
	 * @param bgColor		the color of the progress bar
     * @return	HTML string that represent all the parameters
     */
	private String createProgressBar( int percentage, String bgColor){
		if(percentage > 100)
			percentage=100;

		String bootstrapColor;

		switch (bgColor){
			case "green":
			case "0":
				bootstrapColor = "progress-bar-success";
				break;
			case "blue":
			case "1":
				bootstrapColor = "progress-bar-info";
				break;
			case "red":
			case "2":
				bootstrapColor = "progress-bar-danger";
				break;
			case "yellow":
			case "3":;
				bootstrapColor = "progress-bar-warning";
				break;
			default:
				bootstrapColor = "progress-bar-default";
		}

		String s = "<div class=\"progress\">" +
				   "<div class=\"progress-bar progress-bar-striped active "+bootstrapColor+" \" role=\"progressbar\" aria-valuenow=\""+percentage+"\" aria-valuemin=\"0\" aria-valuemax=\"100\" style=\"width: "+percentage+"%\">"+
				   percentage+"%"+
				   "</div></div>";
		return s;
	}


	private String generateProgressBarServerStatus(){
		StringBuilder s = new StringBuilder();

		Map<String, ArrayList<String>> ret = SqlManager.getDataForProgressBar();
		s.append("<div id='server_occupation_status'  class='server_occupation_status'>");
		int numElem = 0;
		for (Map.Entry<String, ArrayList<String>> elem: ret.entrySet()) {
			String key = elem.getKey();
			ArrayList<String> data = elem.getValue();

			if(data != null && !data.isEmpty()){
				Integer available = Integer.parseInt(data.get(1));
				Integer total = Integer.parseInt(data.get(2));
				Integer percentage = Math.round(100-(available * 100)/total);
				String color = String.valueOf(Integer.parseInt(key)%4);
				String classBar = (numElem>0)?" class='bar' ":"";

				s.append("<div id='div_"+key+"' "+classBar+"><div style=\"float:left; width:65px;\">ID: "+key+"</div>");
				s.append(createProgressBar(percentage,color));
				s.append("</div><br/>");
				numElem++;
			}
		}
		s.append("</div>");
		return s.toString();
	}





/*
 * NEVER USED
 * 	private String generateCorrectJSpath(String originalPath, String username){
		if(originalPath.charAt(0) == Variables.getOSFileSeparator().charAt(0)){
			originalPath =  originalPath.substring(1);
		}
		originalPath =  originalPath.substring(Constants.treeViewRootLabel.length());
		if(originalPath.length()>0) { //caso root e basta
			return originalPath.substring(username.length() + 1);
		}
		return originalPath;
	}
*/

	

	/*****************************************[GENERATE_LINK_MANAGER_START]***************************************/


	private String createToggleArea(String areaId) {
		StringBuilder s = new StringBuilder();
		s.append("<div class=\"collapse personalizationArea\" id=\""+areaId+"\" style=\"top: 20px; position:relative; clear:both; text-align:left;\">\n" +
				"  <div class=\"card card-block\" style=\"text-align:left;\">\n" +
				"  </div>\n" +
				"</div>" +
				"<div id=\"reply_toggleArea\" class=\"replyArea\"> </div>");

		return s.toString();
	}


	private String createComboBox(String name, String comboId, Map<String,String> link, String nameAreaToggle){
		StringBuilder s = new StringBuilder();
		s.append("<div class=\"dropdown\" style='float: left;' >"
				+ "<button style=\"width: 200px;\" class=\"btn btn-default dropdown-toggle\" type=\"button\" id=\""+comboId+"\" data-toggle=\"dropdown\" aria-haspopup=\"true\" aria-expanded=\"true\"> "
				+ name
				+ "\n<span class=\"caret\"></span></button>\n");
		s.append("<ul class=\"dropdown-menu\" aria-labelledby=\""+comboId+"\">");
		for (Map.Entry<String, String> entry : link.entrySet()) {
			//s.append("<li><a target=\"_blank\" href=\""+entry.getValue()+"\">\n"+entry.getKey()+"</a></li>");
			s.append("<li><button class=\"btn\" onClick=\"openLink('"+entry.getValue()+"','"+nameAreaToggle+"')\" style=\"padding: 0; border: none; background: none;\" type=\"button\"  >"+entry.getKey()+"</button></li>\n");
		}
		s.append("</ul></div>\n");

		return s.toString();
	}

	private String createRefreshButton(String name, String function){
		return "<div class=\"btn btn-default\" aria-label=\"refresh tree view\" " +
				"onClick=\""+function+";\">" +
					"<span class=\"glyphicon glyphicon-refresh\" aria-hidden=\"true\"></span> "+name +
				"</div> ";
	}

	private String createDbLink(String name){
		String panelPass  = Variables.getPanelPassword();

		Map<String, String> l = new HashMap<>();
		l.put("users"			,"/dbTable/users/"+panelPass+"/");
		l.put("servers"			,"/dbTable/servers/"+panelPass+"/");
		l.put("filesManaged"	,"/dbTable/filesManaged/"+panelPass+"/");
		l.put("directoryListing","/dbTable/directoryListing/"+panelPass+"/");
		l.put("tmpFiles"		,"/dbTable/tmpFiles/"+panelPass+"/");
		l.put("grant"			,"/dbTable/grant/"+panelPass+"/");
		l.put("host"			,"/dbTable/host/"+panelPass+"/");
		l.put("consensusGroups"	,"/dbTable/consensusGroups/"+panelPass+"/");
		l.put("participate"		,"/dbTable/participate/"+panelPass+"/");
		l.put("filesHosted"		,"/dbTable/filesHosted/"+panelPass+"/");	

		return createComboBox("DATABASE TABLE","dbLink",l, name);
	}

	private String createGeneralLink(String name){
		String panelPass  = Variables.getPanelPassword();

		Map<String, String> l = new LinkedHashMap<>();
		l.put("Server list (var.) TABLE",		"/varServerList/"+panelPass+"/");
		l.put("Server list (var.) TEXT","/variablesServerList/"+panelPass+"/");

		l.put("Make Query",				"/query/");
		l.put("ALL Sys. Variables",	"/systemVariables/"+panelPass+"/");
		//	l.put("go to maintenance state",	"/goToMaintenance/"+panelPass+"/");
		//	l.put("exit maintenance state",	"/exitMaintenance/"+panelPass+"/");
			l.put("go to maintenance state",	"/intermediatePage/10/");
			l.put("exit maintenance state",	"/intermediatePage/11/");

		l.put("Upload File",		"/put/");
		l.put("Get file",			"/intermediatePage/1/");
		l.put("Get file infos",		"/intermediatePage/7/");
		l.put("Delete file",		"/intermediatePage/2/");

		l.put("Create directory",			"/intermediatePage/3/");
		l.put("Delete directory",			"/intermediatePage/4/");

		l.put("Add User",			"/intermediatePage/5/");
		l.put("Remove User",		"/intermediatePage/6/");

		l.put("Directory Listing",				"/intermediatePage/8/");
		l.put("Manage permission",				"/intermediatePage/9/");
		
		

		return createComboBox("LINK","generalLink",l,name);
	}
	
	private String createButtonClient(String name){

		Map<String, String> l = new LinkedHashMap<>();

		l.put("Upload File",		"/put/");
		l.put("Get file",			"/intermediatePage/1/");
		l.put("Get file infos",		"/intermediatePage/7/");
		l.put("Delete file",		"/intermediatePage/2/");

		l.put("Create directory",			"/intermediatePage/3/");
		l.put("Delete directory",			"/intermediatePage/4/");


		l.put("Directory Listing",				"/intermediatePage/8/");
		l.put("Manage permission",				"/intermediatePage/9/");
		
		

		return createComboBox("GENERAL LINK","generalLink",l, name);
	}

	public String createBootstrapInputForm(String type, String label,  String id, String formName){
		String spanStyle = "style=\"width:150px; text-align:left;\"";
		String inputStyle = "style=\"width:400px; text-align:left;\"";
		if(!type.equals("hidden")){
			return "<div class=\"input-group\">\n" +
					"  <span class=\"input-group-addon\" "+spanStyle+" id=\"basic-addon1\">"+label+"</span>\n" +
					"  <input type=\""+type+"\" "+inputStyle+" class=\"form-control\" name=\""+formName+"\" id=\""+id+"\" aria-describedby=\"basic-addon1\">\n" +
					"</div>\n";
		}else{
			return "<input type=\""+type+"\" "+inputStyle+" class=\"form-control\" name=\""+formName+"\" id=\""+id+"\" aria-describedby=\"basic-addon1\">\n";
		}
	}

	public String createLinkTitleForm(String title){
		return "<center><h3>"+title+"</h3></center><br>\n";
	}

	public String createHeadPage(String title){
		return "<center><h3>"+title+"</h3></center><br>\n";
	}

	public String createButtonForm(String type, String label){
		type=(type == null || type.length()==0)?"info":type;
		return "<br><center><button class=\"btn btn-"+type+"\" >"+label+"</button></center>\n";
	}

	@RequestMapping(
			value={ "/intermediatePage/{id}/",
					"/intermediatePage/{id}"
			},
			method=RequestMethod.GET
	)
	public @ResponseBody String intermediatePage(@PathVariable int id){
		StringBuilder str = new StringBuilder();
		str.append("<!DOCTYPE html>\n" +
				"<html>\n" +
				"<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n" +
				"<head>\n" +
				"<title>INTERMEDIATE PAGE</title>\n" +
				"</head>\n" +
				"<body><center>\n");

		switch (id){
			case 1: // /get/{user}/{password}/{usernameOwner}/{path}/
				str.append(createLinkTitleForm("GET FILE"));
				str.append("<form action=\"#\" method=\"POST\" onSubmit='return submitLocalJSON(\"/redirectIntermediatePage/1/\",\"1,1,1,1\")'>");
				str.append(
								createBootstrapInputForm("text","Username","user","user")+
								createBootstrapInputForm("password","Password","password","password")+
								createBootstrapInputForm("text","User Owner","usernameOwner","usernameOwner")+
								createBootstrapInputForm("text","Path","path","path")+
										createButtonForm("info","GET")
				);
				break;
			case 2: // /remove/{user}/{password}/{usernameOwner}/{path}/
				str.append(createLinkTitleForm("REMOVE FILE"));
				str.append("<form action=\"#\" method=\"POST\" onSubmit='return submitLocalJSON(\"/redirectIntermediatePage/2/\",\"1,1,1,1\")'><table>");
				str.append(
						createBootstrapInputForm("text","Username","user","user")+
						createBootstrapInputForm("password","Password","password","password")+
						createBootstrapInputForm("text","User Owner","usernameOwner","usernameOwner")+
						createBootstrapInputForm("text","Path","path","path")+
						createButtonForm("danger","REMOVE")
				);
				break;
			case 3: // /mkdir/{username}/{userPass}/{usernameOwner}/{path}/
				str.append(createLinkTitleForm("MAKE DIR FILE"));
				str.append("<form action=\"#\" method=\"POST\" onSubmit='return submitLocalJSON(\"/redirectIntermediatePage/3/\",\"1,1,1,1\")'><table>");
				str.append(
						createBootstrapInputForm("text","Username","username","username")+
						createBootstrapInputForm("password","Password","userPass","userPass")+
						createBootstrapInputForm("text","User Owner","usernameOwner","usernameOwner")+
						createBootstrapInputForm("text","Path","path","path")+
						createButtonForm("success","SEND")
				);
				break;
			case 4: // /deldir/{username}/{userPass}/{usernameOwner}/{path}/
				str.append(createLinkTitleForm("DELETE DIR"));
				str.append("<form action=\"#\" method=\"POST\" onSubmit='return submitLocalJSON(\"/redirectIntermediatePage/4/\",\"1,1,1,0\")'><table>");
				str.append(
						createBootstrapInputForm("text","Username","username","username")+
						createBootstrapInputForm("password","Password","userPass","userPass")+
						createBootstrapInputForm("text","User Owner","usernameOwner","usernameOwner")+
						createBootstrapInputForm("text","Path","path","path")+
								createButtonForm("danger","DELETE")
				);
				break;
			case 5: // /addUser/{username}/{userPass}/{password}/
				str.append(createLinkTitleForm("ADD USER"));
				str.append("<form action=\"#\" method=\"POST\" onSubmit='return submitLocalJSON(\"/redirectIntermediatePage/5/\",\"1,1\")'><table>");
				str.append(
						createBootstrapInputForm("text","Username","username","username")+
						createBootstrapInputForm("password","Password","userPass","userPass")+
								createButtonForm("success","CREATE")
				);
				break;
			case 6: // /delUser/{username}/{userPass}/{password}/
				str.append(createLinkTitleForm("DELETE USER"));
				str.append("<form action=\"#\" method=\"POST\" onSubmit='return submitLocalJSON(\"/redirectIntermediatePage/6/\",\"1,1,1\")'><table>");
				str.append(
						createBootstrapInputForm("text","Username","username","username")+
						createBootstrapInputForm("password","Password","userPass","userPass")+
								createButtonForm("danger","DELETE")
				);
				break;
				
			case 7: // /getFileInfo/{user}/{password}/{usernameOwner}/{path}/
				str.append(createLinkTitleForm("GET FILE INFOs"));
				str.append("<form action=\"#\" method=\"POST\" onSubmit='return submitLocalJSON(\"/redirectIntermediatePage/7/\",\"1,1,1,0\")'><table>");
				str.append(
						createBootstrapInputForm("text","Username","user","user")+
						createBootstrapInputForm("password","Password","password","password")+
						createBootstrapInputForm("text","User Owner","usernameOwner","usernameOwner")+
						createBootstrapInputForm("text","Path","path","path")+
								createButtonForm("info","GET INFOs")
				);
				break;
			
			case 8: // /list/{user}/{password}/{usernameOwner}/{path}/
				str.append(createLinkTitleForm("DIRECTORY LIST"));
				str.append("<form action=\"#\" method=\"POST\" onSubmit='return submitLocalJSON(\"/redirectIntermediatePage/8/\",\"1,1,1,0\")'><table>");
				str.append(
						createBootstrapInputForm("text","Username","user","user")+
						createBootstrapInputForm("password","Password","password","password")+
						createBootstrapInputForm("text","User Owner","usernameOwner","usernameOwner")+
						createBootstrapInputForm("text","Path","path","path")+
								createButtonForm("info","SEND")
				);
				break;
				
			case 9: // /chmod/{user}/{password}/{usernameTarget}/{permission}/{usernameOwner}/{path}/
				str.append(createLinkTitleForm("FILE/DIRECTORY PERMISSION MANAGER"));
				StringBuilder options = new StringBuilder();
				Permission perms[] = Constants.Permission.class.getEnumConstants();
				for(int i = 0;i< perms.length;i++){
					options.append("<option>");
					options.append(perms[i]);
					options.append("</option>");
				}
				str.append("<form action=\"#\" method=\"POST\" onSubmit='return submitLocalJSON(\"/redirectIntermediatePage/9/\",\"1,1,1,1,1,1,1\")'><table>");
				str.append(
						createBootstrapInputForm("text","Username","user","user")+
						createBootstrapInputForm("password","Password","password","password")+
						createBootstrapInputForm("text","Username Target","usernameTarget","usernameTarget")+
						"<label for=\"permission\" >Permission: </label><select class=\"selectpicker\" name=\"permission\" id=\"permission\">" + options.toString() + "</select>"+
						createBootstrapInputForm("text","User Owner","usernameOwner","usernameOwner")+
						createBootstrapInputForm("text","Path","path","path")+
								createButtonForm("success","CHANGE")
				);
				break;
			case 10: // /goToMaintenance/{panelPassword}/
				str.append(createLinkTitleForm("Go to Maintenance State"));
				str.append("<form action=\"#\" method=\"POST\" onSubmit='return submitLocalJSON(\"/redirectIntermediatePage/10/\",\"0\")'><table>");
				str.append(
						createBootstrapInputForm("hidden","gotomaintenance","gotomaintenance","gotomaintenance")+
						createButtonForm("submit","Go To Mainenance")
				);
				break;
			case 11: // /exitMaintenance/{panelPassword}/
				str.append(createLinkTitleForm("Exit Maintenance State"));
				str.append("<form action=\"#\" method=\"POST\" onSubmit='return submitLocalJSON(\"/redirectIntermediatePage/11/\",\"0\")'><table>");
				str.append(
						createBootstrapInputForm("hidden","exitmaintenance","exitmaintenance","exitmaintenance")+
						createButtonForm("submit","Exit Mainenance")
				);
				break;
		}
		str.append("</form></center>\n");
		return str.toString();
	}

	@RequestMapping(
			value={ "/redirectIntermediatePage/{id}/",
					"/redirectIntermediatePage/{id}"},
			method = { RequestMethod.GET, RequestMethod.POST }
	)
	public @ResponseBody ModelAndView redirectIntermediatePage(@PathVariable int id, @RequestParam Map<String,String> allRequestParams) {
		String panelPass = Variables.getPanelPassword();
		String redirect = null;
		String user, password, path, username, usernameOwner, userPass, permission, usernameTarget;
		PadFsLogger.log(LogLevel.DEBUG,"REDIRECT PARAMS:"+allRequestParams.toString(),"white","red",true);

		switch (id) {
			case 1: // /get/{user}/{password}/{usernameOwner}/{path}/
				user 			= allRequestParams.get("user");
				usernameOwner 	= allRequestParams.get("usernameOwner");
				password 		= allRequestParams.get("password");
				path 			= allRequestParams.get("path");
				redirect 		= "/get/"+user+"/"+password+"/"+usernameOwner+"/"+path+"/";
				break;
			case 2: // /remove/{user}/{password}/{path}/
				user 			= allRequestParams.get("user");
				usernameOwner 	= allRequestParams.get("usernameOwner");
				password		= allRequestParams.get("password");
				path 			= allRequestParams.get("path");
				redirect 		= "/remove/"+user+"/"+password+"/"+usernameOwner+"/"+path+"/";
				break;
			case 3: // /mkdir/{path}/{username}/{userPass}/
				username		= allRequestParams.get("username");
				usernameOwner 	= allRequestParams.get("usernameOwner");
				userPass		= allRequestParams.get("userPass");
				path 			= allRequestParams.get("path");
				redirect 		= "/mkdir/"+username+"/"+userPass+"/"+usernameOwner+"/"+path+"/";
				break;
			case 4: // /deldir/{path}/{username}/{userPass}/
				username		= allRequestParams.get("username");
				usernameOwner 	= allRequestParams.get("usernameOwner");
				userPass 		= allRequestParams.get("userPass");
				path 			= allRequestParams.get("path");
				redirect 		= "/deldir/"+username+"/"+userPass+"/"+usernameOwner+"/"+path+"/";
				break;
			case 5: // /addUser/{username}/{userPass}/{password}/
				username	= allRequestParams.get("username");
				userPass 	= allRequestParams.get("userPass");
				redirect 	= "/addUser/"+username+"/"+userPass+"/"+panelPass+"/";
				break;
			case 6: // /delUser/{username}/{userPass}/{password}/
				username	= allRequestParams.get("username");
				userPass 	= allRequestParams.get("userPass");
				path 		= allRequestParams.get("path");
				redirect 	= "/delUser/"+username+"/"+userPass+"/"+panelPass+"/";
				break;
			case 7: // /getFileInfo/{user}/{password}/{usernameOwner}/{path}/
				user 			= allRequestParams.get("user");
				usernameOwner 	= allRequestParams.get("usernameOwner");
				password 		= allRequestParams.get("password");
				path 			= allRequestParams.get("path");
				redirect 		= "/getFileInfo/"+user+"/"+password+"/"+usernameOwner+"/"+path+"/";
				break;
			case 8: // /list/{user}/{password}/{usernameOwner}/{path}/
				user 			= allRequestParams.get("user");
				usernameOwner 	= allRequestParams.get("usernameOwner");
				password 		= allRequestParams.get("password");
				path 			= allRequestParams.get("path");
				redirect 		= "/list/"+user+"/"+password+"/"+usernameOwner+"/"+path+"/";
				break;
			case 9: // /chmod/{user}/{password}/{usernameTarget}/{permission}/{usernameOwner}/{path}/
				user 			= allRequestParams.get("user");
				usernameTarget 	= allRequestParams.get("usernameTarget");
				usernameOwner 	= allRequestParams.get("usernameOwner");
				password 		= allRequestParams.get("password");
				permission 		= allRequestParams.get("permission");
				path 			= allRequestParams.get("path");
				redirect 		= "/chmod/"+user+"/"+password+"/"+usernameTarget+"/"+permission+"/"+usernameOwner+"/"+path+"/";
				break;
			case 10: // /goToMaintenance/{panelPassword}/
				redirect 		= "/goToMaintenance/"+panelPass+"/";
				break;
			case 11: // /exitMaintenance/{panelPassword}/
				redirect 		= "/exitMaintenance/"+panelPass+"/";
				break;
		}
		redirect = SystemEnvironment.normalizePath(redirect)+"/";
		PadFsLogger.log(LogLevel.DEBUG,"REDIRECT URL:"+redirect,"white","red",true);
		return new ModelAndView(new RedirectView(redirect));
	}

	/*************************************
	 * SHARED MANAGER
	 *************************************/
	@RequestMapping(value={
			RestInterface.GetSharedFile.path,
			RestInterface.GetSharedFile.path+"/"
	})
	public static DeferredResult<RestGetSharedFile> getSharedFiles(@PathVariable String username, @PathVariable String password){
		DeferredResult<RestGetSharedFile> responseMessage = new DeferredResult<>();
		if(Variables.isNetworkUp() == false){
			responseMessage.setResult(new RestGetSharedFile(Rest.status.error, Rest.errors.networkDown, null));
			return responseMessage;
		}

		Integer checkPermission = SqlManager.getIdUser(username,password);
		if(checkPermission<=0){
			responseMessage.setResult(new RestGetSharedFile(Rest.status.error, Rest.errors.userDoNotExists, null));
			return responseMessage;
		}


		RestTemplate restTemplate = SystemEnvironment.generateRestTemplate();


		List<Server> 		serverList 		= new ArrayList<Server>(Variables.getServerList());

		List<MetaInfo> 		filesList 		= new ArrayList<>();
		List<String> 		TMP_filesInList = new ArrayList<>();
		ResponseEntity<RestGetListSharedWith> response;

		//sort according to groupId and label
		Collections.sort(serverList, new Server.ServerComparator()) ;


		for(int i=0; i<serverList.size(); i++){
			Server server = serverList.get(i);
			String url = RestInterface.GetListSharedWith.generateUrl(server.getIp(),server.getPort(), username );
			
			try{
				response = restTemplate.exchange(url, HttpMethod.GET, null, RestGetListSharedWith.class);
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.WARNING,"cannot communicate with: id:"+server.getId()+" ip:"+server.getIp()+" port:"+server.getPort());
				response = null;
			}
			
			if(response != null && response.getBody()!=null) {
				Iterator<MetaInfo> files = (response.getBody()).getFileList().iterator();
				while (files.hasNext()) {
					MetaInfo currentFile = files.next();
					if (!TMP_filesInList.contains(currentFile.getChecksum())) {
						TMP_filesInList.add(currentFile.getChecksum());
						filesList.add(currentFile);
					}
				}
			}
		}
		responseMessage.setResult(new RestGetSharedFile(Rest.status.ok,null,filesList));
		return responseMessage;
	}

	@RequestMapping(value={
			RestInterface.GetListSharedWith.path,
			RestInterface.GetListSharedWith.path+"/"
	},method = { RequestMethod.GET })
	public static DeferredResult<RestGetListSharedWith> getListSharedWith(@PathVariable String serverPassword, @PathVariable String username){
		DeferredResult<RestGetListSharedWith> responseMessage = new DeferredResult<>();

		if(Variables.isNetworkUp() == false){
			responseMessage.setResult(new RestGetListSharedWith(Rest.status.error, Rest.errors.networkDown, null));
			return responseMessage;
		}

		if(checkPanelPassword(serverPassword)){
			responseMessage.setResult(new RestGetListSharedWith(Rest.status.error, Rest.errors.wrongPanelPassword, null));
			return responseMessage;
		}

		responseMessage.setResult(new RestGetListSharedWith(Rest.status.ok,null,SqlManager.getSharedFileWith(username)));
		return responseMessage;
	}

	/*****************************************[GENERATE_LINK_MANAGER_END]***************************************/


	@RequestMapping(
			value={ "/updateserverlist",
					"/updateserverlist/"
			},
			method=RequestMethod.GET
	)
	public @ResponseBody String updateserverlist() {
		StringBuilder str = new StringBuilder();
		str.append(generateProgressBarServerStatus());
		return str.toString();
	}

    @RequestMapping(value={
            RestInterface.UserList.path,
            RestInterface.UserList.path+"/"
    }, method = RequestMethod.GET)
    public static RestGetUsers userList(
            @PathVariable String password,
            HttpServletRequest request) {

        if(!checkPanelPassword(password)){
            PadFsLogger.log(LogLevel.ERROR,Rest.errors.wrongServerPassword.toString());
            return new RestGetUsers(Rest.status.error, null, Rest.errors.wrongServerPassword);
        }

        List<User> users = SqlManager.getUserList();

        return new RestGetUsers(Rest.status.ok, users, null);
    }




	private String adminPage(){
		StringBuilder str = new StringBuilder();


		//Variables.cleanFSTree(); // CLEAN AND RENEW ALL THE TREE //TODO DA RIVEDERE FARE UN THREAD CHE AGGIORNA OGNI TOT
		str.append(createLinkTitleForm("ADMINISTRATION interface"));

		str.append("<table width='100%'>");

		str.append("<tr><td width='50%' valign='top' ><h3>SERVERS DISK STATUS:</h3><div id='serverliststatus'>");
		str.append(generateProgressBarServerStatus());
		str.append("</div></td><td width='50%' valign='top'><h3>PADFS FILESYSTEM:</h3>" +
				"<div id='treeFS'>");

		/*generateFSTree(retrieveFS());
		str.append(createFolderTree());*/
		str.append("</div></td></tr>");

		str.append("<tr><td colspan='2' align='center' id='pageArea' style='position:relative;'><hr>");

		str.append("<div class=\"btn-group\" role=\"group\" aria-label=\"action button\">\n");
		str.append(createGeneralLink("toggleArea"));
		str.append(createDbLink("toggleArea"));
		str.append("<span class=\"btn-separator\"></span>");
		str.append(createRefreshButton("Refresh Tree view","updateTreeViewManagement()"));
		str.append(createRefreshButton("Refresh Server status","updateServerList()"));
		str.append("</div>\n");

		str.append("<br>");
		str.append(createToggleArea("toggleArea"));

		str.append("</td></tr>");

		str.append("</table>");

		str.append("<script>" +
				"$( document ).ready(function() {\n" +
				"    bootManagement();\n" +
				"});"+
				"</script>"
		);

		return str.toString();
	}

	private String userPage(String username){
		StringBuilder str = new StringBuilder();

		//Variables.cleanFSTree(); // CLEAN AND RENEW ALL THE TREE //TODO DA RIVEDERE FARE UN THREAD CHE AGGIORNA OGNI TOT



		str.append(createLinkTitleForm("CLIENT <"+username+"> interface"));
		str.append("<div id='treeFS'>");
		/*generateFSTree(retrieveFS());
		str.append(createFolderTree());*/

		str.append("</div>");
		str.append("<hr>");
		str.append(createButtonClient("toggleArea"));
		str.append("<span class=\"btn-separator\"></span>");
		str.append(createRefreshButton("Refresh Tree view","updateTreeView()"));

		str.append(createToggleArea("toggleArea"));

		str.append("<script>" +
				"$( document ).ready(function() {\n" +
				"    boot();\n" +
				"});"+
				"</script>"
		);
		return str.toString();
	}

	@RequestMapping(
			value={ "/loginUser",
					"/loginUser/"},
			method=RequestMethod.POST
	)
	public @ResponseBody String loginUser(@RequestParam Map<String,String> allRequestParams){
		String username = allRequestParams.get("username");
		String password = allRequestParams.get("password");
		if(SqlManager.getIdUser(username,password) > 0){
			return userPage(username);
		}else{
			return "-1";
		}
	}

	@RequestMapping(
			value={ "/loginManager",
					"/loginManager/"},
			method=RequestMethod.POST
	)
	public @ResponseBody String loginManager(@RequestParam Map<String,String> allRequestParams){
		String password = allRequestParams.get("password");
		if(Variables.getPanelPassword().toString().equals(password)){
			return adminPage();
		}else{
			return "-1";
		}
	}

	@RequestMapping(
			value={ "/management",
					"/management/"
			},
			method=RequestMethod.GET
	)
	public @ResponseBody String management(){
		StringBuilder s = new StringBuilder();

		s.append(headerHtml("MANAGEMENT PAGE"));
		s.append("<div id='page' class='page'>");
		s.append("<center><form action=\"#\" method=\"POST\" onSubmit='return checkAdminLogin()'>");
		s.append(createLinkTitleForm("LOGIN MANAGEMENT"));
		s.append(createBootstrapInputForm("password","Password","password","password"));
		s.append(createButtonForm("info","LOGIN MANAGEMENT"));
		s.append("</form></center>");
		s.append("</div>");
		s.append(footerHtml());

		return s.toString();
	}

	@RequestMapping(
			value={ "/",
					"/index",
					"/index.htm",
					"/index.html",
					"/index/"},
			method=RequestMethod.GET
	)
	public @ResponseBody String indexClient(){
		StringBuilder s = new StringBuilder();

		s.append(headerHtml("USER PAGE"));
		s.append("<div id='page' class='page'>");
		s.append("<center><form action=\"#\" method=\"POST\" onSubmit='return checkLoginUser()'>");
		s.append(createLinkTitleForm("LOGIN"));
		s.append(createBootstrapInputForm("text","Username","username","username"));
		s.append(createBootstrapInputForm("password","Password","password","password"));
		s.append(createButtonForm("info","LOGIN"));
		s.append("</form></center>");
		s.append("</div>");
		s.append(footerHtml());
		return s.toString();
	}
	
	
	

	@RequestMapping(value={
			RestInterface.GoToMaintenance.path,
			RestInterface.GoToMaintenance.path+"/"
	}, method = { RequestMethod.GET, RequestMethod.POST })
    public static @ResponseBody DeferredResult<RestMaintenanceRequest> goToMaintenance(
    		@PathVariable("password") String serverPassword,
    		HttpServletRequest request){
		

		DeferredResult<RestMaintenanceRequest> responseMessage = new DeferredResult<>();
		JobOperation op = null;
	
		if(!Variables.isNetworkUp()){
			responseMessage.setResult(new RestMaintenanceRequest(Rest.status.error, Rest.errors.networkDown));
			return responseMessage;
		}

		op = new MaintenanceRequested(responseMessage);
		
		PadFsLogger.log(LogLevel.DEBUG, "OPERAZIONE MaintenanceRequested AGGIUNTA IN CODA: "+op.getIdOp());
	 	inOp.add(op);
    	return responseMessage;
    }
	
	@RequestMapping(value={
			RestInterface.ExitMaintenance.path,
			RestInterface.ExitMaintenance.path+"/"
	}, method = { RequestMethod.GET, RequestMethod.POST })
    public static @ResponseBody DeferredResult<RestExitMaintenance> exitMaintenance(
    		@PathVariable("password") String serverPassword,
    		HttpServletRequest request){
		

		DeferredResult<RestExitMaintenance> responseMessage = new DeferredResult<>();
		JobOperation op = null;
	
		if(!Variables.isNetworkUp()){
			responseMessage.setResult(new RestExitMaintenance(Rest.status.error, Rest.errors.networkDown));
			return responseMessage;
		}

		op = new ExitMaintenance(responseMessage);
		
		PadFsLogger.log(LogLevel.DEBUG, "OPERAZIONE ExitMaintenance AGGIUNTA IN CODA: "+op.getIdOp());
	 	inOp.add(op);
    	return responseMessage;
    }
	
	
}