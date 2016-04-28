/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.duke.cs.osprey.partitionfunctionbounds;

import edu.duke.cs.osprey.astar.AStarNode;
import edu.duke.cs.osprey.astar.AStarTree;
import edu.duke.cs.osprey.astar.comets.UpdatedPruningMatrix;
import edu.duke.cs.osprey.astar.kadee.SequenceNode;
import edu.duke.cs.osprey.astar.partfunc.PartFuncTree;
import edu.duke.cs.osprey.confspace.RCTuple;
import edu.duke.cs.osprey.confspace.SearchProblem;
import edu.duke.cs.osprey.control.ConfigFileParser;
import edu.duke.cs.osprey.pruning.PruningControl;
import edu.duke.cs.osprey.pruning.PruningMatrix;
import edu.duke.cs.osprey.structure.Molecule;
import edu.duke.cs.osprey.structure.Residue;
import edu.duke.cs.osprey.tools.StringParsing;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 *
 * @author hmn5
 */
public class SequenceFreeEnergy extends AStarTree {

    SearchProblem[] searchSpaces;
    boolean[] spIndex2IsMutable;
    public ArrayList<ArrayList<String>> aaTypeOptions;
    ArrayList<Integer> mutable2PosNums;

    int numLevels;

    boolean optimizeBoundState = true;
    SearchProblem spToOptimize;
    int spToOptimizeIndex;
    double epsilon = 0.1;

    public SequenceFreeEnergy(ConfigFileParser cfp) {
        setupConfigs(cfp);
        this.numLevels = aaTypeOptions.size();
        SearchProblem sp;
        if (optimizeBoundState) {
            spToOptimize = searchSpaces[0];
            spToOptimizeIndex = 0;
        } else {
            this.spToOptimizeIndex = spIndex2IsMutable[1] ? 1 : 2;
            spToOptimize = searchSpaces[spToOptimizeIndex];
        }
    }

    @Override
    public ArrayList<AStarNode> getChildren(AStarNode curNode) {
        SequenceNode seqNode = (SequenceNode) curNode;
        ArrayList<AStarNode> ans = new ArrayList<>();

        printPartialSequence(seqNode);
        //expand next position...
        int[] curAssignments = seqNode.getNodeAssignments();

        for (int splitPos = 0; splitPos < numLevels; splitPos++) {
            if (curAssignments[splitPos] < 0) {//we can split this level

                for (int aa = 0; aa < aaTypeOptions.get(splitPos).size(); aa++) {
                    int[] childAssignments = curAssignments.clone();
                    childAssignments[splitPos] = aa;
                    UpdatedPruningMatrix childPruneMat = doChildPruning(seqNode.pruneMat, splitPos, aa);

                    SequenceNode childNode = new SequenceNode(childAssignments, childPruneMat);

                    childNode.setScore(boundFreeEnergy(childNode));
                    ans.add(childNode);
                }

                return ans;
            }
        }

        throw new RuntimeException("ERROR: Not splittable position found but sequence not fully defined...");
    }

    private void printPartialSequence(SequenceNode seqNode) {
        int[] assignments = seqNode.getNodeAssignments();
        for (int pos = 0; pos < this.numLevels; pos++) {
            if (assignments[pos] > 0){
                String aatype = this.aaTypeOptions.get(pos).get(assignments[pos]);
                System.out.print(aatype+" ");
            }
            else{
                System.out.print("XXX ");
            }
        }
        System.out.println();
    }

    @Override
    public boolean isFullyAssigned(AStarNode node) {
        return node.isFullyDefined();
    }

    @Override
    public AStarNode rootNode() {
        int[] conf = new int[numLevels];
        Arrays.fill(conf, -1);//indicates the sequence is not assigned

        PruningMatrix pruneMat = spToOptimize.pruneMat;

        SequenceNode root = new SequenceNode(conf, pruneMat);

        root.setScore(boundFreeEnergy(root));
        return root;
    }

    private double boundFreeEnergy(SequenceNode seqNode) {
        double bound = computeBoundVariationalInf(seqNode);
        return bound;
    }

    private double computeBoundVariationalInf(SequenceNode seqNode) {
        PartFuncTree tree = new PartFuncTree(this.spToOptimize.emat, seqNode.pruneMat);
        double logZUB = tree.computeEpsilonApprox(this.epsilon);
        return logZUB;
    }

