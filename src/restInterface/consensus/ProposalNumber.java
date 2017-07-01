package restInterface.consensus;

import java.io.IOException;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import system.SystemEnvironment.Variables;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;

public class ProposalNumber {
	private int number;
	private long nodeId;
	
	public ProposalNumber(ProposalNumber hpn){
		if(hpn == null){
			PadFsLogger.log(LogLevel.ERROR, "hpn is null");
			number = 0;
		}
		else{
			number = hpn.getNumber()+1;
		}
		nodeId = Variables.getServerId();
	}
	
	public ProposalNumber(@JsonProperty("number") int number,@JsonProperty("nodeId") long nodeId){
		
		this.number = number;
		this.nodeId = nodeId;
	}
	
	
	public static ProposalNumber createFromJson(String propNum)  {
		try {
			return new ObjectMapper().readValue(propNum, ProposalNumber.class);
		} catch (IOException e) {
			PadFsLogger.log(LogLevel.ERROR,"[ProposalNumber][createFromJson]: " + e.getMessage());
			return null;
		}
	}

	public boolean greaterThan(ProposalNumber x){
		if(number > x.getNumber())
			return true; 
		else if(number == x.getNumber() && (nodeId > x.getNodeId()) )
			return true;
		
		return false;
	}
	
	public boolean greaterEqualThan(ProposalNumber x){
		if(number > x.getNumber())
			return true;
		else if(number == x.getNumber() && (nodeId >= x.getNodeId()) )
			return true;
		
		return false;
	}
	
	public boolean equals(ProposalNumber x){
		return (number == x.getNumber() && (nodeId == x.getNodeId()) );
	}
	
	public int getNumber(){
		return number;
	}
	
	public long getNodeId(){
		return nodeId;
	}

	public String toJSON() {
		ObjectWriter writer = new ObjectMapper().writer();
		try {
			return writer.writeValueAsString(this);
		} catch (JsonProcessingException e) {
			PadFsLogger.log(LogLevel.ERROR,"[toJSON]: " + e.getMessage());
		}
		return null;
	}
	
	@Override
	public String toString(){
		return String.valueOf(number)+":"+nodeId;
	}
	
	
}