package restInterface.manageOp;


import com.fasterxml.jackson.annotation.JsonProperty;

import system.SystemEnvironment.Constants;
import system.SystemEnvironment.Variables;
import system.merkleTree.NodeLowerTree;
import system.merkleTree.NodeUpperTree;

public class RestGetLowerTreeResponse extends RestManageOp{
	NodeLowerTree lowerTree = null;
	long consRunId = 0;

	
	public RestGetLowerTreeResponse(
			long startLabel, 
			long endLabel){
		NodeUpperTree rootUpper = Variables.getMerkleTree().getUpperTree();
		this.lowerTree = rootUpper.findLowerTree(startLabel,endLabel);
		this.consRunId = Variables.consensusVariableManager.getConsVariables(Constants.globalConsensusGroupId).getIdConsRun(); 
		
	}
	
	public RestGetLowerTreeResponse(@JsonProperty("lowerTree") NodeLowerTree lowerTree,
									@JsonProperty("consRunId") long consRunId ){
		this.lowerTree = lowerTree;
		this.consRunId = consRunId;
	}
	
	public NodeLowerTree getLowerTree(){
		return lowerTree;
	}
	
	public long getConsRunId(){ //TODO usare questa info in synchGlobal
		return consRunId;
	}
	
}
