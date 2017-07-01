package jobManagement.consensus;

import org.springframework.web.context.request.async.DeferredResult;

import restInterface.consensus.ProposalNumber;
import restInterface.consensus.RestAcceptResponse;
import system.SystemEnvironment.Constants.Rest;

public class Accept extends JobConsMsg<RestAcceptResponse>{
	
	
	public Accept(DeferredResult<RestAcceptResponse> defResultMsg, String op, ProposalNumber proposalNumber, String password, long idConsensusRun, String opType){
		super(op,opType,proposalNumber,idConsensusRun,password,defResultMsg);
		
	}
	
	
	public boolean answer(Rest.status ack, long idConsRun){
		try{
			this.getDeferredResult().setResult(	new RestAcceptResponse(ack,idConsRun) );
			return true;
		}
		catch(Exception e){
			return false;
		}
	}
	
	public boolean answer(Rest.status ack){
		try{
			this.getDeferredResult().setResult(	new RestAcceptResponse(ack) );
			return true;
		}
		catch(Exception e){
			return false;
		}
	}
	


}
