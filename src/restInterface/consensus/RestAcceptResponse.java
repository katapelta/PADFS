package restInterface.consensus;

import system.SystemEnvironment.Constants.Rest;

import com.fasterxml.jackson.annotation.JsonProperty;


public class RestAcceptResponse extends RestConsensus {

	private final Rest.status ack;
	private final Long idConsRun;
	
	   public RestAcceptResponse(@JsonProperty("ack") Rest.status ack,@JsonProperty("idConsRun") Long idConsRun) {
	        this.ack = ack;
	        this.idConsRun = idConsRun;
	        
	    }
	   
	   public RestAcceptResponse(Rest.status ack) {
	        this.ack = ack;
	        this.idConsRun = null;
	        
	    }
       

    public Rest.status getAck() {
        return ack;
    }
	
	public Long getIdConsRun() {
        return idConsRun;
    }
    
   
}