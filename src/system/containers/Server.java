package system.containers;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import system.SystemEnvironment.Constants;
import system.SystemEnvironment.Constants.ServerStatus;
import system.SystemEnvironment.Variables;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;

public class Server {
	private String ip = null;
	private String port = null;
	
	private Long id = -1L;
	private int groupId = -1;
	private Long label= Constants.maxLabel;
	private String availableSpace = null;
	private String totalSpace = null;
	private ServerStatus	status = Constants.ServerStatus.UNKNOWN;
	private String keepAlive = null;
	private String keepAliveTime = null;
	
/*	
	public Server( @JsonProperty("ip") String ip,
			@JsonProperty("id") Long id,
			@JsonProperty("port") String port,
			@JsonProperty("groupId") int groupId,
			@JsonProperty("label") Long label){
		this.ip = ip;
		this.id = id;
		this.port = port;
		this.label = label;
		this.groupId = groupId;
	}
*/	
	@JsonCreator
	public Server( @JsonProperty("id") Long id,
			@JsonProperty("ip") String ip,
			@JsonProperty("port") String port,
			@JsonProperty("availableSpace")String availableSpace,
			@JsonProperty("totalSpace")String totalSpace,
			@JsonProperty("status")ServerStatus status,
			@JsonProperty("keepAlive")String keepAlive,
			@JsonProperty("keepAliveTime") String keepAliveTime,
			@JsonProperty("groupId") int replicaGroupId,
			@JsonProperty("label") Long replicaLabel
			){

		this.id = id;
		this.ip = ip;
		this.port = port;
		this.availableSpace = availableSpace;
		this.totalSpace = totalSpace;
		this.status = status;
		this.keepAlive = keepAlive;
		this.keepAliveTime = keepAliveTime;
		this.label = replicaLabel;
		this.groupId = replicaGroupId;
	}
	
	public Server( Long id,
			String ip,
			String port,
			ServerStatus status,
			int replicaGroupId,
			Long replicaLabel
			){

		this.id = id;
		this.ip = ip;
		this.port = port;
		this.status = status;
		this.label = replicaLabel;
		this.groupId = replicaGroupId;
	}
	
	
	
	public Server(long id, String ip, String port){
		this.id = id;
		this.ip = ip;
		this.port = port;
		this.status = Constants.ServerStatus.UNKNOWN;
	}
	
	public Server(String ip, String port){
		this.ip = ip;
		this.port = port;
	}
	

	
	public String getIp() {
		return ip;
	}
	
	
	public Long getId() {
		return id;
	}

	public String getPort() {
		return port;
	}
	
	public Long getLabel() {
		return label;
	}
	
	public int getGroupId() {
		return groupId;
	}
	
	public String getAvailableSpace(){
		return availableSpace;
	}
	
	public String getTotalSpace(){
		return totalSpace;
	}
	
	public ServerStatus getStatus(){
		return status;
	}
	
	public String getKeepAlive(){
		return keepAlive;
	}
	
	public String getKeepAliveTime(){
		return keepAliveTime;
	}
	
	
	public boolean setId(Long idServer) {
		if(this.id != -1){
			return false;
		}
		this.id = idServer;
		return true;
	}
	
	public boolean setGroupId(int groupId) {
		if(this.groupId != -1){
			return false;
		}
		this.groupId = groupId;
		return true;
	}
	
	public boolean setLabel(Long label) {
		if(this.label != Constants.maxLabel){
			return false;
		}
		this.label = label;
		return true;
	}
	
	public boolean setStatus(ServerStatus status) {
		this.status = status;
		return true;
	}

	public boolean setAvailableSpace(String availableSpace) {
		this.availableSpace = availableSpace;
		return true;
	}

	public boolean setTotalSpace(String totalSpace) {
		this.totalSpace = totalSpace;
		return true;
	}

	public boolean setKeepAlive(String keepAlive) {
		this.keepAlive = keepAlive;
		return true;
	}

	public boolean setKeepAliveTime(String keepAliveTime) {
		this.keepAliveTime = keepAliveTime;
		return true;
	}
	
	public String toString(){
		String formato = "[ ID: %-4s ; IP: %-15s ; PORT: %-5s ; KEEPALIVE: %-2s; KEEPALIVETIME: %-10s; AV-SPACE: %-10s; TOT-SPACE: %-10s; STATUS: (%-2d) %-5s  ; GROUP ID: %-2d ; LABEL: %-20s ]\n";
		String str2 = String.format(
				formato,
				String.valueOf(id),
				ip,
				port,
				keepAlive,
				keepAliveTime,
				availableSpace,
				totalSpace,
				status.getNumVal(),
				status.getStringVal(),
				groupId,
				String.valueOf(label)
		);
		return str2;
		/**
		StringBuilder str = new StringBuilder();
		//str.append("\n\t");
		str.append("[ id: "+id+"; ");
		str.append("ip: "+ip+"; ");
		str.append("port: "+port+"; ");
		str.append("status: "+status.getNumVal()+"; ");
		str.append("groupId: "+groupId+"; ");
		str.append("label: "+label+" ] ");
		return str.toString();
		*/
	}

	
	public String toMinimalString(){
		String formato = "%-4s;%-15s;%-5s;(%-2s);%-5s;%-2d;%-20s";
		String str2 = String.format(formato, String.valueOf(id), ip, port, status.getNumVal() ,status.toString(), groupId, String.valueOf(label));
		return str2;
		/**
		StringBuilder str = new StringBuilder();
		//str.append("\n\t");
		str.append("[ id: "+id+"; ");
		str.append("ip: "+ip+"; ");
		str.append("port: "+port+"; ");
		str.append("status: "+status.getNumVal()+"; ");
		str.append("groupId: "+groupId+"; ");
		str.append("label: "+label+" ] ");
		return str.toString();
		*/
	}
	
	
	@SuppressWarnings("unchecked")
	public static List<Server> createServerListFromJson(String serverList) {
		try {	
			PadFsLogger.log(LogLevel.DEBUG, "CREATE FROM JSON ->  serverList: "+serverList);
			List<Server> ret; 
			ret = (List<Server>) new ObjectMapper().readValue(serverList,List.class);
			return ret;
		} catch (IOException e) {
			PadFsLogger.log(LogLevel.ERROR, "JSON DECODE ERROR: " + e.getMessage());
		}
		return null;
	}
	
	
	public static String serverListToJson(List<Server> serverList){
		ObjectWriter writer = new ObjectMapper().writer();
		try {
			return writer.writeValueAsString(serverList);
		} catch (JsonProcessingException e) {
			PadFsLogger.log(LogLevel.ERROR,"[toJSON]: " + e.getMessage());
		}
		return null;
	}
	
	
	public static class ServerComparator implements Comparator<Server> {
	    @Override
	    public int compare(Server a, Server b) {
	        if( a.groupId < b.groupId )
	        	return -1;
	        if(a.groupId > b.groupId )
	        	return 1;
	        
	        //else if they have same groupId
	        return Long.compareUnsigned(a.label,b.label); 
	    }
	}

	/**
	 * @return the server with the id specified if it exists in the Variables
	 * @return null otherwise
	 */
	public static Server find(Long id) {
		List<Server> l = Variables.getServerList();
		if(l!= null){
			for(Server s : l){
				if(s!= null && s.getId().equals(id)){
					return s;
				}
			}
		}
		return null;
	}
	
}
