package restInterface.manageOp;

import java.util.List;

import system.SystemEnvironment.Constants.Rest;
import system.containers.Server;
import system.containers.User;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RestGlobalSynchResponse extends RestManageOp {
	private Rest.status status;
	private List<Server> serverList;
	private List<User> userList;
	private Rest.errors error = null;
	private long globalConsensusRunId = 0;
	
	private String creatorId;
	private String creatorPort;
	private List<String> creatorIpList;
	
	public RestGlobalSynchResponse(@JsonProperty("status") Rest.status status,
								@JsonProperty("serverList") List<Server> serverList,
								@JsonProperty("userList") List<User> userList,
								@JsonProperty("creatorIpList") List<String> creatorIpList,
								@JsonProperty("creatorPort") String creatorPort,
								@JsonProperty("creatorId") String creatorId,
								 @JsonProperty("globalConsensusRunId") long globalConsensusRunId,
								 @JsonProperty("error") Rest.errors error){
		this.status 	= status;
		this.error  	= error;
		this.serverList	= serverList;
		this.userList 	= userList;
		this.globalConsensusRunId = globalConsensusRunId;
		this.creatorId = creatorId;
		this.creatorIpList = creatorIpList;
		this.creatorPort = creatorPort;
	}
	
	public Rest.errors getError(){
		return this.error;
	}
	
	public Rest.status getStatus(){
		return this.status;
	}

	public List<Server> getServerList() {
		return serverList;
	}
	
	public List<User> getUserList() {
		return userList;
	}
	
	public long getGlobalConsensusRunId() {
		return globalConsensusRunId;
	}

	public String getCreatorPort() {
		return creatorPort;
	}
	
	public String getCreatorId() {
		return creatorId;
	}
	
	public List<String> getCreatorIpList() {
		return creatorIpList;
	}
	

	
}
