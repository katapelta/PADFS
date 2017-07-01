package jobManagement.jobOperation.clientOp;

import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.async.DeferredResult;

import com.fasterxml.jackson.annotation.JsonIgnore;

import restInterface.RestInterface;
import restInterface.RestServer;
import restInterface.manageOp.RestGetPermission;
import restInterface.manageOp.RestIsPresent;
import restInterface.op.RestGetFileInfo;
import system.SystemEnvironment;
import system.SystemEnvironment.Constants;
import system.SystemEnvironment.Constants.OperationPriority;
import system.SystemEnvironment.Constants.Permission;
import system.SystemEnvironment.Constants.Rest;
import system.SystemEnvironment.Variables;
import system.containers.MetaInfo;
import system.containers.Server;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;
import system.managers.SqlManager;

public class GetFileInfo extends JobClientOp{
	private DeferredResult<RestGetFileInfo> defResultMsg;
		
	
	public GetFileInfo(DeferredResult<RestGetFileInfo> defResultMsg,String user,String password,String usernameOwner,String path){
		super(user,password,usernameOwner,path,OperationPriority.GET_FILE_INFO);
		this.defResultMsg = defResultMsg;
	}
	
	

	
	public void answer(Rest.status status, MetaInfo metaInfo, int hostersAlive, Permission userPermission, Rest.errors error){
		if(defResultMsg != null)
			defResultMsg.setResult( 
				new RestGetFileInfo(status, metaInfo, getPath(),hostersAlive, userPermission, error) 
			);
	}


	@Override
	public boolean prepareOp() {
		Rest.errors err = checkPermission(Constants.RequiredPermissions.Get);
		if(err != null){
			replyError(err);
			PadFsLogger.log(LogLevel.DEBUG,err.toString());
			return false;
		}
		
		MetaInfo file = SqlManager.getManagedFile(getUsernameOwner(),getPath(),null);
		
		/* if file exists */
		if(file != null){
			/* if it is a directory */
			if(file.isDirectory()){
				replyError(Rest.errors.isDirectory);
				PadFsLogger.log(LogLevel.DEBUG, "metaInfo found BUT it is a directory");
				return false;
			}


			/* count hostersAlive */
			PadFsLogger.log(LogLevel.TRACE, "hostersIdSize="+file.getHostersId().size());
			int i = 0;
			int hostersAlive = 0;
            for(Long id : file.getHostersId()){
            	PadFsLogger.log(LogLevel.TRACE, "hoster id = "+i);
            	Server s = SqlManager.getServer(id);
            	PadFsLogger.log(LogLevel.TRACE, "server s = "+s);
            	
            	RestIsPresent response = RestServer.isPresent(s, file);
    			
    			if(response != null && response.getStatus() == Rest.status.ok){
    				hostersAlive++;
    			}
            	            	
            	i++;
            }
            
            
            /* get user permission */
            Permission userPermission = null;
            Integer idUser = SqlManager.getIdUser(getUsername());
            Integer idOwner = SqlManager.getIdUser(getUsernameOwner());
            {
            	/* retrieve the permission from localhost because this MetaInfo is managed from this server. */
            	Server s = new Server(Constants.localhost,Variables.getServerPort());
            	RestGetPermission p = RestServer.getPermission(s,idUser,idOwner,getPath());
    		     
		        if(p != null){
		        	userPermission = p.getPermission();
		        }
            }
	        
			answer(Rest.status.ok,file,hostersAlive,userPermission,null); 
			PadFsLogger.log(LogLevel.DEBUG, "metaInfo found, RestGet sent");
			return true;
		}
		
		replyError(Rest.errors.fileNotFound);
		PadFsLogger.log(LogLevel.DEBUG, "metaInfo not found");
		return false;
	}


	@Override
	@JsonIgnore
	public boolean isConsensusNeeded(){
		return false;
	}
	
	@Override
	public boolean completeOp() { 
		// never called!
		PadFsLogger.log(LogLevel.ERROR, "this should be never called");
		return false;
	}

	@Override
	public void replyOperationCompleted() {
		// this should be never called
		replyError(null);
	}


	@Override
	public void replyError(Rest.errors message) {
		answer(Constants.Rest.status.error,null,0,null,message);
	}



	
	public boolean forward(Server s) {
		RestTemplate restTemplate = SystemEnvironment.generateRestTemplate();
		String  url = RestInterface.GetFileInfo.generateUrl(s.getIp(),s.getPort(),getUsername(),getPassword(),getUsernameOwner(),getPath()); 

		
		try{
			PadFsLogger.log(LogLevel.DEBUG, url);
				
			ResponseEntity<RestGetFileInfo> response = restTemplate.exchange(url, HttpMethod.GET, null,RestGetFileInfo.class);
			
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
