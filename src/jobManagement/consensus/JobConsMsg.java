package jobManagement.consensus;

import org.springframework.web.context.request.async.DeferredResult;

import jobManagement.Job;
import jobManagement.jobOperation.JobOperation;
import restInterface.consensus.ProposalNumber;
import system.SystemEnvironment.Constants.Rest;
import system.SystemEnvironment.Variables;
import system.consensus.ConsensusServerGroup;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;

public abstract class JobConsMsg<T> extends Job {
	private ProposalNumber proposalNumber;
	private long idConsRun;
	private JobOperation op;
	private DeferredResult<T> defResult;
	private String password;
	


	//costruttore chiamato da RestPadfsController
	public JobConsMsg(String op,String opType, ProposalNumber proposalNumber, long idConsRun, String password, DeferredResult<T> defResult){
		this.op = JobOperation.createFromJson(op, opType);
	 	
		this.proposalNumber = proposalNumber;
		this.idConsRun = idConsRun;
		
		this.defResult = defResult;
		this.password = password;
		
	}

	public ProposalNumber getProposalNumber(){
		return proposalNumber;
	}
	
	public final JobOperation getOp(){
		return op;
	}
	
	public final long getIdConsRun(){
		return idConsRun;
	}

	public final DeferredResult<T> getDeferredResult(){
		return defResult;
	}

	public ConsensusServerGroup consensusGroupServerList() {		
		JobOperation op = this.getOp();
		if(op==null){
			PadFsLogger.log(LogLevel.ERROR, "jobOperation is null");
			return null;
		}
		return op.consensusGroupServerList();
	}
	
	public boolean checkPassword(){
		if(password.equals(Variables.getServerPassword()) ) 
			return true;
		return false;
	}

	public abstract boolean answer (Rest.status status,long  idConsRun);
	public abstract boolean answer (Rest.status status);
}
