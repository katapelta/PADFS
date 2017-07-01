package system.containers;

import com.fasterxml.jackson.annotation.JsonProperty;

import system.SystemEnvironment.Constants;

public class FilePermission{
	private final Constants.Permission permission;
	private final Integer idUser;
	
	public FilePermission(@JsonProperty("idUser") 		Integer idUser, 
					  @JsonProperty("permission")	Constants.Permission permission){
		this.permission = permission;
		this.idUser = idUser;
	}
	
	public Integer getIdUser(){
		return idUser;
	}
	
	public Constants.Permission getPermission(){
		return permission;
	}
}