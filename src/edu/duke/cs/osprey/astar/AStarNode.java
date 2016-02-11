/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.duke.cs.osprey.astar;

/**
 *
 * @author mhall44
 */
public class AStarNode implements Comparable {
    
    int nodeAssignments[];//assignments (e.g. partial conformation) for node
    
    double score;//score (probably a lower bound on the energy)
    
    public double perturbation;//useful for GumbelMap (HMN)
    public int[] feasibleSolution; //useful for GumbelMap (HMN) for now this is a random feasible solution
    
    boolean scoreNeedsRefinement;

    boolean isRoot = false; //HMN: Temporary

    
    //These are used in COMETS
    public double UB = Double.POSITIVE_INFINITY;//upper bound
    public int UBConf[] = null;//can have an upper bound on GMEC energy for this node's conf space
    //(and thus on the overall GMEC energy)
    //that is the energy of the conf denoted by UBConf
    
    
    
    //indicates the score needs to be refined (e.g. with EPIC continuous terms)
    //always false in simpler versions of A*
    public AStarNode(int[] nodeAssignments, double score, boolean scoreNeedsRefinement) {
        this.nodeAssignments = nodeAssignments;
        this.score = score;
        this.scoreNeedsRefinement = scoreNeedsRefinement;
    }

    @Override
    public int compareTo(Object o) {
        AStarNode node2 = (AStarNode)o;//we can only compare to other AStarNodes, and expect no other cases
        return Double.valueOf(score).compareTo(node2.score);
    }

    public int[] getNodeAssignments() {
        return nodeAssignments;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public double getScore() {
        return score;
    }
    
    
    
    
    public boolean isFullyDefined(){
        //Assuming assignments greater than 0 denote fully defined positions,
        //determine if this node is fully defined or not
        for(int a : nodeAssignments){
            if(a<0)
                return false;
        }
        return true;
    }
    
    
}
