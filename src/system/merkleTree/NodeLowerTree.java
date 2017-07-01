package system.merkleTree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;

public class NodeLowerTree{
	private HashValue     hash   = null;
	private long	      label        ;
	private NodeLowerTree left   = null;
	private NodeLowerTree right  = null;
	private NodeLowerTree parent = null;
	private int height;
	private int numElement = 0;
	
	@JsonCreator
	public NodeLowerTree(
			@JsonProperty("hash") HashValue hash,
			@JsonProperty("label") long label,
			@JsonProperty("left") NodeLowerTree left,
			@JsonProperty("right") NodeLowerTree right,
			@JsonProperty("height") int height,
			@JsonProperty("numElement") int numElement
			){
		this.hash = hash;
		this.label = label;
		this.left = left;
		this.right = right;
		
		this.parent = null;
		if(left != null)
			left.parent = this;
		if(right != null)
			right.parent = this;
		
		this.height = height;
		this.numElement = numElement;
		
	}
			
	/**
	 * generate a new node with a new label and its related information
	 * @param label
	 * @param updatesNumber
	 * @param path
	 * @param size
	 * @param idOwner
	 */
	public NodeLowerTree(Long label, Integer updatesNumber, String path, long size, int idOwner){
		this.label = label;
		this.numElement++;
		this.hash = new HashValue(label, updatesNumber, path, size, idOwner);
	}

	
	public synchronized long getLabel(){
		return label;
	}
	public synchronized int getheight(){
		return height;
	}

	public synchronized NodeLowerTree addFile(Long label, Integer updatesNumber, String path, long size, int idOwner, NodeLowerTree root) {
		
		//check if the label is already present in the lower tree
		NodeLowerTree node = findNode(label);
		if(node == null){
			//create a new node and append it to the tree
			NodeLowerTree newNode = new NodeLowerTree(label, updatesNumber, path, size, idOwner);
			return root.add(newNode);
			
		}
		else{
			//update the hashValue of the node
			node.addExistingHash(label, updatesNumber, path, size, idOwner);
			return root;
		}
		
	}

	public synchronized NodeLowerTree delete(Long label) {
		//TODO  what if there is only one element?
		//TODO what if we need to remove the root?
		NodeLowerTree root = this;
		NodeLowerTree np = this.findParentNode(label);
		NodeLowerTree n  = null;
		if(np == null)
			n = findNode(label);
		
		
		int childType = 0; // 0 null /  1 rightChild / -1 leftChild
		if(np != null){
			if(np.left != null && np.left.label == label){
				n = np.left;
				childType=-1;
			}else if(np.right != null && np.right.label == label){
				n = np.right;
				childType=1;
			}else{
				PadFsLogger.log(LogLevel.ERROR, "Try to remove a node that not exist");
				return null;
			}
			
		}
		
		/*
		 * if it is a leaf, we can simply delete the node
		 */
		if(n.left == null && n.right == null){
			/* it does not have children */
			if(np != null){
				/* it is not the root */
				if(childType == 1)
					np.right = null;
				else if(childType == -1)
					np.left = null;
				return root;
			}
			else{
				/* it is the root */
				return null;
			}
			
			
		}
		
		/* 
		 * otherwise
		 * get predecessor or successor and substitute
		 *          ...
		 *           
		 *           np
		 *          / \ 
		 *         ..  n
		 *            / \
		 *           L   R
		 * 
		 */
		else if(n.left != null){
			/* if the left child exists, select the predecessor */
			NodeLowerTree predecessor = n.left.rightMost();
			NodeLowerTree parentPredecessor = predecessor.parent;
			if(childType == 1){
				np.right = predecessor;
			}
				
			else if(childType == -1){
				np.left= predecessor;
				
			}
			else if(childType == 0){
				/* if it is the root - (np = null ) */
				root = predecessor;
			}
			
			predecessor.parent = np;
			if(parentPredecessor != n){
				/* if the removed node is not the parent of its predecessor */
				
				parentPredecessor.right = predecessor.left;
				if(predecessor.left != null)
					predecessor.left.parent = parentPredecessor;
				
				predecessor.left = n.left;
				predecessor.right = n.right;
				n.left.parent = predecessor; /* n.left != null (known by precedent if) */
				if(n.right != null)
					n.right.parent = predecessor;
			}
			else{
				/* if the removed node is the parent of its predecessor */
				predecessor.right = n.right;
				if(n.right != null)
					n.right.parent = predecessor;
				
				
			}
			
			
			
			
		}else if(n.right != null){
			/* if the right child exists, select the successor */
			NodeLowerTree predecessor = n.right.leftMost();
			NodeLowerTree parentPredecessor = predecessor.parent;
			if(childType == 1){
				np.right = predecessor;
			}
				
			if(childType == -1){
				np.left= predecessor;
			}
			else if(childType == 0){
				/* if it is the root - (np = null ) */
				root = predecessor;
			}
			predecessor.parent = np;
			if(parentPredecessor != n){
				/* if the removed node is not the parent of its predecessor */
				parentPredecessor.left = predecessor.right;
				if(predecessor.right != null)
					predecessor.right.parent = parentPredecessor;
				predecessor.left = n.left;
				predecessor.right = n.right;
				if(n.left!= null)				
					n.left.parent = predecessor; /* dead code. if we reach this point, n.left is null */
				n.right.parent = predecessor;
			}
			else{
				/* if the removed node is the parent of its predecessor */
				predecessor.left = n.left;
				if(n.left!= null)
					n.left.parent = predecessor;
			}
		}
		
		/* balance the tree (starting from the new root) */
		root = root.balanceTree();
		return root;
	}




