package jobManagement.jobOperation.clientOp;

import java.io.File;

import org.springframework.core.io.FileSystemResource;
import org.springframework.web.context.request.async.DeferredResult;

import com.fasterxml.jackson.annotation.JsonIgnore;

import restInterface.RestInterface;
import restInterface.RestServer;
import system.SystemEnvironment.Constants;
import system.SystemEnvironment.Constants.OperationPriority;
import system.SystemEnvironment.Constants.Rest;
import system.containers.MetaInfo;
import system.containers.Server;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;
import system.managers.SqlManager;

import javax.servlet.http.HttpServletResponse;

public class Get extends JobClientOp{
	private DeferredResult<FileSystemResource> defResultMsg;
	private HttpServletResponse response;
		
	
	public Get(DeferredResult<FileSystemResource> defResultMsg,String user,String password,String usernameOwner,String path, HttpServletResponse response){
		super(user,password,usernameOwner,path,OperationPriority.GET);
		this.defResultMsg = defResultMsg;
		this.response = response;
	}
	
	
	public void answer(FileSystemResource fileRes){
		if(defResultMsg != null)
			defResultMsg.setResult( fileRes );
	}
	

	@Override
	public boolean prepareOp() {
		Rest.errors err = checkPermission(Constants.RequiredPermissions.Get);
		if(err != null){
			replyError(err);
			PadFsLogger.log(LogLevel.ERROR,err.toString());
			return false;
		}
		
		MetaInfo file = SqlManager.getManagedFile(getUsernameOwner(),getPath(),null);

		response.reset();


		/* if file exists */
		if(file != null){
			/* if it is a directory */
			if(file.isDirectory()){
				replyError(Rest.errors.isDirectory);
				PadFsLogger.log(LogLevel.DEBUG, "metaInfo found BUT it is a directory");
				return false;
			}

			
			PadFsLogger.log(LogLevel.TRACE, "hostersIdSize="+file.getHostersId().size());
			 int i = 0;
            for(Long id : file.getHostersId()){
            	PadFsLogger.log(LogLevel.TRACE, "hoster id = "+i);
            	Server s = SqlManager.getServer(id);
            	PadFsLogger.log(LogLevel.TRACE, "server s = "+s);
            	String url  =  RestInterface.GetFile.generateUrl( s.getIp(), s.getPort(),  file.getIdOwner(), file.getChecksum(), file.getPath());

            	PadFsLogger.log(LogLevel.DEBUG, "try download resource "+i+" = "+url);
            	String tmpName;
            	FileSystemResource tmpFile = null;
            	if((tmpName = RestServer.download(url)) != null){	
            		PadFsLogger.log(LogLevel.DEBUG, "downloaded file "+url);
    				tmpFile = new FileSystemResource(tmpName);
    				if(tmpFile != null){
    					
    					/* set the calbak to delete the temporary file */
    					defResultMsg.onCompletion(new Runnable() {
    			            @Override
    			            public void run() {
    			            	try{
    			            		PadFsLogger.log(LogLevel.TRACE, "deleting temporary file");
    			            		(new File(tmpName)).delete();
    			            	}
    			            	catch(Exception e){
    			            		PadFsLogger.log(LogLevel.ERROR, "CANNOT DELETE TMP FILE");
    			            	}
    			            }
    			        });

						setResponse(getPath(),response);

    					/* send file to the client */
    					answer(tmpFile);

    					return true;
    				}
    			}

   				PadFsLogger.log(LogLevel.WARNING, "failed downloading file");
            	i++;
            }
            
	            
			
            replyError(Rest.errors.failedDownloadingFile);
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


	private HttpServletResponse setResponse(String path, HttpServletResponse response){
		String fileName 	= new File(path).getName();
		String extension 	= path.substring(path.lastIndexOf("."),path.length());
		if(fileName=="" || fileName == null) {
			if (path.lastIndexOf(".") != -1 && path.lastIndexOf(".") < path.length()){
				fileName = "file" + extension;
			}else{
				fileName = null;
			}
		}
		response.setContentType("application/force-download");
		response.setHeader("Content-Transfer-Encoding", "binary");
		if(fileName != null && fileName != "") {
			response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
		}

		return response;
	}

	@Override
	public void replyError(Rest.errors message) {
		answer(null);
	}



	
	public boolean forward(Server s) {
		String  url = RestInterface.Get.generateUrl(s.getIp(),s.getPort(),getUsername(),getPassword(),getUsernameOwner(),getPath()); 	
		
	
		PadFsLogger.log(LogLevel.DEBUG, url);
		String fileName = RestServer.download(url);
					
		if(fileName != null ){
			FileSystemResource tmpFile = new FileSystemResource(fileName);
			
			/* set callback to delete tmp file on completion */
			defResultMsg.onCompletion(new Runnable() {
	            @Override
	            public void run() {
	            	try{
	            		PadFsLogger.log(LogLevel.TRACE, "deleting temporary file");
	            		(new File(fileName)).delete();
	            	}
	            	catch(Exception e){
	            		PadFsLogger.log(LogLevel.ERROR, "CANNOT DELETE TMP FILE");
	            	}
	            }
	        });
			
			/* sending file to the server */
			defResultMsg.setResult(tmpFile);
			
			return true;
		}
		
		return false;
	}
	
}
