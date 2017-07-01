package system.consensus;

import java.util.HashMap;

import jobManagement.jobOperation.JobOperation;
import restInterface.consensus.ProposalNumber;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;
import system.managers.SqlManager;

public class ConsensusVariables {
	private long consensusGroupId = -1;
	private ProposalNumber hpn,hapn;
	private JobOperation hapv;
	private Long idConsRun;
	
	public ConsensusVariables(long consensusGroupId){
		this.consensusGroupId = consensusGroupId;
		hpn = null;
		hapn = null;
		hapv = null;
		idConsRun = null;
		getValues();
	}
	
	public synchronized long getIdConsRun() {
		if(idConsRun != null)
			return idConsRun;
			
		PadFsLogger.log(LogLevel.ERROR, "idConsRun is null");
		return -1;
	}

	public synchronized ProposalNumber getHpn() {
		return hpn;
	}

	public synchronized JobOperation getHapv() {
		return hapv;
	}

	public synchronized ProposalNumber getHapn() {
		return hapn;
	}

	public synchronized void setHPN(ProposalNumber hpn) {
		this.hpn = hpn;
		setValues();
		
	}
	
	public synchronized void setHapn(ProposalNumber hapn) {
		this.hapn = hapn;
		setValues();
		
	}
	
	public synchronized void setHapv(JobOperation hapv) {
		this.hapv = hapv;
		setValues();
		
	}
	
	public synchronized void incIdConsRun() {
		this.idConsRun++;
		setValues();
		
	}


	
	private synchronized boolean getValues(){
		HashMap<String,String> DBconsVar = SqlManager.getConsVar(consensusGroupId);
		
		if(DBconsVar!=null){
			hapn	  = ProposalNumber.createFromJson(DBconsVar.get("hapn"));
			hpn  	  = ProposalNumber.createFromJson(DBconsVar.get("hpn"));
			hapv 	  = JobOperation.createFromJson(DBconsVar.get("hapv"),DBconsVar.get("hapvType"));
			idConsRun = new Long(DBconsVar.get("idConsensusRun"));
			return true;
		}
		else{
			PadFsLogger.log(LogLevel.ERROR, "reading DBconsVar from the database failed");
		}
		
		return false;
	}
	
	
	private synchronized boolean setValues(){
		HashMap<String,String> hm = new HashMap<>(4);
		
		if(hpn!=null)
			hm.put("hpn", hpn.toJSON());
		else
			hm.put("hpn", "{\"number\":\"0\",\"nodeId\":0}");
		
		if(hapn!=null)
			hm.put("hapn", hapn.toJSON());
		else
			hm.put("hapn", "{\"number\":\"0\",\"nodeId\":0}");
		
		if(hapv!=null)
			hm.put("hapv", hapv.toJSON());
		else
			hm.put("hapv", null);
		
		if(hapv!=null)
			hm.put("hapvType", hapv.getClass().getName().toString());
		else
			hm.put("hapvType", null);
		
		if(idConsRun!=null)
			hm.put("idConsensusRun", idConsRun.toString());
		else
			hm.put("idConsensusRun", "0");

		return SqlManager.setConsVars(consensusGroupId,hm);
		
	}

	public synchronized void nextConsensus() {
		idConsRun++;
		hpn  = new ProposalNumber(0,0);
		hapn = new ProposalNumber(0,0);
		hapv = null;
		setValues();
	}

	public boolean setIdConsRun(long idConsRun) {
		this.idConsRun = idConsRun;
		hpn  = new ProposalNumber(0,0);
		hapn = new ProposalNumber(0,0);
		hapv = null;
		setValues();
		return true;
	}
	
	
	public long getConsensusGroupId(){
		return consensusGroupId;
	}
	

}
