package restInterface.op;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import system.SystemEnvironment.Constants.Rest;


public class RestList extends RestOp {

    private final Rest.status status;
    private final String name;
    private final Rest.errors error;
    private final List<Map<String,String>> pathList;

    @JsonCreator
    public RestList(@JsonProperty("status") Rest.status status, 
    				@JsonProperty("name") String name, 
    				@JsonProperty("error") Rest.errors error, 
    				@JsonProperty("pathList") List<Map<String,String>> pathList) {
        this.status = status;
        this.name = name;
        this.error = error;
        this.pathList = pathList;
    }

    public Rest.status getStatus() {
        return status;
    }
    
    public List<Map<String,String>> getPathList(){
    	return pathList;
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