package restInterface.op;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import system.SystemEnvironment.Constants.Rest;


public class RestPut extends RestOp {

    private final Rest.status status;
    private final String name;
    private final Rest.errors error;

    @JsonCreator
    public RestPut(@JsonProperty("status") Rest.status status,
    			   @JsonProperty("name") String name,
    			   @JsonProperty("error") Rest.errors error) {
        this.status = status;
        this.name = name;
        this.error = error;
    }

    public Rest.status getStatus() {
        return status;
    }
    
    public String getName() {
        return name;
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