	private synchronized NodeLowerTree rightMost() {
		if(this.right != null)
			return this.right.rightMost();
		else
			return this;
	}
	
	private NodeLowerTree leftMost() {
		if(this.left != null)
			return this.left.leftMost();
		else
			return this;
	}




	public synchronized NodeLowerTree[] getSubTrees( long splitLabel){
		
		NodeLowerTree root = this;
		NodeLowerTree findLeft = null, findRight = null;
		NodeLowerTree ret[] = new NodeLowerTree[2];
		
		/* find the splitting node    
		 * that is the node with the label=splitLabel 
		 *      or the node that would be its parent if it does not exist   */
		NodeLowerTree splittingNode = findParentNode(splitLabel, root);
		if(splittingNode == null)
			splittingNode = root; // findParentNode can return null if the node searched is the root
		
		else{
			if(splitLabel < splittingNode.label){
				if(splittingNode.left != null && splittingNode.left.label == splitLabel)
					splittingNode = splittingNode.left;
			}
			else if(splitLabel > splittingNode.label){
				if(splittingNode.right != null && splittingNode.left.label == splitLabel)
					splittingNode = splittingNode.right;
			}
		}
		
		/* bring it to the top keeping order */
		NodeLowerTree tmp = bringToTop(root, splittingNode);
		
		/* split the tree */	
		if(tmp.label <= splitLabel){
			findRight = tmp.right;
			findLeft = tmp;
			if(findRight != null){
				findRight.parent = null;
			}
			findLeft.right = null;
		}
		else{
			findRight = tmp;
			findLeft = tmp.left;
			if(findLeft != null){
				findLeft.parent = null;
			}
			findRight.left = null;
		}
		
		/* balance the trees */
		if(findLeft != null)
			findLeft = findLeft.balanceTree();
		if(findRight != null)
			findRight = findRight.balanceTree();
		
		
		ret[0]=findLeft;
		ret[1]=findRight;
		return ret;
	}		
	
	/**
	 * balance the tree
	 * @return the root of the balanced tree
	 */
	private synchronized NodeLowerTree balanceTree() {
		NodeLowerTree critical = null;
		NodeLowerTree root = this;
		height();
		while( (critical=findCriticalNodeFromTop(root)) != null){
			root = applyRotation(root, critical);
			root.height();
		}
		return root;
	}

