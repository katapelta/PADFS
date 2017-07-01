package jobManagement.consensus;

import org.springframework.web.context.request.async.DeferredResult;

import restInterface.consensus.ProposalNumber;
import restInterface.consensus.RestProposeResponse;
import system.SystemEnvironment.Constants.Rest;

public class Propose extends JobConsMsg<RestProposeResponse>{

	
	public Propose(DeferredResult<RestProposeResponse> defResultMsg, String op, ProposalNumber proposalNumber, String password, long idConsensusRun, String opType){
		super(op,opType,proposalNumber,idConsensusRun,password,defResultMsg);
	}
	
	
	public boolean answer(Rest.status ack, long idConsRun){
		try{
			this.getDeferredResult().setResult( new RestProposeResponse(ack,idConsRun) );
			return true;
		}
		catch(Exception e){
			return false;
		}
			
	}
	
	public boolean answer(Rest.status ack){
		try{
			this.getDeferredResult().setResult( new RestProposeResponse(ack) );
			return true;
		}
		catch(Exception e){
			return false;
		}
			
	}
	
	
}
