package restInterface.consensus;

import com.fasterxml.jackson.annotation.JsonProperty;

import system.SystemEnvironment.Constants.Rest;


public class RestProposeResponse extends RestConsensus {

	private final Rest.status ack;
	private final Long idConsRun;
	
	   public RestProposeResponse(@JsonProperty("ack") Rest.status ack,@JsonProperty("idConsRun") Long idConsRun) {
	        this.ack = ack;
	        this.idConsRun = idConsRun;
	        
	    }
	   
	   public RestProposeResponse(Rest.status ack) {
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