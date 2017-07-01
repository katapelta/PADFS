package restInterface.manageOp;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import system.SystemEnvironment.Constants.Rest;

public class RestIsDirManaged extends RestManageOp{
	Rest.status status;
	List<Long> hosters;
	Rest.errors error;
	String uniqueId;
	
	/*public RestTransfer(
			 Rest.status status){
		this.status = status;
	}*/
	
	
	public RestIsDirManaged(@JsonProperty("status") Rest.status status, 
						@JsonProperty("hosters") List<Long> hosters,
						@JsonProperty("uniqueId") String uniqueId,
			 			@JsonProperty("error")Rest.errors error){
		this.status = status;
		this.error = error;
		this.hosters = hosters;
		this.uniqueId = uniqueId;
	}
	
	public Rest.status getStatus(){
		return status;
	}
	
	public Rest.errors getError(){
		return error;
	}
	
	public String getUniqueId(){
		return uniqueId;
	}
	
	public List<Long> getHosters(){
		return hosters;
	}
}
