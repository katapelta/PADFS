package restInterface.op;

import com.fasterxml.jackson.annotation.JsonProperty;
import system.SystemEnvironment.Constants.Rest;

public class RestDeldirResponse extends RestOp {
	Rest.status status;
	String path = null;
	Rest.errors error = null;

	public RestDeldirResponse(@JsonProperty("status") Rest.status status,
							  @JsonProperty("path") String path,
							  @JsonProperty("error") Rest.errors error){
		this.status 				= status;
		this.error  				= error;
		this.path					= path;
	}
	
	public Rest.status getStatus(){
		return status;
	}
	
	public String getPath(){
		return path;
	}

	public Rest.errors getError(){
		return this.error;
	}
	
	public String getErrorDescription(){
    	if(this.error != null)
    		return this.error.toString();
    	return null;
	}
	
	
}
