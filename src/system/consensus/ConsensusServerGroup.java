package system.consensus;

import java.util.Iterator;
import java.util.List;

import system.SystemEnvironment.Constants;
import system.containers.Server;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;
import system.managers.SqlManager;

public class ConsensusServerGroup {
	private List<Server> serverList = null;
	private boolean isGlobal=false;
	
	public ConsensusServerGroup(long[] serversId){
		
		serverList = SqlManager.getServerList(serversId);

	}
	
	public ConsensusServerGroup(long[] serversId,boolean isGlobal){
		this.isGlobal = isGlobal;
		serverList = SqlManager.getServerList(serversId);

	}
	
	public ConsensusServerGroup(List<Server> servList) {
		serverList = servList;
	}
	
	public ConsensusServerGroup(List<Server> servList, boolean isGlobal) {
		this.isGlobal = isGlobal;
		serverList = servList;
	}

	public Iterator<Server> iterator() {
		return serverList.iterator();
	}
 
	public long[] getIdList(){
		long [] ret = new long[serverList.size()];
		
		for(int i=0;i<ret.length;i++){
			ret[i]=serverList.get(i).getId();
		}
		
		return ret;
	}
	
	/**
	 * Retrieve from Database the consGroupId of the server group
	 * if it does not exists, it creates it
	 * @return the groupId
	 */
	public Long getConsensusGroupId() {
		return getConsensusGroupId(true);
	}

	/**
	 * Retrieve from Database the consGroupId of the server group
	 * if it does not exists and forceCreation is set to true, it creates it
	 * 
	 * @return the groupId or null
	 */
	public Long getConsensusGroupId(boolean forceCreation) {
		if(isGlobal)
			return Constants.globalConsensusGroupId;
		
		
		long []serverIdList = getIdList();
		if(serverIdList != null) {

			//convert long[] to string[] to print it
 			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < serverIdList.length; i++) {
				sb.append(serverIdList[i]);
				if (i != serverIdList.length - 1) {
					sb.append(",");
				}
			}
			PadFsLogger.log(LogLevel.DEBUG, "idList: " + sb.toString());
		
			Long consGroupId = SqlManager.getConsGroupId(serverIdList);

			if(consGroupId==null && forceCreation){
				consGroupId = SqlManager.addConsGroup(serverIdList);
				PadFsLogger.log(LogLevel.DEBUG, "created new serverGroup with id = "+consGroupId);
			}
			return consGroupId;
		}else{
			PadFsLogger.log(LogLevel.ERROR, "idList: NULL");
			return null;
		}

	}

	public int size() {
		if(serverList == null)
			return 0;
		return serverList.size();
	}
	
	
	
	public void printServerList(){
		Iterator<Server> i = this.iterator();
		while(i.hasNext()){
			Server server = i.next();
			PadFsLogger.log(LogLevel.DEBUG,"  ---------   consensusGroupServerList -> "+server.getIp()+":"+server.getPort());
		}
	}
	
	
	
}
