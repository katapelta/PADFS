package padfsThreads;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import restInterface.RestServer;
import restInterface.manageOp.RestIsManaged;
import system.SystemEnvironment;
import system.SystemEnvironment.Constants;
import system.SystemEnvironment.Constants.Rest;
import system.containers.DirectoryListingItem;
import system.containers.HostedFile;
import system.containers.MetaInfo;
import system.containers.Server;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;
import system.managers.LocalFsManager;
import system.managers.SqlManager;
import system.SystemEnvironment.Variables;

/**
 * periodically execute its routine:
 *   - remove filesHosted no more managed by the net
 *   - remove filesManaged belonging to deleted directories
 *   - remove directoryListing entry referencing no more managed files
 *   
 * @author matte
 *
 */
public class GarbageCollector extends StoppableThread{

	
	public GarbageCollector(){
		super();
	}
	
	public void run() {
		while(getExitingRequested() == false){
			try{
		
				cleanFilesManaged();
				
				cleanDirectoryListing();
								
				cleanFilesHosted();
			
				/* sleep for the given amount of time or until a requestToStop call will arrive */
				PadFsLogger.log(LogLevel.DEBUG, "GarbageCollector goes to sleep");
				waitFor(Variables.getSleepTime_CheckReplicasAlive());
				
			}catch(Exception e){ 
				PadFsLogger.log(LogLevel.ERROR, e.getClass().getName() + ": " + e.getMessage());
			}
		}
		PadFsLogger.log(LogLevel.INFO, "GarbageCollector shutdown"); 
		signalStopCompleted();
	}

	
	/**
	 * foreach directory managed on this server
	 *   - retrieve the list of files inside the directory
	 *      - foreach file retrieve the server list managing this file
	 *   		- ask them if they are still managing the file
	 *  		- collect responses and delete the file entry in the listing if it is garbage (because all servers have deleted it).
	 *  
	 *  compensate missing notification of deletedFiles
	 */
	private void cleanDirectoryListing() {
		PadFsLogger.log(LogLevel.DEBUG, "GarbageCollector start -cleanDirectoryListing- procedure");
		
		List<DirectoryListingItem> pathList = SqlManager.getDirectoryListing();
		
		Iterator<DirectoryListingItem> it = pathList.iterator();
		while(it.hasNext() && getExitingRequested() == false ){
			DirectoryListingItem item = it.next();
			if(item != null){
			
				/* check that fileInfo is still managed by someone in the net */
				Boolean result = null;
				if(item.isDirectory()){
					PadFsLogger.log(LogLevel.DEBUG, "GC check that dir exists: " + item.getPath() + " owner: " + item.getUsernameOwner());
					result = SystemEnvironment.checkDirExists(item.getPath(),item.getUsernameOwner(),null); 
				}
				else{
					PadFsLogger.log(LogLevel.DEBUG, "GC check that file exists: " + item.getPath() + " owner: " + item.getUsernameOwner());
					result = SystemEnvironment.checkFileExists(item.getPath(),item.getUsernameOwner());
				}
				
				if(result != null && result == false){
					SqlManager.removeFromDirectoryListing(item);
				}
			
				
			}
		}
		
	}

	
	/**
	 * foreach file or dir managed on this server
	 *   - retrieve the list of servers managing the parent directory of that file
	 *   - ask them if they are still managing the directory
	 *   - collect responses and delete the file managed if it is garbage (because the directory was deleted).
	 *   	- the deletion cannot be done without executing a consensus foreach file to be deleted.
				a possible scenario of error is this:
				 # a server delete a directory
				 # after that a server run the GarbageCollector and delete all its managed files inside that directory
				 # after that a user create again a new directory with the same name
				 # after that another server run the GarbageCollector and do not delete its managed files.
				 status corrupted.
				 
			- NOTE: this cannot occur if foreach file to be deleted we execute the consensus BUT
			      the dirDeletion and dirCreation (with the same name) can be so close in time that no servers can detect.
			      In this way it is like that no dirDeletion is occured.
			      
			- to avoid this problem and to avoid the execution of a consensus for each file to be deleted, 
			   each directory must be assigned with a unique id that must distinguish all directories. 
			   This id must be shared among the consensus group managing the directory
			   This id must be assigned at the fileManaged during the prepareOp of the PUT and stored in the DB in the completeOp of the PUT. 
			          In this way it is UNIQUELY referencing the same directory
			          The id can be the the idOp of the operation MKDIR
		
		
	 */
	private void cleanFilesManaged() {
		PadFsLogger.log(LogLevel.DEBUG, "GarbageCollector start -cleanFilesManaged- procedure");
		
		List<MetaInfo> list = SqlManager.getManagedFiles();
		
		PadFsLogger.log(LogLevel.DEBUG, "list of metainfo. size: "+list.size());
		
		Iterator<MetaInfo> it = list.iterator();
		while(it.hasNext() && getExitingRequested() == false){
			MetaInfo file = it.next();
			
			PadFsLogger.log(LogLevel.DEBUG, "checking that parent of "+file.getPath()+" is still managed");
			String parentDirectory = SystemEnvironment.getParentPath(file.getPath());
			if(parentDirectory!= null && !parentDirectory.equals("")){
				Boolean res = SystemEnvironment.checkDirExists(parentDirectory, 
															SqlManager.getUsername(file.getIdOwner()), 
															file.getChecksumParent()); 
				if(res == null){
					PadFsLogger.log(LogLevel.DEBUG, "cannot check if dir is still managed");
				}
				else{
					if(res == false){
						PadFsLogger.log(LogLevel.DEBUG, "the file or dir '"+file.getPath()+"' of '"+file.getIdOwner()+"' is no more managed");
						if(!SqlManager.deleteManagedFile(file.getIdOwner(), file.getPath())){
							PadFsLogger.log(LogLevel.ERROR, "impossible to delete file from the DB");
						}
						if(!Variables.getMerkleTree().removeFile(file.getLabel(), file.getUpdatesNumber(), file.getPath(), Long.valueOf(file.getSize()), file.getIdOwner())){
							PadFsLogger.log(LogLevel.ERROR, "impossible to delete file from the Merkle tree");
						}
					}
					else{
						PadFsLogger.log(LogLevel.TRACE, "file or dir  is still managed"); 
					}
				}
			}
			
			
		}
	}

