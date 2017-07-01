package restInterface.manageOp;


import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import system.SystemEnvironment.Constants;
import system.SystemEnvironment.Variables;
import system.containers.DirectoryListingItem;
import system.managers.SqlManager;

public class RestGetDirectoryListing extends RestManageOp{
	long consRunId = 0;
	List<DirectoryListingItem> directoryList = null;
	
	@JsonIgnore
	public RestGetDirectoryListing(long startLabel, long endLabel){
		
		this.consRunId = Variables.consensusVariableManager.getConsVariables(Constants.globalConsensusGroupId).getIdConsRun(); 
		this.directoryList = SqlManager.getDirectoryListing(startLabel,endLabel);
		
	}
	
	@JsonCreator
	public RestGetDirectoryListing(@JsonProperty("directoryList") List<DirectoryListingItem> directoryList,
						   		   @JsonProperty("consRunId") long consRunId ){
		this.consRunId = consRunId;
		this.directoryList = directoryList;
	}
	
	public List<DirectoryListingItem> getDirectoryList(){
		return directoryList;
	}
	
	public long getConsRunId(){
		return consRunId;
	}
	
}
