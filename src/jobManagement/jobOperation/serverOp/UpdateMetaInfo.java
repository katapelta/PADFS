package jobManagement.jobOperation.serverOp;

import java.util.Iterator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jobManagement.ForwardableInterface;
import system.SystemEnvironment;
import system.SystemEnvironment.Constants.OperationPriority;
import system.SystemEnvironment.Constants.Rest;
import system.consensus.ConsensusServerGroup;
import system.containers.MetaInfo;
import system.containers.Server;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;
import system.managers.SqlManager;

public class UpdateMetaInfo extends JobServerOp implements ForwardableInterface{
	private MetaInfo file;
		
	private String path;
	private String user;
	
	
	public UpdateMetaInfo(MetaInfo file) { 
		super(OperationPriority.UPDATE_META_INFO);
		
		this.file = file;
		
		this.path = file.getPath();
		this.user = SqlManager.getUsername(file.getIdOwner());
		
	}
	
	@JsonCreator
	public UpdateMetaInfo(  @JsonProperty("idOp") String idOp,
							@JsonProperty("file") MetaInfo file,
					    	@JsonProperty("idConsRun") Long idConsRun){
		
		super(idOp,OperationPriority.UPDATE_META_INFO,idConsRun);
		
		this.file = file;
		this.path = file.getPath();
		this.user = SqlManager.getUsername(file.getIdOwner());
		
	}
	

	public MetaInfo getFile(){
		return file;
	}

	
	
	
	@Override
	public boolean prepareOp() {

		return true;
	}
	
	

	@Override
	public boolean completeOp() {
		
		/*
		 * this operation is executed to update the replicas of a file.
		 * From the time it is created to the time it is approved by the consensusGroup, others operations can be occurred.
		 * 	 if another PUT with the same <idUser,path> has overwritten this file
		 * 	 OR if a DELETE has removed this file
		 * 		we won't execute the completeOp()
		 */
		
		
		//check that the metaInfo still exists
		Integer idFile = SqlManager.getIdFile(path, file.getIdOwner());
		if(idFile == null || idFile < 0){
			PadFsLogger.log(LogLevel.DEBUG, "metaInfo does not exists anymore. nothing to update");
			return true;
		}
		
		//check the updates number: if it is the same as the one inside this.file, than no other PUT is executed on this file
		/*
		 *  possible problem:  DO NOT DELETE this comment. it is referenced in another comment in this file!
		 *  
		 * 	1 user scenario
		 *  
		 *  t0.
		 *  t1. PUT file.txt   -->  MetaInfo{ path = "file.txt", updatesNumber=1, ....}
		 *  t2. 
		 *  t3. REMOVE file.txt   -->  No MetaInfo
		 *  t4. 
		 *  t5. PUT file.txt   -->  MetaInfo{ path = "file.txt", updatesNumber=1, ....}
		 *  t6.
		 *  
		 *  if an UpdateMetaInfo is created at time t2 and it is accepted by the Consensus at time t6, 
		 *  	it will update the metaInfo with wrong information. 
		 *  	The new version of the file is now stored in the servers defined by the put executed in t5
		 *  
		 *  FIXED, checksum comparison inserted instead of updatesNumber comparison
		 *  
		 */
		
		
		MetaInfo StoredFile = SqlManager.getMetaInfo(idFile);
		//if(file.getUpdatesNumber() == StoredFile.getUpdatesNumber()){
		if(file.getChecksum().equals(StoredFile.getChecksum())){
				/*
			 * NOTE: did not write >= because if this server has lost some message, 
			 * 		 it can not execute this method because the consensus would have stopped and started a synchronization
			 * 		 
			 * 		 instead it can be a different updatesNumber as in the above depicted scenario 
			 * 		 but with this sequence of operations on the same file
			 * 		 PUT,PUT,constructor UpdateMetaInfo,REMOVE,PUT,completeOp UpdateMetaInfo
			 * 		
			 */
			
			
			if(SqlManager.updateMetaInfoNoPermission(file.getIdOwner(),file.getPath(),file.getHostersId())){
				return true;
			}
			else{
				PadFsLogger.log(LogLevel.ERROR, "impossible to update MetaInfo of file '"+file.getPath()+"'");
				return false;
			}
			
		}
		
		PadFsLogger.log(LogLevel.ERROR, "MetaInfo of file '"+file.getPath()+"' is of a different version. nothing to do");
		return true;
			
	}


	@Override
	public void replyOperationCompleted() { ; }


	@Override
	public void replyError(Rest.errors message) { ; }

	

	/**
	 * Return the list of servers of the group that have to manage the 
	 * consensus problem
	 * 
	 * @return null|ServerList 
	 */
	@Override
	public final ConsensusServerGroup consensusGroupServerList(){
		long serverLabel;
		long[] serverIds;
		
		
		serverLabel = getServerLabel();
		serverIds = SqlManager.getIdFromConsensusLabel(serverLabel);
			
		ConsensusServerGroup serverList = new ConsensusServerGroup(serverIds);		
				
		return serverList;
	}
	
	@JsonIgnore
	private long getServerLabel(){
		return SystemEnvironment.getLabel(this.user,this.path);
	}


	public boolean forward(Server s){
		//TODO implement this
		PadFsLogger.log(LogLevel.ERROR, "TODO forward function is still to implement");
		return false;
	}
	
	@JsonIgnore
	@Override
	public boolean isForwardingNeeded(){
		return true;
	}
	
	@Override
	public boolean actAsAProxy() {
		Iterator<Server> it = consensusGroupServerList().iterator();
		
		
		while(it.hasNext()){
			Server s = it.next();
			if(this.forward(s)){
				return true;
			}
			else{
				PadFsLogger.log(LogLevel.WARNING, "failed to forward to server "+s.getId());
			}
		}
		return false;
		
	}
	
}