	/**
	 * foreach file hosted on this server
	 *   - retrieve the list of servers managing this file
	 *   - ask them if they are still managing it
	 *   - collect responses and delete the file hosted if it is garbage.
	 */
	private void cleanFilesHosted(){
			PadFsLogger.log(LogLevel.DEBUG, "GarbageCollector start -cleanFilesHosted- procedure");
			// read filesHosted list from DB
			List<HostedFile> list = SqlManager.getHostedFiles();
			
			
			
			// foreach file, check if it exists at least one server that have to manage the metaInfo of this file 				Iterator<> it = list.iterator();
			
			
			for(HostedFile file : list){
				if(getExitingRequested() ){
					PadFsLogger.log(LogLevel.TRACE, "exiting requested");
					return;
				}
				/*
				 * check if metaInfo exists
				 * 		if (metaInfo do NOT exists OR it exists but I'm not in the hostersList ) in any server AND all servers have answered correctly
				 * 		then remove the physical file
				 */
				
				int idUser 			= file.getIdOwner();
				String logicalPath 	= file.getLogicalPath();
				String checksum		= file.getChecksum();
				PadFsLogger.log(LogLevel.TRACE, "selected file "+idUser+" "+logicalPath+" "+checksum); 
				
				
				List<Server> servList = file.getMetaInfoServers();
				if(servList == null){
					PadFsLogger.log(LogLevel.ERROR, "serverList can't be null");
					continue;
				}
				if(servList.size() < Constants.replicaNumber){
					PadFsLogger.log(LogLevel.WARNING, "serverList is lower than replicaNumber");
				}
				
				boolean atLeastOneFound  = false;
				boolean atLeastOneFailed = false;
				for(Server s : servList){
					if(getExitingRequested() ){
						PadFsLogger.log(LogLevel.TRACE, "exiting requested");
						return;
					}
				
					
					RestIsManaged isManaged = RestServer.isManaged(s,file);
					
					if(isManaged != null ){
						
						if(isManaged.getStatus() == Rest.status.ack && isManaged.getHosters().contains(Variables.getServerId()) ){
							PadFsLogger.log(LogLevel.TRACE, "at least one found");
							atLeastOneFound = true;
							break;
						}
						else if(isManaged.getStatus() == Rest.status.error){
							PadFsLogger.log(LogLevel.TRACE, "at least one failed"); 
							atLeastOneFailed = true;
							break;
						}
					}
					else{
						PadFsLogger.log(LogLevel.TRACE, "at least one failed");
						atLeastOneFailed = true;
						break;
					}
				
				}
				
				if(atLeastOneFound || (atLeastOneFailed && servList != null && servList.size() > 0)){
					;
					/*
					 * nothing to do because it exists a server that hosts metaInfo of this file or it exists one server not reachable
					 */
					
					
					/* if atLeastOneFound and if the file is flagged as "uploading", remove the flag because the upload is completed successfully */
					if(atLeastOneFound && file.getUploadingFlag()){
						if(!SqlManager.unflagHostedFile(file.getIdOwner(), file.getLogicalPath(), file.getChecksum())){
							PadFsLogger.log(LogLevel.WARNING, "cannot unflag file: "+file.getIdOwner()+" - "+file.getLogicalPath()+ " - "+file.getChecksum());
						}
						else{
							PadFsLogger.log(LogLevel.TRACE, "file unflagged: "+file.getIdOwner()+" - "+file.getLogicalPath()+ " - "+file.getChecksum()); 
						}
						/*
						 * 
						 * TODO in the case of a delServer, do we need to flag its files until a synchronization is not complete?
						 */
					}
					
					
				}
				else{
					/* check that the file is not flagged */
					boolean isGarbage = false;
					if(!file.getUploadingFlag()){
						isGarbage = true;
					}
					
					/* check if the file is flagged and the time passed from its flag is higher than a treshold  */
					if(file.getUploadingFlag()){
						SimpleDateFormat format = new SimpleDateFormat(SqlManager.getDateFormat());
						ParsePosition position = new ParsePosition(0);
						format.setLenient(false);
						Date dateFile;
						try{
							dateFile = format.parse(file.getDateTime(),position);
						}
						catch(Exception e){
							PadFsLogger.log(LogLevel.ERROR, "Wrong date-format: "+e.getMessage());
							dateFile = new Date(); //prevent to delete flagged files if we do not have information on the date 
						}
						if (position.getIndex() != file.getDateTime().length()) {
						    PadFsLogger.log(LogLevel.ERROR, "Wrong date-format");
						}
						
						Date dateNow = new Date();
						long difference = dateNow.getTime() - dateFile.getTime(); 
						if(difference > Variables.getMaxTimeMantainUploadingFlag()){
							PadFsLogger.log(LogLevel.DEBUG, "the file is flagged from more than maxTimeMantainFlaggedFile: "+idUser+" "+logicalPath+" "+checksum);
							PadFsLogger.log(LogLevel.TRACE, "dateNow - dateFile = "+difference  +" <= "+Variables.getMaxTimeMantainUploadingFlag() + " --- "+dateNow.getTime()+" / "+dateFile.getTime() + " :: "+file.getDateTime());
							isGarbage = true;
						}
						
					}
					
					if(isGarbage){	
						PadFsLogger.log(LogLevel.DEBUG, "the file is now garbage: "+idUser+" "+logicalPath+" "+checksum);
						LocalFsManager.deletePhysicalFile(idUser, logicalPath, checksum);
					}
				}
				
			}
		
	}
	

}
