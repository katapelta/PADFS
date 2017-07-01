package system.consensus;

import java.util.HashMap;

import system.SystemEnvironment.Constants;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;

public class ConsensusVariablesManager {
	private HashMap<Long, ConsensusVariables> cvm = new HashMap<>();
	
	public ConsensusVariablesManager(){}
	
	public ConsensusVariables getConsVariables(Long consGroupId){
		
		ConsensusVariables ret = null;
		if(consGroupId == null) {
			PadFsLogger.log(LogLevel.ERROR, "consGroupId is null");
			return null;
		}
		
		
		if( (ret=cvm.get(consGroupId)) != null){
			return ret;
		}else{
			ret = new ConsensusVariables(consGroupId);
			cvm.put(consGroupId, ret);
			return ret;
		}
	}
	
	

	public void initializeGlobalConsensusVariables() {
		ConsensusVariables consVar = new ConsensusVariables(Constants.globalConsensusGroupId);
		cvm.put(Constants.globalConsensusGroupId,consVar);
		
	}
	
}
