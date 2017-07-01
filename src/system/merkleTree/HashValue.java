package system.merkleTree;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class HashValue{
	private int hash; 	//the hash of the node and the sub nodes
	private int myHash; //the hash of the node
	
	@JsonCreator
	public HashValue(
			@JsonProperty("hash") int hash,
			@JsonProperty("myHash") int myHash){
		this.hash = hash;
		this.myHash = myHash;
	}
	
	public int getHash(){
		return hash;
	}
	public int getMyHash(){
		return myHash;
	}
	
	public HashValue(Long label, Integer updatesNumber,
			String path, long size, int idOwner){
		this.hash = this.evaluateHash(createUniqueString(label, updatesNumber, path, size, idOwner));
		this.myHash = this.hash;
	}
	
	public HashValue(NodeLowerTree lowerTree){
		this.myHash = lowerTree.getHash().getHashValue();
		this.hash = this.myHash;
	}
	
	public int getHashValue(){
		return hash;
	}
	
	public String toString(){
		return String.valueOf(this.hash);
	}
	
	private String createUniqueString(Long label, Integer updatesNumber,
			String path, long size, int idOwner){
		StringBuilder str = new StringBuilder();
		str.append(label);
		str.append(updatesNumber);
		str.append(path);
		str.append(size);
		str.append(idOwner);
		return str.toString();
	}
	
	public boolean add(Long label, Integer updatesNumber,
			String path, long size, int idOwner){
		int val = this.evaluateHash(createUniqueString(label, updatesNumber, path, size, idOwner));
		this.hash   += val;
		this.myHash += val;
		return true;
	}
	
	public boolean sub(Long label, Integer updatesNumber,
			String path, long size, int idOwner){
		int val = this.evaluateHash(createUniqueString(label, updatesNumber, path, size, idOwner));
		this.hash   -= val;
		this.myHash -= val;
		return true;
	}
	
	public boolean updateHash( HashValue left, HashValue right ){
		
		this.hash = this.myHash;
		
		if(left!=null)
			this.hash += left.getHashValue();
		if(right!=null)
			this.hash += right.getHashValue();
		
		return true;
	}
	
	private int evaluateHash(String str){
		int hash = 7;
		for (int i = 0; i < str.length(); i++) {
		    hash = hash*31 + str.charAt(i);
		}
		return hash;
	}
}

