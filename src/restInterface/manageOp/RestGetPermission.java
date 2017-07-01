package restInterface.manageOp;


import com.fasterxml.jackson.annotation.JsonProperty;

import system.SystemEnvironment.Constants.Permission;
import system.SystemEnvironment.Constants.Rest;

public class RestGetPermission extends RestManageOp{
	final Permission permission;
	final Long globalConsRunId;
	final Long groupConsRunId;
	final Rest.errors error;
	final Rest.status status;

	public RestGetPermission(@JsonProperty("error") Rest.errors error, 
							 @JsonProperty("permission") Permission permission, 
							 @JsonProperty("globalConsRunId") Long globalConsRunId, 
							 @JsonProperty("groupConsRunId") Long groupConsRunId){
		this.permission = permission;
		this.globalConsRunId = globalConsRunId;
		this.groupConsRunId = groupConsRunId;
		if(error == null){
			this.status = Rest.status.ok;
		}
		else{
			this.status = Rest.status.error;
		}
		this.error = error;
	}
	
	public Rest.errors getError(){
		return error;
	}
	
	
	public Rest.status getStatus(){
		return status;
	}
	
	public Permission getPermission(){
		return permission;
	}
	
	public Long getGlobalConsRunId(){
		return globalConsRunId;
	}
	public Long getGroupConsRunId(){
		return groupConsRunId;
	}
	
}