    /**
     * Prunes rotamers to reflex the new allowed amino-acids
     *
     * @param state bound vs unbound state
     * @param parentMat parent pruning matrix
     * @param splitPos position that was split
     * @param aa amino acid label for new positions that was split
     * @return updated pruning matrix
     */
    private UpdatedPruningMatrix doChildPruning(PruningMatrix parentMat, int splitPos, int aa) {
        //Create an update to parentMat (without changing parentMat)
        //to reflect that splitPos has been assigned an amino-acid type

        String assignedAAType = this.aaTypeOptions.get(splitPos).get(aa);

        UpdatedPruningMatrix ans = new UpdatedPruningMatrix(parentMat);
        int posAtState = mutable2PosNums.get(splitPos);

        //first, prune all other AA types at splitPos
        for (int rc : parentMat.unprunedRCsAtPos(posAtState)) {
            //HUNTER: TODO: AATYperPerRes should only be one residue for now
            //We should change this to allow for rcs at the sequence search level
            String rcAAType = spToOptimize.confSpace.posFlex.get(posAtState).RCs.get(rc).AAType;

            if (!rcAAType.equalsIgnoreCase(assignedAAType)) {
                ans.markAsPruned(new RCTuple(posAtState, rc));
            }
        }

        return ans;
    }

    private void setupConfigs(ConfigFileParser cfp) {
        searchSpaces = cfp.getMSDSearchProblems();
        for (SearchProblem sp : searchSpaces) {
            loadEMatandPrune(sp, Double.POSITIVE_INFINITY, cfp);
        }

        ArrayList<ArrayList<ArrayList<String>>> allowedAAPerSP = getAllowedAAPerSP(cfp);
        ArrayList<ArrayList<Integer>> mutablePosNumsPerSP = handleMutable2StatePosNums(allowedAAPerSP);
        this.spIndex2IsMutable = new boolean[searchSpaces.length];
        for (int spIndex = 0; spIndex < this.searchSpaces.length; spIndex++) {
            if (mutablePosNumsPerSP.get(spIndex).size() > 0) {
                spIndex2IsMutable[spIndex] = true;
            } else {
                spIndex2IsMutable[spIndex] = false;
            }
        }
        ArrayList<ArrayList<ArrayList<String>>> allowedAAPerMutableSP = new ArrayList<>();
        for (int spIndex = 0; spIndex < this.searchSpaces.length; spIndex++) {
            if (spIndex2IsMutable[spIndex]) {
                allowedAAPerMutableSP.add(allowedAAPerSP.get(spIndex));
            }
        }
        if (optimizeBoundState) {
            mutable2PosNums = mutablePosNumsPerSP.get(0);
        } else {
            int unboundMutSpIndex = spIndex2IsMutable[1] ? 1 : 2;
            mutable2PosNums = mutablePosNumsPerSP.get(unboundMutSpIndex);
        }
        this.aaTypeOptions = handleAATypeOptions(allowedAAPerMutableSP);
    }

    /**
     * Get a list of mutable posNums for each search problem If the state has no
     * mutable positions, and empty list is given
     *
     * @param allowedAAPerState
     * @return
     */
    private ArrayList<ArrayList<Integer>> handleMutable2StatePosNums(ArrayList<ArrayList<ArrayList<String>>> allowedAAPerState) {
        ArrayList<ArrayList<Integer>> mutable2StatePosNum = new ArrayList<>();

        for (int state = 0; state < allowedAAPerState.size(); state++) {
            ArrayList<ArrayList<String>> allowedAAForState = allowedAAPerState.get(state);
            ArrayList<Integer> mutablePositionsForState = getMutablePosNums(allowedAAForState);
            mutable2StatePosNum.add(mutablePositionsForState);
        }

        return mutable2StatePosNum;
    }

    /**
     * For a particular state, get the mutable posNums, if any exist
     *
     * @param allowedAAForState
     * @return
     */
    private ArrayList<Integer> getMutablePosNums(ArrayList<ArrayList<String>> allowedAAForState) {
        ArrayList<Integer> mutablePosNum = new ArrayList<>();
        for (int posNum = 0; posNum < allowedAAForState.size(); posNum++) {
            if (allowedAAForState.get(posNum).size() > 1) {
                mutablePosNum.add(posNum);
            }
        }
        return mutablePosNum;
    }

