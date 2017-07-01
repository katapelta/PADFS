package restInterface.manageOp;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import system.SystemEnvironment.Constants;
import system.SystemEnvironment.Variables;
import system.merkleTree.NodeUpperTree;

public class RestGetUpperTreeResponse extends RestManageOp{
	NodeUpperTree upperTree = null;
	long consRunId = 0;
	
	public RestGetUpperTreeResponse(){
		this.upperTree = Variables.getMerkleTree().getUpperTree().cloneClean();
		this.consRunId = Variables.consensusVariableManager.getConsVariables(Constants.globalConsensusGroupId).getIdConsRun(); 
	}
	
	@JsonCreator
	public RestGetUpperTreeResponse(@JsonProperty("upperTree") NodeUpperTree upperTree,
									@JsonProperty("consRunId") long consRunId ){
		this.upperTree = upperTree;
		this.consRunId = consRunId;
	}
	
	public NodeUpperTree getUpperTree(){
		return upperTree;
	}
	
	public long getConsRunId(){
		return consRunId;
	}
	
}
