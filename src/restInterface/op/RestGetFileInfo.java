package restInterface.op;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import system.SystemEnvironment.Constants.Permission;
import system.SystemEnvironment.Constants.Rest;
import system.containers.MetaInfo;


/**
 * RISPOSTA ALLA GET DEL CLIENTE
 */
public class RestGetFileInfo extends RestOp {

    private final Rest.status status; // Stato della richiesta GET (se � andata a buon fine oppure no)
    private final String path;   // Nome del file che si � richiesto
    private final Rest.errors error;  // MSG di errore nel caso non sia andato a buon fine la richiesta
    private final String checksum;
    private final String size;
    private final String dateTime;
    private final int hostersAlive;
    private final Permission userPermission;
    
    
    @JsonCreator
    public RestGetFileInfo(
    		@JsonProperty("status") Rest.status status,
    		@JsonProperty("path") String path,
    		@JsonProperty("error") Rest.errors error,
    		@JsonProperty("checksum") String checksum,
    		@JsonProperty("size") String size,
    		@JsonProperty("dateTime") String dateTime,
    		@JsonProperty("errorDescription") String errorDescription,
    		@JsonProperty("hostersAlive") int hostersAlive,
    		@JsonProperty("userPermission") Permission userPermission
    		
    		){
    	this.status = status;
    	this.path = path;
    	this.error = error;
    	this.checksum = checksum;
    	this.size = size;
    	this.dateTime = dateTime;
    	this.hostersAlive = hostersAlive;
    	this.userPermission = userPermission;
    }
    
    
    public RestGetFileInfo(Rest.status status,  MetaInfo metaInfo, String path, int hostersAlive, Permission userPermission, Rest.errors error) {
        this.status 	= status;    
        this.error 		= error; 
        if(metaInfo != null && metaInfo.getHostersId() != null){
        	this.path 		= metaInfo.getPath();
        	this.checksum	= metaInfo.getChecksum();
        	this.dateTime	= metaInfo.getDateTime();
        	this.size		= metaInfo.getSize();
            this.hostersAlive = hostersAlive;
            this.userPermission = userPermission;
        }
           
        else{
        	this.path	= path;
        	this.checksum = null;
        	this.dateTime = null;
        	this.size 	  = null;
        	this.hostersAlive = 0;
        	this.userPermission = null;
        }
        
        
    }
    
    public Rest.status getStatus() {
        return status;
    }
    
    public String getPath() {
        return path;
    }
    
    public Permission getUserPermission() {
        return userPermission;
    }


    public String getSize() {
        return size;
    }

    public int getHostersAlive(){
    	return hostersAlive;
    }

    public String getDateTime() {
        return dateTime;
    }

    public String getchecksum() {
        return checksum;
    }
    
    public Rest.errors getError() {
        return error;
    }
    
    public String getErrorDescription(){
    	if(this.error != null)
    		return this.error.toString();
    	return null;
	}
	
}