package restInterface.op;

import com.fasterxml.jackson.annotation.JsonProperty;

import system.SystemEnvironment.Constants.Rest;

public class RestMaintenanceRequest extends RestOp {
	Rest.status status;
	Rest.errors error = null;

	public RestMaintenanceRequest(@JsonProperty("status") Rest.status status,
							 @JsonProperty("error") Rest.errors error){
		this.status 				= status;
		this.error  				= error;
	}
	
	public Rest.status getStatus(){
		return status;
	}
	
	
	 public Rest.errors getError() {
	        return error;
	 }
	    
	 public String getErrorDescription(){
	    	if(this.error != null)
	    		return this.error.toString();
	    	return null;
		}
}
