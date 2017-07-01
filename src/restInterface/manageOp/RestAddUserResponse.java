package restInterface.manageOp;

import com.fasterxml.jackson.annotation.JsonProperty;

import system.SystemEnvironment.Constants.Rest;

public class RestAddUserResponse extends RestManageOp{
	Rest.status status;
	Long idUser = null;
	Rest.errors error = null;

	public RestAddUserResponse(@JsonProperty("status") Rest.status status,
							   @JsonProperty("idUser") Long idUser,
							   @JsonProperty("error") Rest.errors error){
		this.status 				= status;
		this.error  				= error;
		this.idUser					= idUser;
	}
	
	public Rest.status getStatus(){
		return status;
	}
	
	public Long getIdUser(){
		return idUser;
	}

	public Rest.errors getError(){
		return this.error;
	}
	
}