	/**
	 * bring the splittingNode to the root of the tree. Of course the tree will not be balanced anymore
	 * 
	 * @param root
	 * @param splittingNode
	 * @return the new root of the tree
	 */
	private synchronized NodeLowerTree bringToTop(NodeLowerTree root,NodeLowerTree splittingNode) {
		while(splittingNode.parent != null){
			if(splittingNode.parent.left == splittingNode)
				root = leftLeftRotate(root, splittingNode.parent);

			else if(splittingNode.parent.right == splittingNode)
				root = rightRightRotate(root, splittingNode.parent);
			
			else
				PadFsLogger.log(LogLevel.ERROR, "LowerTree compromised");
		}
		return splittingNode;
	}

	/**
	 * 
	 * apply a rightLeft rotation to the tree
	 * 
	 * @param root the root of this tree
	 * @param criticalNode the criticalNode to apply the rotation
	 * @return
	 */
	private synchronized NodeLowerTree rightLeftRotate(NodeLowerTree root, NodeLowerTree criticalNode){
		NodeLowerTree parent = criticalNode.parent;
		NodeLowerTree child  = criticalNode.getRight();
		NodeLowerTree grandChild  = child.getLeft();
		
		criticalNode.right = grandChild.left;
		child.left = grandChild.right;
		
		grandChild.left = criticalNode;
		grandChild.right = child;
		
		grandChild.parent = parent;
		child.parent = grandChild;
		criticalNode.parent = grandChild;
		if(grandChild.right != null)
			grandChild.right.parent = grandChild;
		if(grandChild.left != null)
			grandChild.left.parent = grandChild;
		if(child.left != null)
			child.left.parent = child;
		
		
		if(parent != null){
			if(parent.getLeft() == criticalNode){
				parent.left = grandChild;
			}else{
				parent.right = grandChild;
			}
			return root;
		}else{
			return grandChild; 
		}
	}
	
	/**
	 * 
	 * apply a leftRight rotation to the tree
	 * 
	 * @param root the root of this tree
	 * @param criticalNode the criticalNode to apply the rotation
	 * @return
	 */
	private synchronized NodeLowerTree leftRightRotate(NodeLowerTree root, NodeLowerTree criticalNode){
		NodeLowerTree parent = criticalNode.parent;
		NodeLowerTree child  = criticalNode.left;
		NodeLowerTree grandChild  = child.right;
		
		criticalNode.left = grandChild.right;
		child.right = grandChild.left;
		
		grandChild.right = criticalNode;
		grandChild.left = child;
		
		grandChild.parent = parent;
		child.parent = grandChild;
		criticalNode.parent = grandChild;
		if(grandChild.right != null)
			grandChild.right.parent = grandChild;
		if(grandChild.left != null)
			grandChild.left.parent = grandChild;
		if(child.right != null)
			child.right.parent = child;
		
		if(parent != null){
			if(parent.getLeft() == criticalNode){
				parent.left = grandChild;
			}else{
				parent.right = grandChild;
			}
			return root;
		}else{
			return grandChild; 
		}
	}
	
