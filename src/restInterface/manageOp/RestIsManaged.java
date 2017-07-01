package restInterface.manageOp;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import system.SystemEnvironment.Constants.Rest;

public class RestIsManaged extends RestManageOp{
	Rest.status status;
	List<Long> hosters;
	Rest.errors error;
	
	/*public RestTransfer(
			 Rest.status status){
		this.status = status;
	}*/
	
	
	public RestIsManaged(@JsonProperty("status") Rest.status status, 
						@JsonProperty("hosters") List<Long> hosters,
			 			@JsonProperty("error")Rest.errors error){
		this.status = status;
		this.error = error;
		this.hosters = hosters;
	}
	
	public Rest.status getStatus(){
		return status;
	}
	
	public Rest.errors getError(){
		return error;
	}
	
	public List<Long> getHosters(){
		return hosters;
	}
}
