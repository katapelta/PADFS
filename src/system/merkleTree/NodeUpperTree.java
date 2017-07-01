package system.merkleTree;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import system.SystemEnvironment.Variables;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;
import system.merkleTree.NodeLowerTree.TreePrinter;

public class NodeUpperTree implements Cloneable{
	private NodeUpperTree left  = null;
	private NodeUpperTree right = null;
	private NodeUpperTree parent = null;
	private long min,max,avg;
	private HashValue hashValue = null;
	private NodeLowerTree lowerTree = null;
	
	
	@JsonCreator
	public NodeUpperTree(
			@JsonProperty("leftChild") NodeUpperTree left,
			@JsonProperty("rightChild") NodeUpperTree right,
			@JsonProperty("minValue") long min,
			@JsonProperty("maxValue") long max,
			@JsonProperty("avgValue") long avg,
			@JsonProperty("hashValue") HashValue hashValue,
			@JsonProperty("lowerTree") NodeLowerTree lowerTree,
			@JsonProperty("leaf") Boolean leaf
			
			){
		/*
		 * during the deserialization made by spring, we set the parent field of my children 
		 */
		this.parent = null;
		if(left != null)
			left.parent = this;
		if(right != null)
			right.parent = this;
		
		this.left = left;
		this.right = right;
		
		this.min = min;
		this.max = max;
		this.avg = avg;
		this.hashValue = hashValue;
		this.lowerTree = lowerTree;
		
	}
	

	
	public NodeUpperTree(long min,long max){
		this.min = min;
		this.max = max;
		this.avg = (max-min)/2+min;
		
	}	
	
	/**
	 * Clone the node upper tree and all the leaf
	 * Create a new COMPLEATE upperTree
	 */
	public NodeUpperTree clone() {
		NodeUpperTree m;
		try { 
			m = (NodeUpperTree)super.clone();
			m.left	= (m.left  != null)? m.left.clone() : null;
			m.right = (m.right != null)? m.right.clone() : null;
			return m;
		} catch (CloneNotSupportedException e) { 
			e.printStackTrace(); 
			return null; 
		}
	}
	
	/**
	 * Remove the lower tree from the upperTree
	 */
	private void removeLowerTree(){			
		if(this.isLeaf()){ //upper tree leaf
			this.lowerTree = null;
		}else{
			if( this.left != null ){
				this.left.removeLowerTree();
			}
			if( this.right != null ){
				this.right.removeLowerTree();
			}
		}
	}
	
	/**
	 * Clone the nodeUpperTree and remove all lowerTree
	 * @return a new clean NodeUpperTree 
	 */
	public NodeUpperTree cloneClean(){
		NodeUpperTree node = this.clone();
		node.removeLowerTree();					
		return node;
	}
	
	public Boolean isLeaf(){
		return left == null && right == null;
	}
	
	public Boolean setLowerTree(NodeLowerTree lt){
		if(isLeaf()){
			lowerTree = lt;
			return true;
		}
		return false;
		
	}


	public String print(int height) {
		StringBuilder s = new StringBuilder();

		for(int i = 0; i<height; i++)
			s.append("  ");
		s.append("UpperNode ("+this.min+","+this.max+"){\n");
		if(this.lowerTree!= null){
			for(int i = 0; i<height; i++)
				System.out.print("  ");
			s.append("LowerTree:");
			TreePrinter.printNode(this.lowerTree);
			//this.lowerTree.printLabelTree();
			
		}
		if(this.left!= null){
			s.append(this.left.print(height+1));
		
			for(int i = 0; i<height; i++)
				s.append("  ");
			s.append(",");
			
			if(this.right!= null)
				s.append(this.right.print(height+1));
		}			
		for(int i = 0; i<height; i++)
			s.append("  ");
		s.append("}\n");
		return s.toString();
	}

	public synchronized NodeUpperTree findLeaf(Long label) {
		if(this.isLeaf()){
			//io sono una foglia
			return this;
		}else{
			if(label > avg){
				return this.right.findLeaf(label);
			}else{
				return this.left.findLeaf(label);
			}
		}
	}

	/*public NodeLowerTree findLowerTree(Long label) {
		NodeUpperTree upperLeaf = this.findLeaf(label);
		return upperLeaf.lowerTree;			
	}*/

	public synchronized boolean appendNewLowerTree(NodeLowerTree nl){
		this.lowerTree = nl;
		return updateHash();
	}
	