    /**
     * Get the allowed amino acids per mutable position
     *
     * @param mutableStateAllowedAAs
     * @return
     */
    private ArrayList<ArrayList<String>> handleAATypeOptions(ArrayList<ArrayList<ArrayList<String>>> mutableStateAllowedAAs) {
        ArrayList<ArrayList<String>> AATypeOptions = mutableStateAllowedAAs.get(0).stream().filter(aaTypes -> aaTypes.size() > 1).collect(Collectors.toCollection(ArrayList::new));

        for (int state = 1; state < mutableStateAllowedAAs.size(); state++) {
            ArrayList<ArrayList<String>> AATypesForState = mutableStateAllowedAAs.get(state).stream().filter(aaTypes -> aaTypes.size() > 1).collect(Collectors.toCollection(ArrayList::new));
            if (AATypesForState.size() != AATypeOptions.size()) {
                throw new RuntimeException("ERROR: Different Number of Mutable Positions between Bound and Unbound");
            }
            for (int posNum = 0; posNum < AATypesForState.size(); posNum++) {
                ArrayList<String> AATypesForPos = AATypesForState.get(posNum);
                for (int aaIndex = 0; aaIndex < AATypesForPos.size(); aaIndex++) {
                    if (!(AATypeOptions.get(posNum).contains(AATypesForPos.get(aaIndex)))) {
                        throw new RuntimeException("ERROR: AAType Different for Bound and Unbound Mutable Residues");
                    }
                }
            }
        }
        return AATypeOptions;
    }

    /**
     * For each search problem, is the search problem mutable (True) or not
     * (False)
     *
     * @param allowedAAPerSP
     * @return
     */
    boolean[] getSearchProbIndex2IsMutable(ArrayList<ArrayList<ArrayList<String>>> allowedAAPerSP) {
        boolean[] spIndex2IsMutable = new boolean[this.searchSpaces.length];
        Arrays.fill(spIndex2IsMutable, false);

        for (int spIndex = 0; spIndex < searchSpaces.length; spIndex++) {
            ArrayList<ArrayList<String>> allowedAAs = allowedAAPerSP.get(spIndex);
            for (int pos = 0; pos < allowedAAs.size(); pos++) {
                if (allowedAAs.get(pos).size() > 1) {
                    spIndex2IsMutable[spIndex] = true;
                    break;
                }
            }
        }
        return spIndex2IsMutable;
    }

    /**
     * Get the allowed amino acids at each position for each search problem
     *
     * @param cfp
     * @return
     */
    private ArrayList<ArrayList<ArrayList<String>>> getAllowedAAPerSP(ConfigFileParser cfp) {
        ArrayList<ArrayList<ArrayList<String>>> allowedAAPerSP = new ArrayList<>();
        for (int spNum = 0; spNum < searchSpaces.length; spNum++) {
            allowedAAPerSP.add(getAllowedAA(spNum, cfp));
        }
        return allowedAAPerSP;
    }

    /**
     * Get the allowed amino acids at each position for a particular state
     *
     * @param state
     * @param cfp
     * @return
     */
    private ArrayList<ArrayList<String>> getAllowedAA(int state, ConfigFileParser cfp) {
        ArrayList<ArrayList<String>> complexAllowedAAs = cfp.getAllowedAAs();
        ArrayList<String> complexFlexRes = cfp.getFlexRes();
        int numFlexRes = complexFlexRes.size();
        int beginPos = -1;
        int endPos = -1;
        if (state == 0) {
            beginPos = 0;
            endPos = numFlexRes;
        } else if (state == 1) {
            beginPos = 0;
            endPos = this.searchSpaces[1].confSpace.posFlex.size();
        } else {
            beginPos = this.searchSpaces[1].confSpace.posFlex.size();
            endPos = beginPos + this.searchSpaces[2].confSpace.posFlex.size();
        }
        ArrayList<ArrayList<String>> allowedAAs = new ArrayList<>();
        Molecule wtMolec = this.searchSpaces[0].confSpace.m;

        for (int posNum = beginPos; posNum < endPos; posNum++) {
            ArrayList<String> currentAAOptions = complexAllowedAAs.get(posNum);
            if (cfp.params.getBool("AddWT")) {
                Residue res = wtMolec.getResByPDBResNumber(complexFlexRes.get(posNum));
                if (!StringParsing.containsIgnoreCase(complexAllowedAAs.get(posNum), res.template.name)) {
                    currentAAOptions.add(res.template.name);
                }
            }
            if (currentAAOptions.isEmpty()) {
                throw new RuntimeException("ERROR: No AAtype for Position: " + posNum);
            }
            allowedAAs.add(currentAAOptions);
        }
        return allowedAAs;
    }

