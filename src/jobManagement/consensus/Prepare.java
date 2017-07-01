package jobManagement.consensus;

import org.springframework.web.context.request.async.DeferredResult;

import jobManagement.jobOperation.JobOperation;
import restInterface.consensus.ProposalNumber;
import restInterface.consensus.RestReply;
import system.SystemEnvironment.Constants.Rest;

public class Prepare extends JobConsMsg<RestReply>{
	
	
	public Prepare(DeferredResult<RestReply> defResultMsg, String op, ProposalNumber proposalNumber, String password, long idConsensusRun, String opType){
		super(op,opType,proposalNumber,idConsensusRun,password,defResultMsg);		
	}
	
	public boolean answer(Rest.status ack, JobOperation op, ProposalNumber proposalNumber){
		return answer(ack,op,proposalNumber,null);
	}
	
	public boolean answer(Rest.status ack, JobOperation op, ProposalNumber proposalNumber, ProposalNumber highestProposalNumberSeen){
		try{
			this.getDeferredResult().setResult( 
				new RestReply(ack, proposalNumber, op, this.getIdConsRun(), highestProposalNumberSeen)
				);
			return true;
		}
		catch(Exception e){
			return false;
		}
	}
	
	public boolean answer(Rest.status status, long idConsRun){
		try{
			this.getDeferredResult().setResult(	new RestReply(status,idConsRun) );
			return true;
		}
		catch(Exception e){
			return false;
		}
	}
	
	public boolean answer(Rest.status ack){
		try{
			this.getDeferredResult().setResult(	new RestReply(ack) );
			return true;
		}
		catch(Exception e){
			return false;
		}
	}


}
