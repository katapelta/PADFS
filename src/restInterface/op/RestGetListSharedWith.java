package restInterface.op;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import system.SystemEnvironment.Constants.Rest;
import system.containers.MetaInfo;

import java.util.List;


public class RestGetListSharedWith extends RestOp {

    private final Rest.status status;
    private final Rest.errors error;
    private final List<MetaInfo> fileList;

    @JsonCreator
    public RestGetListSharedWith(@JsonProperty("status") Rest.status status,
                                 @JsonProperty("error") Rest.errors error,
                                 @JsonProperty("fileList") List<MetaInfo> fileList   ) {
        this.status = status;
        this.error = error;
        this.fileList = fileList;
    }

    public Rest.status getStatus() {
        return status;
    }
    
    public List<MetaInfo> getFileList(){
    	return fileList;
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