    //For quality control, it's good to be able to check KaDEE results by exhaustive search
    public void exhaustiveSearch() {

        System.out.println();
        System.out.println("CHECKING Sequence Free Energy RESULT BY EXHAUSTIVE SEARCH");
        System.out.println();

        ArrayList<String[]> seqList = listAllSeqs();
        int numSeqs = seqList.size();
        double[] sequenceScore = new double[numSeqs];

        for (int seqNum = 0; seqNum < numSeqs; seqNum++) {
            String[] sequence = seqList.get(seqNum);

            UpdatedPruningMatrix upm = handleExhaustivePruning(sequence);

            PartFuncTree tree = new PartFuncTree(spToOptimize.emat, upm);
            sequenceScore[seqNum] = tree.computeEpsilonApprox(epsilon);
        }

        //now find the best sequence and obj fcn value
        int topSeqNum = -1;
        double bestSeqScore = Double.POSITIVE_INFINITY;

        for (int seqNum = 0; seqNum < numSeqs; seqNum++) {

            double seqScore = sequenceScore[seqNum];
            if (seqScore < bestSeqScore) {
                bestSeqScore = seqScore;
                topSeqNum = seqNum;
            }
        }

        System.out.println("EXHAUSTIVE MULTISTATE BEST SCORE: " + bestSeqScore + " SEQUENCE: ");
        for (String aa : seqList.get(topSeqNum)) {
            System.out.print(aa + " ");
        }
        System.out.println();
    }

    private ArrayList<String[]> listAllSeqs() {
        //List all possible sequence for the mutable residues,
        //based on AATypeOptions
        return listAllSeqsHelper(0);
    }

    private ArrayList<String[]> listAllSeqsHelper(int mutPos) {
        //List all partial sequences for the subset of mutable positions
        //starting at mutPos and going to the last mutable position
        ArrayList<String[]> ans = new ArrayList<>();

        if (mutPos == this.numLevels) {
            ans.add(new String[0]);
        } else {
            ArrayList<String[]> subList = listAllSeqsHelper(mutPos + 1);
            for (String AAType : aaTypeOptions.get(mutPos)) {
                for (String[] subSeq : subList) {
                    String seq[] = new String[numLevels - mutPos];
                    System.arraycopy(subSeq, 0, seq, 1, numLevels - mutPos - 1);
                    seq[0] = AAType;
                    ans.add(seq);
                }
            }
        }

        return ans;
    }

    private UpdatedPruningMatrix handleExhaustivePruning(String[] sequence) {
        UpdatedPruningMatrix upm = new UpdatedPruningMatrix(this.spToOptimize.pruneMat);
        for (int mutPos = 0; mutPos < numLevels; mutPos++) {
            String assignedAAType = sequence[mutPos];

            int posNum = mutable2PosNums.get(mutPos);
            //first, prune all other AA types at splitPos
            for (int rc : spToOptimize.pruneMat.unprunedRCsAtPos(posNum)) {
                String rcAAType = spToOptimize.confSpace.posFlex.get(posNum).RCs.get(rc).AAType;

                if (!rcAAType.equalsIgnoreCase(assignedAAType)) {
                    upm.markAsPruned(new RCTuple(posNum, rc));
                }
            }
        }
        return upm;

    }

    //Loads energy matrices and prune 
    private void loadEMatandPrune(SearchProblem searchProb, double pruningInterval, ConfigFileParser cfp) {
        System.out.println("Precomputing Energy Matrix for " + searchProb.name + " state");
        searchProb.loadEnergyMatrix();

        System.out.println("Initializing Pruning for " + searchProb.name + " state");
        initializePruning(searchProb, cfp);
        PruningControl pruning = cfp.setupPruning(searchProb, pruningInterval, false, false);
        pruning.prune();
    }

    private void initializePruning(SearchProblem searchProblem, ConfigFileParser cfp) {
        //Creates an efficient competitor pruning matrix
        searchProblem.competitorPruneMat = null;
        System.out.println("PRECOMPUTING COMPETITOR PRUNING MATRIX");
        //prune with 0 interval, anything that survives will be added as a competitor
        PruningControl compPruning = cfp.setupPruning(searchProblem, Double.POSITIVE_INFINITY, false, false);
        compPruning.setOnlyGoldstein(true);
        compPruning.prune();
        searchProblem.competitorPruneMat = searchProblem.pruneMat;
        searchProblem.pruneMat = null;
        System.out.println("COMPETITOR PRUNING DONE");
    }

}
