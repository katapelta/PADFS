package system.containers;

import java.util.LinkedList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import system.managers.SqlManager;

public class HostedFile {

	private String logicalPath;
	private String physicalPath;
	private int idOwner;
	private String checksum;
	private String dateTime;
	private String size;
	private long label;
	private boolean uploadingFlag;
	
	
	
	
	@JsonCreator
	public HostedFile(
					@JsonProperty("logicalPath") String logicalPath,
					@JsonProperty("physicalPath") String physicalPath,
					@JsonProperty("dateTime") String dateTime,
					@JsonProperty("size") String size, 
					@JsonProperty("idOwner") int idOwner,
					@JsonProperty("label") long label, 
					@JsonProperty("checksum") String checksum,
					@JsonProperty("uploadingFlag") Boolean uploadingFlag){

		this.logicalPath = logicalPath;
		this.physicalPath = physicalPath;
		this.dateTime = dateTime;
		this.size = size;
		this.idOwner = idOwner;
		this.label = label;
		this.checksum = checksum;
		this.uploadingFlag = uploadingFlag;
		
	}
	
	
	@Override
	public String toString(){
		return "'"+idOwner+":"+logicalPath+"' "+dateTime;
	}
	
	public String getLogicalPath() {
		return logicalPath;
	}
	public String getPhysicalPath() {
		return physicalPath;
	}
	
	public String getDateTime() {
		return dateTime;
	}

	public String getSize() {
		return size;
	}

	public int getIdOwner() {
		return idOwner;
	}

	public long getLabel() {
		return label;
	}

	public String getChecksum() {
		return checksum ;
	}
	
	public boolean getUploadingFlag() {
		return uploadingFlag;
	}
	
	@JsonIgnore
	public List<Server> getMetaInfoServers() {
		long[] serverIds = SqlManager.getIdFromConsensusLabel(label);
		List<Server> servList = new LinkedList<Server>();
		
		for( long i : serverIds){
			servList.add(Server.find(i));
		}
		
		return servList;
	}


	
	

}
