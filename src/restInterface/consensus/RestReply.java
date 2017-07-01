package restInterface.consensus;

import jobManagement.jobOperation.JobOperation;
import system.SystemEnvironment.Constants.Rest;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

public class RestReply extends RestConsensus {

	private final Rest.status ack;
	private final String op;
	private final String opType;
	private final ProposalNumber proposalNumber;
	private final ProposalNumber highestProposalNumberSeen;
	private final Long idConsensusRun;
    
	
	public RestReply(
			@JsonProperty("ack") Rest.status ack, 
			@JsonProperty("proposalNumber")  ProposalNumber proposalNumber, 
			@JsonProperty("operation")  JobOperation operation, 
			@JsonProperty("idConsensusRun")  Long idConsensusRun,
			@JsonProperty("highestProposalNumber")  ProposalNumber highestProposalNumberSeen
			
			) {
		this.ack 			= ack;
		this.op  			= (operation==null)?null:operation.toJSON();
        this.proposalNumber = proposalNumber;
        this.idConsensusRun = idConsensusRun;
        this.opType			= (op==null)?null:op.getClass().toString();
        this.highestProposalNumberSeen = highestProposalNumberSeen;
	}
	
	
	public RestReply(
			 Rest.status ack, 
			 long idConsensusRun
			) {
		this.ack 			= ack;
		this.op  			= null;
        this.proposalNumber = null;
        this.idConsensusRun = idConsensusRun;
        this.opType			= null;
        this.highestProposalNumberSeen = null;
	}
	
	public RestReply(
			 Rest.status ack
			) {
		this.ack 			= ack;
		this.op  			= null;
        this.proposalNumber = null;
        this.idConsensusRun = null;
        this.opType			= null;
        this.highestProposalNumberSeen = null;
	}

	public Rest.status getAck() {
        return ack;
    }
    
    public String getOperation() {
        return op;
    }
    
    public ProposalNumber getProposalNumber() {
        return proposalNumber;
    }
    
    public ProposalNumber getHighestProposalNumber() {
        return highestProposalNumberSeen;
    }

	public Long getIdConsensusRun() {
		return idConsensusRun;
	}
	
	public String getOpType(){
		return opType;
	}
	
	public JobOperation objectOperation(){
		return JobOperation.createFromJson(op, opType);
	}
	
	public String toJSON(){
		ObjectWriter writer = new ObjectMapper().writer();
		try {
			return writer.writeValueAsString(this);
		} catch (JsonProcessingException e) {
			PadFsLogger.log(LogLevel.ERROR,"[toJSON]: " + e.getMessage());
			e.printStackTrace();
		}
		return null;
	}
}