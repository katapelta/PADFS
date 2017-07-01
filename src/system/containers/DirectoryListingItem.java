package system.containers;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import system.SystemEnvironment;
import system.managers.SqlManager;

public class DirectoryListingItem {

	private String path;
	private int idOwner;
	private Long label;
	private boolean isDirectory;
	private String uniqueIdParentDirectory;
	private String usernameOwner = null;
	private String size;
	private String dateTime;
		
	
	
	@JsonCreator
	public DirectoryListingItem(@JsonProperty("path") String path,
					@JsonProperty("idOwner") int idOwner,
					@JsonProperty("size") String size,
					@JsonProperty("dateTime") String dateTime,
					@JsonProperty("directory") boolean isDirectory, 
					@JsonProperty("uniqueIdParentDirectory") String uniqueIdParentDirectory
					){
		this.path = path;
		this.idOwner = idOwner;
		this.size = size;
		this.dateTime = dateTime;
		this.isDirectory = isDirectory;		
		this.uniqueIdParentDirectory = uniqueIdParentDirectory;
	}
	
	public String getSize(){
		return size;
	}
	public String getDateTime(){
		return dateTime;
	}
	
	public String getPath() {
		return path;
	}

	public int getIdOwner() {
		return idOwner;
	}
	
	@JsonIgnore
	public String getUsernameOwner() {
		if(usernameOwner == null)
			usernameOwner = SqlManager.getUsername(idOwner);
		return usernameOwner;
	}


	public boolean isDirectory() {
		return isDirectory;
	}

	public String getUniqueIdParentDirectory(){
		return uniqueIdParentDirectory;
	}

	@JsonIgnore
	public Long getLabel(){
		if(label == null)
			label = SystemEnvironment.getLabel(getUsernameOwner(),getPath());
		return label;
	}
	

}