	/**
	 * recursively update the hashValues from this node to the root
	 */
	private synchronized boolean updateHash(){	
		if(this.left == null && this.right == null ){
			if(this.lowerTree != null)
				this.hashValue = new HashValue(this.lowerTree);
		
		} else if(this.left != null && this.right != null){
			if(this.hashValue == null)
				this.hashValue = new HashValue(0,0);
			
			this.hashValue.updateHash(left.getHashValue(),right.getHashValue());
		}
		else{
			PadFsLogger.log(LogLevel.ERROR, "Malformed Upper Tree");
			Variables.getMerkleTree().print();
			return false;
		}
		if(parent != null)
			parent.updateHash();
		
		return true;
	}
	
	public synchronized boolean split(){
		// add 2 children
		PadFsLogger.log(LogLevel.DEBUG,"splitting min "+min+" , max "+max,"green",null,true);
		addLeftChild(new NodeUpperTree(min, avg));
		addRightChild(new NodeUpperTree(avg+1, max));
		
		// divide into the 2 children the lower tree
		NodeLowerTree subTrees[] = new NodeLowerTree[2];
		
		if(this.lowerTree != null)
			subTrees = this.lowerTree.getSubTrees(avg);
					
			
		if(subTrees == null || subTrees.length != 2)
			return false;
		if(!left.appendNewLowerTree ( subTrees[0]))
			return false;
		if(!right.appendNewLowerTree( subTrees[1]))
			return false;
		
		this.lowerTree = null;
		if(this.left.updateHash())
			return this.right.updateHash();
		
		return false;
	}
	
	private synchronized void addLeftChild(NodeUpperTree n){
		n.parent = this;
		left = n;
		//n.updateHash();
	}
	private synchronized void addRightChild(NodeUpperTree n){
		n.parent = this;
		right = n;
		//n.updateHash();
	}

	
	public synchronized NodeUpperTree getRightChild(){
		return right;
	}
	
	public synchronized NodeUpperTree getLeftChild(){
		return left;
	}
	
	
	public synchronized long getMinValue(){
		return min;
	}
	
	public synchronized long getMaxValue(){
		return max;
	}
	
	public synchronized long getAvgValue(){
		return avg;
	}
	
	public synchronized HashValue getHashValue(){
		return hashValue;
	}
	
	public synchronized boolean add( long minRange, long maxRange ){
		//CREO UN NUOVO NODO con il range
		long newAvg = Long.divideUnsigned(maxRange-minRange,2)+minRange;
					
		
		if( maxRange == this.getMaxValue() && 
			minRange == this.getMinValue()){
			//esiste gia' il range
			return true;
		}else if(newAvg > this.avg){
			if(this.right != null)
				return this.right.add(minRange,maxRange);
			else{
				return this.split();
			}
		}else if (newAvg < this.avg){
			if(this.left != null)
				return this.left.add(minRange,maxRange);
			else{
				return this.split();
			}
		}else{
			PadFsLogger.log(LogLevel.FATAL, "NodeUpperTree compromised");
			return false;
		}
	}
	
	public synchronized boolean del(long minRange, long maxRange) {
		long newAvg = (maxRange-minRange)/2+minRange;
					
		
		if( maxRange == this.getMaxValue() && 
			minRange == this.getMinValue()){
			PadFsLogger.log(LogLevel.ERROR, "Try to remove the root node!");
			return false;
		}else if(this.right == null || this.left == null){
			return false;
		}else if(this.right.getAvgValue() == newAvg || this.left.getAvgValue() == newAvg){
			NodeLowerTree rightLowerTree = this.right.lowerTree;
			NodeLowerTree leftLowerTree  = this.left.lowerTree;
			this.lowerTree = leftLowerTree.merge(rightLowerTree);
			this.right = null;
			this.left = null;
			return true;
		}else if( newAvg > this.getAvgValue()){
			return right.del(minRange, maxRange);
		}else if(newAvg < this.getAvgValue()){
			return left.del(minRange, maxRange);
		}
		else{
			PadFsLogger.log(LogLevel.FATAL, "NodeUpperTree compromised");
			return false;
		}
			
	}

	/**
	 * Return the lowerTree between the startLabel and endLabel
	 * SUPPOSE THE REQUEST IS FOR A KNOWN INTERVAL IN THE UPPER TREE LEAF
	 * The method compare the startLabel with the intervals in the upperTree 
	 * @param startLabel
	 * @param endLabel
	 * @return the lowerTree
	 */
	public NodeLowerTree findLowerTree(long startLabel, long endLabel) {
		if(this.isLeaf()){
			return this.lowerTree;
		}else if(this.left.max > startLabel){ 
			return this.left.findLowerTree(startLabel, endLabel);
		}else if(this.right.min <= startLabel){ 
			return this.right.findLowerTree(startLabel, endLabel);
		}else{
			return null;
		}
	}
	
	public NodeLowerTree getLowerTree(){
		return this.lowerTree;
	}

	
		
}
