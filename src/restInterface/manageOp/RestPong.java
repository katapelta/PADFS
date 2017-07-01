package restInterface.manageOp;


import com.fasterxml.jackson.annotation.JsonProperty;

import system.SystemEnvironment;
import system.SystemEnvironment.Constants.Rest;
import system.SystemEnvironment.Variables;

public class RestPong extends RestManageOp{
	long idConsRun = 0;
	Long idServer  = null;
	boolean isNetworkStarting = false;

	final Rest.status status;
	final Rest.errors error;

	public RestPong(@JsonProperty("status") Rest.status status,
					@JsonProperty("idConsRun") Long idConsRun,
					@JsonProperty("error") Rest.errors error
					){
		this.idConsRun = idConsRun;
		this.idServer  = SystemEnvironment.Variables.getServerId();
		this.isNetworkStarting = Variables.isNetworkStarting();
		
		this.status = status;
		this.error = error;
	}
	
	public Long getIdConsRun(){
		return idConsRun;
	}
	
	public Long getIdServer(){
		return idServer;
	}
	
	public Rest.errors getError(){
		return error;
	}
	
	public Rest.status getStatus(){
		return status;
	}

	public boolean getIsNetworkStarting(){
		return this.isNetworkStarting;
	}
}
