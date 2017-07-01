package restInterface.consensus;

import com.fasterxml.jackson.annotation.JsonProperty;


public class RestAccept extends RestConsensus {

	private final String ack;
	private final String idOp;
	private final ProposalNumber proposalNumber;
    

    public RestAccept(
    		@JsonProperty("ack") String ack, 
    		@JsonProperty("idOp") String idOp, 
    		@JsonProperty("proposalNumber") ProposalNumber proposalNumber) {
        this.ack = ack;
        this.idOp = idOp;
        this.proposalNumber = proposalNumber;
    }

    public String getAck() {
        return ack;
    }
    
    public String getOperation() {
        return idOp;
    }
    
    public ProposalNumber getProposalNumber() {
        return proposalNumber;
    }
}