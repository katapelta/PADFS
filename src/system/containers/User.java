package system.containers;

import com.fasterxml.jackson.annotation.JsonProperty;

public class User {
	private String id;
	private String username;
	private String password;
	
	public User(@JsonProperty("id") String id,
				@JsonProperty("user") String username,
				@JsonProperty("password") String password
				){
		this.id = id;
		this.username= username;
		this.password = password;
		
	}
	
	
	public String getId(){
		return id;
	}
	
	public String getUser(){
		return username;
	}
	
	public String getPassword(){
		return password;
	}
	
	public String toString(){
		StringBuilder str = new StringBuilder();
		str.append("\n");
		str.append("id: "+id+" ");
		str.append("user: "+username+" ");
		str.append("password: "+password+"\n");
		return str.toString();
	}
	
}
