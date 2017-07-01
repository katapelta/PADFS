package system.containers;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import system.managers.SqlManager;

public class MetaInfo {

	
	
	
	private final String path;
	private final String dateTime;
	private final String size;
	private final int idOwner;
	private final long label;
	private final int updatesNumber;
	private final boolean isDirectory;
	private final List<Long> hostersId;
	private final List<FilePermission> permissionList;
	private final String checksum;
	private final String checksumParent;
	private final String nameOwner;

	
	/**
	 * create a new MetaInfo with all the information of MetaInfo file
	 * but with the hostersId contained in List<Long> servers
	 * and WITHOUT permissionList information
	 * 
	 * @param file
	 * @param servers
	 */
	@JsonIgnore 
	public MetaInfo(MetaInfo file,List<Long> servers){
		this.path = file.path;
		this.dateTime = file.dateTime;
		this.size = file.size;
		this.idOwner = file.getIdOwner();
		this.label = file.label;
		this.updatesNumber = file.updatesNumber;
		this.isDirectory = file.isDirectory;
		this.hostersId = servers;
		this.checksum = file.checksum;
		this.checksumParent = file.checksumParent;
		
		/* do not add perissionList information to this constructed object */
		this.permissionList = null;
		this.nameOwner = null;
	}
	
	@JsonIgnore
	public MetaInfo(Long id, //only meaningful inside one server. other servers may change the value of this field
					String path,
					String dateTime,
					String size, 
					int idOwner,
					long label, 
					int updatesNumber, 
					boolean isDirectory,
					String checksum,
					String checksumParent){
		
		this.path = path;
		this.dateTime = dateTime;
		this.size = size;
		this.idOwner = idOwner;
		this.label = label;
		this.updatesNumber = updatesNumber;
		this.isDirectory = isDirectory;		
		this.checksum = checksum;
		this.checksumParent = checksumParent;
		this.nameOwner = null;

		hostersId = SqlManager.getHostersId(id);
		permissionList = SqlManager.getPermissionList(id);
	}

	@JsonIgnore
	public MetaInfo(Long id, //only meaningful inside one server. other servers may change the value of this field
					String path,
					String dateTime,
					String size,
					int idOwner,
					String nameOwner,
					long label,
					int updatesNumber,
					boolean isDirectory,
					String checksum,
					String checksumParent){

		this.path = path;
		this.dateTime = dateTime;
		this.size = size;
		this.idOwner = idOwner;
		this.nameOwner = nameOwner;
		this.label = label;
		this.updatesNumber = updatesNumber;
		this.isDirectory = isDirectory;
		this.checksum = checksum;
		this.checksumParent = checksumParent;

		hostersId = SqlManager.getHostersId(id);
		permissionList = SqlManager.getPermissionList(id);
	}
		
	@JsonCreator
	public MetaInfo(@JsonProperty("path") String path,
					@JsonProperty("dateTime") String dateTime,
					@JsonProperty("size") String size, 
					@JsonProperty("idOwner") int idOwner,
					@JsonProperty("label") long label, 
					@JsonProperty("updatesNumber") int updatesNumber, 
					@JsonProperty("directory") boolean isDirectory, 
					@JsonProperty("hostersId") List<Long> hostersId,
					@JsonProperty("permissionList") List<FilePermission> permissionList,
					@JsonProperty("checksum") String checksum,
					@JsonProperty("checksumParent") String checksumParent
					){
		this.path = path;
		this.dateTime = dateTime;
		this.size = size;
		this.idOwner = idOwner;
		this.label = label;
		this.updatesNumber = updatesNumber;
		this.isDirectory = isDirectory;		
		this.hostersId = hostersId;		
		this.permissionList = permissionList;
		this.checksum = checksum;
		this.checksumParent = checksumParent;
		this.nameOwner = null;
	}
	
	@Override
	public String toString(){
		return "'"+idOwner+":"+path+"' "+dateTime;
	}
	
	public String getPath() {
		return path;
	}
	
	public List<Long> getHostersId() {
		return hostersId;
	}
	
	public List<FilePermission> getPermissionList() {
		return permissionList;
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

	public int getUpdatesNumber() {
		return updatesNumber;
	}

	public boolean isDirectory() {
		return isDirectory;
	}

	public String getChecksum() {
		return checksum ;
	}
	
	public String getChecksumParent() {
		return checksumParent ;
	}

	public String getNameOwner() {
		return nameOwner ;
	}


}
