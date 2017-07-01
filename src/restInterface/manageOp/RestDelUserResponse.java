package restInterface.manageOp;

import com.fasterxml.jackson.annotation.JsonProperty;

import system.SystemEnvironment.Constants.Rest;

public class RestDelUserResponse extends RestManageOp {
	Rest.status status;
	String username = null;
	Rest.errors error = null;

	public RestDelUserResponse(@JsonProperty("status") Rest.status status,
							   @JsonProperty("username") String username,
							   @JsonProperty("error") Rest.errors error){
		this.status 				= status;
		this.error  				= error;
		this.username				= username;
	}
	
	public Rest.status getStatus(){
		return status;
	}
	
	public String getUsername(){
		return username;
	}

	public Rest.errors getError(){
		return this.error;
	}
	
}
