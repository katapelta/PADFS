package restInterface.op;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import system.SystemEnvironment.Constants.Rest;


public class RestListHtml extends RestOp {

    private final Rest.status status;
    private final String name;
    private final Rest.errors  error;
    private final String html;

    @JsonCreator
    public RestListHtml(@JsonProperty("status") Rest.status status,
                        @JsonProperty("name") String name,
                        @JsonProperty("error") Rest.errors error,
                        @JsonProperty("html") String html) {
        this.status = status;
        this.name = name;
        this.error = error;
        this.html = html;
    }

    public Rest.status getStatus() {
        return status;
    }
    
    public String getHtml(){
    	return html;
    }
    
    public String getName() {
        return name;
    }
    
    public Rest.errors  getError() {
        return error;
    }
    
    public String  getErrorDescription() {
    	if(error != null)
    		return error.toString();
    	return null;
    }
}