	/**
	 * 
	 * apply a rightRight rotation to the tree
	 * 
	 * @param root the root of this tree
	 * @param criticalNode the criticalNode to apply the rotation
	 * @return
	 */
	private synchronized NodeLowerTree rightRightRotate(NodeLowerTree root, NodeLowerTree criticalNode){
		NodeLowerTree parent = criticalNode.parent;
		NodeLowerTree child  = criticalNode.right;
		
		criticalNode.right = child.left;
		child.left 		   = criticalNode;
		
		if(criticalNode.right != null)
			criticalNode.right.parent = criticalNode;
		criticalNode.parent = child;
		
		child.parent = parent;
		if(parent != null){
			if(parent.left == criticalNode){
				parent.left = child;
			}else{
				parent.right = child;
			}
			return root;
		}else{
			return child; 
		}
	}
	
	
	/**
	 * 
	 * apply a leftLeft rotation to the tree
	 * 
	 * @param root the root of this tree
	 * @param criticalNode the criticalNode to apply the rotation
	 * @return
	 */
	private synchronized NodeLowerTree leftLeftRotate(NodeLowerTree root, NodeLowerTree criticalNode){
		NodeLowerTree parent = criticalNode.parent;
		NodeLowerTree child  = criticalNode.left;
		
		criticalNode.left = child.right;
		child.right       = criticalNode;
		
		if(criticalNode.left != null)
			criticalNode.left.parent = criticalNode;
		criticalNode.parent = child;
		
		child.parent = parent;
		if(parent != null){
			if(parent.right == criticalNode){
				parent.right = child;
			}else{
				parent.left = child;
			}
		}else{
			return child; 
		}
		return root;
	}
	
	
	/**
	 * find the node containing the specified label
	 * 
	 * @param val  the label to search in this tree
	 * @return the node containing the label searched
	 */
	public synchronized NodeLowerTree findNode( long val){
		return findNode(val,this);
	}
	private synchronized NodeLowerTree findNode( long val, NodeLowerTree current ){
		if(current == null)
			return null;
		if(current.label == val){
			return current;
		}else if(current.label < val){
			return findNode(val, current.right);
		}else{
			return findNode(val, current.left);
		}
	}

	
	/**
	 * find the parent of the node containing the specified label.
	 * If there are no node containing the specified label, 
	 * the returned value is the node at which a new node with the specified label inserted in the tree is attached
	 * 
	 * @param val  the label to search in this tree
	 * @return 
	 */
	public synchronized NodeLowerTree findParentNode( long val ){
		return findParentNode( val, this);
	}
	
	private synchronized NodeLowerTree findParentNode( long val, NodeLowerTree current ){
		if(current == null)
			return null;
		
		NodeLowerTree ret=null;
		if(current.label == val)
			return null;
		
		if(val > current.label){
			ret = findParentNode(val, current.right);
			if(ret == null)
				return current;
			return ret;
		}else{
			ret = findParentNode(val, current.left);
			if(ret == null)
				return current;
			return ret;
		}
	}
	
	
	/**
	 * copy one node inside this and then insert the other subtree and balance this
	 * 
	 * @param left
	 * @param right
	 */
	public synchronized NodeLowerTree merge(NodeLowerTree right){
		NodeLowerTree ret = null;
		if(this._add(right)){
			ret = this.balanceTree();
			if(ret != null){
				if(ret.updateHash())
					return ret;
			}
		
		}
		return null;
	}
	
	
	public synchronized int getNumElements(){
		return numElement;
	}
	
	/**
	 * 
	 * @return the left child
	 */
	public synchronized NodeLowerTree getLeft(){
		return left;
	}
	/**
	 * 
	 * @return the right child
	 */
	public synchronized NodeLowerTree getRight(){
		return right;
	}
	
	/**
	 * balance the tree after an insertion as been done
	 * @param insertedNode the node that is just inserted in the tree
	 * @return the new root of the tree
	 */
	private synchronized NodeLowerTree balanceTreeAfterInsertion(NodeLowerTree insertedNode){
		NodeLowerTree critical = null;
		NodeLowerTree root = this;
		
		
		height();	
		while((critical = findCriticalNode(insertedNode)) != null){
			root = applyRotation(root,critical);
			height();
		}
		
		return root;
	}
	
	private synchronized NodeLowerTree applyRotation( NodeLowerTree root ,NodeLowerTree critical) {
		
		int leftH = 0,rightH = 0;
		if(critical.left != null) leftH = critical.left.height;
		if(critical.right != null) rightH = critical.right.height;
		
		if(leftH > rightH){
			//rotateLeft
			
			int leftLeftH = 0,leftRightH = 0;
			if(critical.left.left != null) leftLeftH = critical.left.left.height;
			if(critical.left.right != null) leftRightH = critical.left.right.height;
			
			if(leftLeftH > leftRightH)
				root = leftLeftRotate(root,critical);//rotateLeftLeft
			else
				root = leftRightRotate(root,critical);//rotateLeftRight
		}
		else{
			//rotateRight

			int rightLeftH = 0,rightRightH = 0;
			if(critical.right.left != null) rightLeftH = critical.right.left.height;
			if(critical.right.right != null) rightRightH = critical.right.right.height;
			
			if(rightLeftH > rightRightH)
				root = rightLeftRotate(root,critical);//rotateRightLeft
			else
				root = rightRightRotate(root,critical);//rotateRightRight
		}
		return root;
		
	}

