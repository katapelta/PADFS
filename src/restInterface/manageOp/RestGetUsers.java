package restInterface.manageOp;


import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import system.SystemEnvironment.Constants.Rest;
import system.containers.User;

public class RestGetUsers extends RestManageOp{
	List<User> userList = new ArrayList<>();

	final Rest.status status;
	final Rest.errors error;

	public RestGetUsers(@JsonProperty("status") Rest.status status,
                        @JsonProperty("userList") List<User> userList,
                        @JsonProperty("error") Rest.errors error
					){
		this.userList = userList;

		this.status = status;
		this.error = error;
	}
	
	public List<User> getListUsers(){
		return userList;
	}
	
	public Rest.errors getError(){
		return error;
	}
	
	public Rest.status getStatus(){
		return status;
	}

}
