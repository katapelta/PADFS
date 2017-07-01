package system.merkleTree;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import system.SystemEnvironment.Constants;

public class MerkleTree {
	
	NodeUpperTree root;
	
	public MerkleTree(){
		root = new NodeUpperTree(0, Constants.maxLabel);
	}
	
	public MerkleTree(NodeUpperTree T){
		root = T;
	}
	
	public NodeUpperTree getUpperTree(){
		return root;
	}
	
	public Boolean addFile(Long label, Integer updatesNumber, String path, long size, int idOwner){
		if(root == null)
			return false;
		
		//find the leaf T that manage this label
		NodeUpperTree T = root.findLeaf(label);
		if(T == null)
			return false;
		
		//get lower tree of T
		NodeLowerTree t = T.getLowerTree();
		
		if(t == null){
			//if no lowerTree exists, create a new one and assign it to T
			t = new NodeLowerTree(label, updatesNumber, path, size, idOwner);
			T.setLowerTree(t);
			return true;
		}
		else{
			//if it exists a lowerTree, do the addFile() and reassign the root of the balanced lowerTree to T
			t = t.addFile(label,updatesNumber, path,size,idOwner, t);
			if(t != null){
				T.setLowerTree(t);
				return true;
			}
		}
		return false;
		
	}

	public String toString(){
		return print();
	}
	
	public String print() {
		return root.print(0);
	}
/**
 * FILE FUNCTION
 */
	/**
	 * append the file in the tree or update the number of updates
	 * @param label
	 * @param updatesNumber
	 * @param path
	 * @param size
	 * @param idOwner
	 * @return
	 */
	public synchronized boolean putFile(long label, Integer updatesNumber, String path, long size, int idOwner){
		NodeLowerTree rootLower = null;
		NodeUpperTree upperLeaf = root.findLeaf(label);
		rootLower = upperLeaf.getLowerTree();
			
		
		if(rootLower==null){
			NodeUpperTree currentUpperNode=null;
			
			NodeLowerTree newNode = new NodeLowerTree(label, updatesNumber, path, size, idOwner);
			currentUpperNode = root.findLeaf(label);
			if(currentUpperNode != null)
				return currentUpperNode.appendNewLowerTree(newNode);
			
			
		}else{
			NodeLowerTree currentLower ;
			currentLower = rootLower.findNode(label);
			if(currentLower != null)
				return currentLower.addExistingHash(label, updatesNumber, path, size, idOwner);
			else{
				NodeLowerTree newNode = new NodeLowerTree(label, updatesNumber, path, size, idOwner);
				 upperLeaf.setLowerTree( rootLower.add(newNode) );
				 if(upperLeaf.getLowerTree() != null)
					 return true;
			}		
		}	
		
		return false;
	}
	
	public synchronized boolean removeFile(Long label, Integer updatesNumber, String path, long size, int idOwner){
		NodeLowerTree rootLower = null;
		NodeUpperTree upperLeaf = root.findLeaf(label);
		rootLower = upperLeaf.getLowerTree();
				
		if(rootLower==null){
			return false;
		}else{
			NodeLowerTree currentLower ;
			currentLower = rootLower.findNode(label);
			if(currentLower != null){
				if(currentLower.getNumElements() == 1){
					upperLeaf.setLowerTree ( upperLeaf.getLowerTree().delete(label) );
					return true;
				}
				else{
					return currentLower.removeExistingHash(label, updatesNumber, path, size, idOwner);
				}
			}else{
				return false;
			}
		}
	}
	
	

/**
 * SERVER FUNCTION
 */
	/**
	 * update the MerkleTree to accomplish the new servers structure
	 * 
	 * @param minRange minRange of the inserted Server
	 * @param maxRange maxRange of the inserted Server
	 */
	public synchronized void addServer(long minRange, long maxRange){
		//long middle = Long.divideUnsigned(maxRange-minRange,2)+minRange;
		this.root.add(minRange, maxRange);
		//this.root.add(middle+1, maxRange);
	}

	public synchronized void removeServer(long minRange, long maxRange){
		this.root.del(minRange, maxRange);
	}

	/**
	 * 
	 * @return the list of label intervals that have a NodeLowerTree inside the merkleTree
	 */
	public Iterator<long[]> intervalIterator() {
		/*
		 * navigate the UpperTree and put in a list all the intervals that have a NodeLowerTree
		 * then return the iterator of this list
		 */	
		List<long[]> l = new LinkedList<long[]>();
		
		NodeUpperTree T = this.getUpperTree();
		populate(l,T);
		
		return l.iterator();
	}
	
	
	/**
	 * navigate the UpperTree and put in a list all the intervals 
	 *  
	 * @param l
	 * @param T
	 * @return
	 */
	private boolean populate(List<long[]> l, NodeUpperTree T){
		if(T.isLeaf()){
			long[] interval = new long[2];
			interval[0] = T.getMinValue();
			interval[1] = T.getMaxValue();
			return l.add(interval);	
		}
		else{
			boolean b = true;
			if(T.getLeftChild() != null)
				b = populate(l,T.getLeftChild());
			
			if(b && T.getRightChild() != null)
				b = populate(l,T.getRightChild());
			
			return b;
		}
	}

	


	
}