	/**
	 * find the critical node starting from the insertedNode going up to the root
	 * @param insertedNode
	 * @return
	 */
	private synchronized NodeLowerTree findCriticalNode(NodeLowerTree insertedNode){
		int leftH = 0;
		int rightH= 0;
		if(insertedNode.left != null)
			leftH = insertedNode.left.height;
		if(insertedNode.right != null)
			rightH = insertedNode.right.height;

		if(leftH - rightH > 1 || leftH - rightH < -1 ){
			return insertedNode;
		}
		else{
			if(insertedNode.parent != null){
				return findCriticalNode(insertedNode.parent);
			}
			return null;
		}
	
	}
	
	/**
	 * find the critical node starting from the root and going down
	 * @param node
	 * @return
	 */
	private synchronized NodeLowerTree findCriticalNodeFromTop(NodeLowerTree node){
		NodeLowerTree ret = null;
		int leftH = 0;
		int rightH= 0;
		if(node.left != null)
			leftH = node.left.height;
		if(node.right != null)
			rightH = node.right.height;

		if(leftH - rightH > 1 || leftH - rightH < -1 ){
			return node;
		}
		else{
			if(node.left != null){
				ret = findCriticalNode(node.left);
			}
			if(node.right != null && ret == null){
				ret = findCriticalNode(node.right);
			}
			return ret;
		}
	
	}
	
	/**
	 * add a node and rebalance the tree
	 * @param node the node to insert
	 * @return the new root of the tree
	 */
	public synchronized NodeLowerTree add(NodeLowerTree node){
		if(! _add(node))
			return null;
		NodeLowerTree tmp = null;
		if((tmp=balanceTreeAfterInsertion(node))!= null)
			if( tmp.updateHash())
				return tmp;
		
		return null;
	}
	
	/**
	 * add (without rebalancing the tree) a node to the tree
	 * @param node the node to insert
	 */
	private synchronized boolean _add(NodeLowerTree node){
		if(node == null ) 
			return true;
		if(node.label > this.label){
			if(this.right != null){
				this.right._add(node);
			}
			else{
				this.right = node;
				node.parent = this;
			}			
			
		} else if(node.label < this.label){
			if(this.left != null){
				this.left._add(node);
			}
			else{
				this.left = node;
				node.parent = this;
			}

		} else {
			PadFsLogger.log(LogLevel.ERROR,"Try to add an already existing label to the tree!");
			return false;
		}			
		return true;
	}
	
	/**
	 * update the hash of the current node taking into account the current hash and the hashes of the children 
	 */
	private synchronized boolean updateHash(){
		HashValue leftHash = null;
		HashValue rightHash = null;
		if(left != null)
			leftHash = left.hash;
		if(right != null)
			rightHash = right.hash;
		return this.hash.updateHash(leftHash, rightHash);
	}
	
	/**
	 * 
	 * @return the hash value of this node
	 */
	public synchronized HashValue getHash(){
		return hash;
	}
	
	/**
	 * add a new value with the same label to the current hash
	 * @param label
	 * @param updatesNumber
	 * @param path
	 * @param size
	 * @param idOwner
	 */
	public synchronized boolean addExistingHash(Long label, Integer updatesNumber,
			String path, long size, int idOwner) {
		if(this.hash != null){
			this.numElement++;
			return this.hash.add(label, updatesNumber, path, size, idOwner);
		}else{
			PadFsLogger.log(LogLevel.FATAL, "HASH VALUE IS NULL!");
			return false;
		}
		
	}
	
