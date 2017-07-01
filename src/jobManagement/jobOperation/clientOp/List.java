package jobManagement.jobOperation.clientOp;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.async.DeferredResult;

import com.fasterxml.jackson.annotation.JsonIgnore;

import restInterface.RestInterface;
import restInterface.op.RestList;
import system.SystemEnvironment;
import system.SystemEnvironment.Constants;
import system.SystemEnvironment.Constants.OperationPriority;
import system.SystemEnvironment.Constants.Rest;
import system.containers.DirectoryListingItem;
import system.containers.Server;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;
import system.managers.SqlManager;

public class List extends JobClientOp{
	private DeferredResult<RestList> defResultMsg;
	private java.util.List<Map<String,String>> pathList;
	
	public List(DeferredResult<RestList> defResultMsg,String userOwner, String path, String user, String password){
		super(user,password,userOwner,path,OperationPriority.LIST); 
		this.defResultMsg = defResultMsg;
		this.pathList = null;
	}
	
	@Override
	public boolean prepareOp() {
		Rest.errors err = checkPermission(Constants.RequiredPermissions.List);
		if(err != null){
			replyError(err);
			PadFsLogger.log(LogLevel.ERROR,err.toString());
			return false;
		}
		
		java.util.List<DirectoryListingItem> pathList = SqlManager.getDirectoryListing(getUsernameOwner(),getPath());
		java.util.List<Map<String,String>> ret = null;
		/* if file exists */
		if(pathList != null){
			
			/* build response */
			
			ret = new LinkedList<Map<String,String>>();
			Iterator<DirectoryListingItem> it = pathList.iterator();
			while(it.hasNext()){
				DirectoryListingItem item = it.next();
				Map<String,String> m = new HashMap<String,String>();
				m.put("path", item.getPath());
				m.put("isDirectory", String.valueOf(item.isDirectory()));
				m.put("size", String.valueOf(item.getSize()));
				m.put("dateTime", String.valueOf(item.getDateTime()));
				
				ret.add(m);
			}
			
			
			answer(Rest.status.ok,null,ret);
			PadFsLogger.log(LogLevel.DEBUG, "metaInfo found, RestList sent");
			return true;
		}
				
		replyError(Rest.errors.directoryNotFound);
		PadFsLogger.log(LogLevel.DEBUG, "directory not found");
		return false;
	}
	
	
	@Override
	public boolean completeOp() {
		// never called!
		PadFsLogger.log(LogLevel.ERROR, "this should be never called");
		return false;
	}
	
	@Override
	@JsonIgnore
	public boolean isConsensusNeeded(){
		return false;
	}
	
	public void answer(Rest.status status, Rest.errors error, java.util.List<Map<String,String>> pathList){
		if(defResultMsg != null)
			defResultMsg.setResult( 
				new RestList(status, getPath(),  error, pathList)
			);
	}


	@Override
	public void replyOperationCompleted() {
		answer(Constants.Rest.status.ok,null,pathList);
	}
	@Override
	public void replyError(Rest.errors message) {
		answer(Constants.Rest.status.error,message,null);		
	}

	
	public boolean forward(Server s) {
		RestTemplate restTemplate = SystemEnvironment.generateRestTemplate();
		String  url = RestInterface.List.generateUrl(s.getIp(),s.getPort(),getUsername(),getPassword(),getUsernameOwner(),getPath()); 
		
		try{
			PadFsLogger.log(LogLevel.DEBUG, url);
				
			ResponseEntity<RestList> response = restTemplate.exchange(url, HttpMethod.GET, null,RestList.class);
			
			if(response!= null && response.getBody() != null){
				defResultMsg.setResult(response.getBody());
				return true;
			}
		}
		catch(Exception e){
			PadFsLogger.log(LogLevel.WARNING, "communication with server "+s.getId()+" failed");
			PadFsLogger.log(LogLevel.DEBUG, e.getMessage());
			
		}
		
		return false;
	}
	
}
