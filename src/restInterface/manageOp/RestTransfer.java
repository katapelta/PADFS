package restInterface.manageOp;

import com.fasterxml.jackson.annotation.JsonProperty;

import system.SystemEnvironment.Constants.Rest;

public class RestTransfer extends RestManageOp{
	Rest.status status;
	Rest.errors error;
	
	/*public RestTransfer(
			 Rest.status status){
		this.status = status;
	}*/
	
	
	public RestTransfer(@JsonProperty("status") Rest.status status, 
			 			@JsonProperty("error")Rest.errors error){
		this.status = status;
		this.error = error;
	}
	
	public Rest.status getStatus(){
		return status;
	}
	
	public Rest.errors getError(){
		return error;
	}
}
