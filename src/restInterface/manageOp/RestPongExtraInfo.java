package restInterface.manageOp;


import com.fasterxml.jackson.annotation.JsonProperty;

import system.SystemEnvironment.Constants.ServerStatus;
import system.SystemEnvironment.Variables;

public class RestPongExtraInfo extends RestManageOp{
	long idConsRun = 0;
	Long idServer  = null;
	boolean isNetworkStarting = false;
	String availableSpace = null;
	String totalSpace = null;
	ServerStatus serverStatus = null;

	public RestPongExtraInfo(@JsonProperty("idConsRun") long idConsRun){
		this.idConsRun = idConsRun;
		this.idServer  = Variables.getServerId();
		this.isNetworkStarting = Variables.isNetworkStarting();
		this.availableSpace = Variables.getAvailableSpace();
		this.totalSpace = Variables.getTotalSpace();
		this.serverStatus = Variables.getServerStatus();
	}
	
	public long getIdConsRun(){
		return idConsRun;
	}
	
	public Long getIdServer(){
		return idServer;
	}

	public ServerStatus getServerStatus() { return serverStatus; }
	
	public String  getAvailableSpace() { return  availableSpace; }

	public String getTotalSpace (){return totalSpace; }

	public boolean getIsNetworkStarting(){
		return this.isNetworkStarting;
	}
}
