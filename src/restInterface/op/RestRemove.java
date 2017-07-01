package restInterface.op;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import system.SystemEnvironment.Constants.Rest;


public class RestRemove extends RestOp{

    private final Rest.status status;
    private final Rest.errors error;

    @JsonCreator
    public RestRemove(
    		@JsonProperty("status") Rest.status status,
    		@JsonProperty("error")  Rest.errors error) {
        this.status = status;
        this.error = error;
    }

    public Rest.status getStatus() {
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