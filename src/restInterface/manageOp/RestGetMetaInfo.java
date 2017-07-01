package restInterface.manageOp;


import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import system.SystemEnvironment.Constants;
import system.SystemEnvironment.Variables;
import system.containers.MetaInfo;
import system.managers.SqlManager;

public class RestGetMetaInfo extends RestManageOp{
	long consRunId = 0;
	List<MetaInfo> metaInfoList = null;
	
	@JsonIgnore
	public RestGetMetaInfo(long startLabel, long endLabel){
		
		this.consRunId = Variables.consensusVariableManager.getConsVariables(Constants.globalConsensusGroupId).getIdConsRun(); 
		this.metaInfoList = SqlManager.getMetaInfo(startLabel,endLabel);
		
	}
	
	@JsonCreator
	public RestGetMetaInfo(@JsonProperty("metaInfoList") List<MetaInfo> metaInfoList,
						   @JsonProperty("consRunId") long consRunId ){
		this.consRunId = consRunId;
		this.metaInfoList = metaInfoList;
	}
	
	public List<MetaInfo> getMetaInfoList(){
		return metaInfoList;
	}
	
	public long getConsRunId(){
		return consRunId;
	}
	
}