	public synchronized boolean removeExistingHash(Long label, Integer updatesNumber,
			String path, long size, int idOwner) {
		
		if(this.hash != null){
			this.numElement--;
			return this.hash.sub(label, updatesNumber, path, size, idOwner);
		}else{
			PadFsLogger.log(LogLevel.FATAL, "HASH VALUE IS NULL!");
			return false;
		}
	}


	/**
	 * measure and update the height of this tree
	 * @return the height of the tree
	 */
	public synchronized int height(){
		int leftH = 0;
		int rightH= 0;
		if(this.left != null)
			leftH = this.left.height();
		if(this.right != null)
			rightH = this.right.height();

		if(leftH > rightH)
			this.height = 1 + leftH;
		else
			this.height = 1 + rightH;
		return this.height;
	}
	
	/**
	 * print the tree
	 */
	public synchronized void printLabelTree() {
		printLabelTree(this);
		System.out.print("\n");
	}
	
	/** 
	 * recursively print the tree
	 * @param n the current node in the recursion
	 */
	private synchronized void printLabelTree(NodeLowerTree n){
		if(n != null){
			printLabelTree(n.left);
			System.out.print(n.label+" ");
			printLabelTree(n.right);
		}
		
	}
	
	
	

static class TreePrinter {

    public static <T extends Comparable<?>> void printNode(NodeLowerTree root) {
        int maxLevel = TreePrinter.maxLevel(root);
        printNodeInternal(Collections.singletonList(root), 1, maxLevel);
    }

	private static <T extends Comparable<?>> void printNodeInternal(List<NodeLowerTree> nodes, int level, int maxLevel) {
        if (nodes.isEmpty() || TreePrinter.isAllElementsNull(nodes))
            return;

        int floor 		  = maxLevel - level;
        int endgeLines 	  = (int) Math.pow(2, (Math.max(floor - 1, 0)));
        int firstSpaces   = (int) Math.pow(2, (floor)) - 1;
        int betweenSpaces = (int) Math.pow(2, (floor + 1)) - 1;

        TreePrinter.printWhitespaces(firstSpaces);

        List<NodeLowerTree> newNodes = new ArrayList<NodeLowerTree>();
        for (NodeLowerTree node : nodes) {
            if (node != null) {
                System.out.print(node.label);
                newNodes.add(node.left);
                newNodes.add(node.getRight());
            } else {
                newNodes.add(null);
                newNodes.add(null);
                System.out.print(" ");
            }

            TreePrinter.printWhitespaces(betweenSpaces);
        }
        System.out.println("");

        for (int i = 1; i <= endgeLines; i++) {
            for (int j = 0; j < nodes.size(); j++) {
                TreePrinter.printWhitespaces(firstSpaces - i);
                if (nodes.get(j) == null) {
                    TreePrinter.printWhitespaces(endgeLines + endgeLines + i + 1);
                    continue;
                }

                if (nodes.get(j).left != null)
                    System.out.print("/");
                else
                    TreePrinter.printWhitespaces(1);

                TreePrinter.printWhitespaces(i + i - 1);

                if (nodes.get(j).getRight() != null)
                    System.out.print("\\");
                else
                    TreePrinter.printWhitespaces(1);

                TreePrinter.printWhitespaces(endgeLines + endgeLines - i);
            }

            System.out.println("");
        }

        printNodeInternal(newNodes, level + 1, maxLevel);
    }

    private static void printWhitespaces(int count) {
        for (int i = 0; i < count; i++)
            System.out.print(" ");
    }
    
    
    private static int maxLevel(NodeLowerTree node) {
    	if (node == null)
            return 0;

        return Math.max(TreePrinter.maxLevel(node.left), TreePrinter.maxLevel(node.getRight())) + 1;
	}

    private static <T> boolean isAllElementsNull(List<NodeLowerTree> nodes) {
        for (NodeLowerTree t : nodes) {
            if (t != null)
                return false;
        }

        return true;
    }

}




	
}
