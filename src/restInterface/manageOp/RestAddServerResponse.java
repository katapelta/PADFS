package restInterface.manageOp;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import system.SystemEnvironment.Constants.Rest;
import system.containers.Server;

public class RestAddServerResponse extends RestManageOp {
	Rest.status status;
	List<Server> serverConfiguration;
	Long idServer = null;
	Rest.errors error = null;
	
	public RestAddServerResponse(@JsonProperty("status") Rest.status status,
								 @JsonProperty("serverConfiguration") List<Server> serverConfiguration,
								 @JsonProperty("idServer") Long idServer,
								 @JsonProperty("error") Rest.errors error){
		this.serverConfiguration 	= serverConfiguration;
		this.status 				= status;
		this.error  				= error;
		this.idServer				= idServer;
	}
	
	public Rest.status getStatus(){
		return status;
	}
	
	public Long getIdServer(){
		return idServer;
	}
	
	public List<Server> getServerConfiguration(){
		return serverConfiguration;
	}
	
	public Rest.errors getError(){
		return this.error;
	}
	
}
