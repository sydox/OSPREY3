/*
** This file is part of OSPREY 3.0
** 
** OSPREY Protein Redesign Software Version 3.0
** Copyright (C) 2001-2018 Bruce Donald Lab, Duke University
** 
** OSPREY is free software: you can redistribute it and/or modify
** it under the terms of the GNU General Public License version 2
** as published by the Free Software Foundation.
** 
** You should have received a copy of the GNU General Public License
** along with OSPREY.  If not, see <http://www.gnu.org/licenses/>.
** 
** OSPREY relies on grants for its development, and since visibility
** in the scientific literature is essential for our success, we
** ask that users of OSPREY cite our papers. See the CITING_OSPREY
** document in this distribution for more information.
** 
** Contact Info:
**    Bruce Donald
**    Duke University
**    Department of Computer Science
**    Levine Science Research Center (LSRC)
**    Durham
**    NC 27708-0129
**    USA
**    e-mail: www.cs.duke.edu/brd/
** 
** <signature of Bruce Donald>, Mar 1, 2018
** Bruce Donald, Professor of Computer Science
*/

package edu.duke.cs.osprey.energy;

import edu.duke.cs.osprey.confspace.*;
import edu.duke.cs.osprey.ematrix.UpdatingEnergyMatrix;
import edu.duke.cs.osprey.energy.forcefield.ForcefieldParams;
import edu.duke.cs.osprey.kstar.TestKStar;
import edu.duke.cs.osprey.restypes.ResidueTemplateLibrary;
import edu.duke.cs.osprey.sharkstar.TestSHARKStar;
import edu.duke.cs.osprey.structure.Molecule;
import edu.duke.cs.osprey.structure.PDBIO;
import edu.duke.cs.osprey.tools.FileTools;
import org.junit.Test;

import java.io.FileNotFoundException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static edu.duke.cs.osprey.sharkstar.TestSHARKStar.loadFromCFS;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class TestUpdatingEnergyMatrix {
    public static final int NUM_FLEX = 10;
    public static final int NUM_TUPS = 100;

    @Test
    public void testTupleTrieRandom()
    {
        SimpleConfSpace confSpace = make1GUASmall(NUM_FLEX);
        UpdatingEnergyMatrix.TupleTrie trie = new UpdatingEnergyMatrix.TupleTrie(confSpace.positions);
        runManual(trie, makeManualTupE());
        for(int i = 0; i < NUM_TUPS; i++)
        {
            TupE tupE = makeRandomTupE(confSpace);
            System.out.println("Inserting "+tupE.tup.stringListing()+":"+tupE.E);
            trie.insert(tupE);
            assert(trie.contains(tupE.tup));
            List<TupE> corrections = trie.getCorrections(tupE.tup);
            for(TupE tupEout: corrections)
                System.out.println("Retrieved "+tupEout.tup.stringListing()+":"+tupEout.E);

        }
    }

    @Test
    public void testTupleTrieManual () {
        SimpleConfSpace confSpace = make1GUASmall(4);
        UpdatingEnergyMatrix.TupleTrie trie = new UpdatingEnergyMatrix.TupleTrie(confSpace.positions);
        runManual(trie, makeManualTupE());
    }

    @Test
    public void testTupleTrieManual2 () {
        try {
            SimpleConfSpace mutableConfSpace = loadFromCFS("test-resources/3ma2_A_6res_3.157E+06.cfs").complex;
            UpdatingEnergyMatrix.TupleTrie trie = new UpdatingEnergyMatrix.TupleTrie(mutableConfSpace.positions);
            runManual(trie, makeManualTupE2());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

    }

    @Test
    public void testGetAllCorrections(){
        SimpleConfSpace confSpace = make1GUASmall(4);
        UpdatingEnergyMatrix.TupleTrie trie = new UpdatingEnergyMatrix.TupleTrie(confSpace.positions);
        runManual(trie, makeManualTupE());

        for(int i = 0; i < NUM_TUPS; i++)
        {
            TupE tupE = makeRandomTupE(confSpace);
            System.out.println("Inserting "+tupE.tup.stringListing()+":"+tupE.E);
            trie.insert(tupE);
        }

        List<TupE> corrections = trie.getAllCorrections();
        assertThat(corrections.size(), is(trie.size()));
        System.out.println("Retrieved "+trie.size()+" corrections.");
    }

    private void runManual(UpdatingEnergyMatrix.TupleTrie trie, List<TupE> tupleList) {
        for(TupE tupE : tupleList) {
            System.out.println("Inserting "+tupE.tup.stringListing()+":"+tupE.E);
            trie.insert(tupE);
            List<TupE> corrections = trie.getCorrections(tupE.tup);
            for(TupE tupEout: corrections)
                System.out.println("Retrieved "+tupEout.tup.stringListing()+":"+tupEout.E);
        }
    }

    private List<TupE> makeManualTupE() {
        List<TupE> out = new ArrayList<>();
        RCTuple tup4 = new RCTuple();
        tup4 = tup4.addRC(3, 20);
        double energy4 = Math.random() * -40;
        out.add(new TupE(tup4, energy4));
        RCTuple tup2 = new RCTuple();
        tup2 = tup2.addRC(2, 15);
        tup2 = tup2.addRC(3, 20);
        double energy2 = Math.random() * -40;
        out.add(new TupE(tup2, energy2));
        RCTuple tup3 = new RCTuple();
        tup3 = tup3.addRC(0, 15);
        tup3 = tup3.addRC(3, 20);
        double energy3 = Math.random() * -40;
        out.add(new TupE(tup3, energy3));
        RCTuple tup = new RCTuple();
        tup = tup.addRC(0, 15);
        tup = tup.addRC(2, 15);
        tup = tup.addRC(3, 20);
        double energy = Math.random() * -40;
        out.add(new TupE(tup, energy));
        return out;
    }

    private List<TupE> makeManualTupE2() {
        List<TupE> out = new ArrayList<>();
        out.add(makeTuple(new int[]{0,1,2,3,4,5},
                new int[]{8,8,0,1,4,3},
                Math.random() *-40));
        out.add(makeTuple(new int[]{1, 2, 3, 4, 5},
                new int[]{8, 0, 1, 4, 3},
                Math.random() *-40));
        return out;
    }

    private TupE makeTuple(int[] pos, int[] RCs, double energy) {
        RCTuple tuple = new RCTuple();
        for(int i = 0; i < pos.length; i++) {
            tuple = tuple.addRC(pos[i], RCs[i]);
        }
        return new TupE(tuple, energy);
    }

    private TupE makeRandomTupE(SimpleConfSpace space) {
        RCTuple tup = new RCTuple();
        for(SimpleConfSpace.Position pos: space.positions) {
            int index = pos.index;
            if(Math.random() >= 0.5 || (pos.index == 3 && tup.size() < 1))
            tup = tup.addRC(index, (int) (Math.random() * 20));
        }
        double energy = Math.random()*-40;
        return new TupE(tup, energy);
    }

    private static SimpleConfSpace make1GUASmall(int numFlex) {


        // configure the forcefield
        ForcefieldParams ffparams = new ForcefieldParams();

        Molecule mol = PDBIO.read(FileTools.readResource("/1gua_adj.min.pdb"));

        // make sure all strands share the same template library
        ResidueTemplateLibrary templateLib = new ResidueTemplateLibrary.Builder(ffparams.forcefld)
                .build();

        // define the protein strand
        Strand protein = new Strand.Builder(mol)
                .setTemplateLibrary(templateLib)
                .setResidues("1", "180")
                .build();
        int start = 21;
        for(int i = start; i < start+numFlex; i++) {
            protein.flexibility.get(i+"").setLibraryRotamers(Strand.WildType).addWildTypeRotamers().setContinuous();
        }

        SimpleConfSpace proteinConfSpace = new SimpleConfSpace.Builder()
                .addStrand(protein)
                .build();

        return proteinConfSpace;
    }

    @Test
    public void testTupEFromString(){
        String tupString = "[5=8,6=8,7=4,8=5]->1.4466163083210901";
        TupE test = new TupE(tupString);
        System.out.println(test.toString());
    }
    @Test
    public void testReadFromFile(){
        try{
            TestKStar.ConfSpaces confSpaces = TestSHARKStar.loadFromCFS("test-resources/3bua_B_10res_4.363E+11.cfs");
            UpdatingEnergyMatrix.TupleTrie trie = new UpdatingEnergyMatrix.TupleTrie(confSpaces.complex.positions);
            trie.readCorrectionsFromFile("3bua_test_corrections.txt");
            System.out.println(String.format("Read %d corrections from file.", trie.size()));
        }catch(FileNotFoundException e){
            System.out.println(e.getMessage());
        }
    }
    @Test
    public void testWriteToFile(){
        try{
            TestKStar.ConfSpaces confSpaces = TestSHARKStar.loadFromCFS("test-resources/3bua_B_10res_4.363E+11.cfs");
            UpdatingEnergyMatrix.TupleTrie trie = new UpdatingEnergyMatrix.TupleTrie(confSpaces.complex.positions);
            trie.readCorrectionsFromFile("3bua_test_corrections.txt");
            System.out.println(String.format("Read %d corrections from file.", trie.size()));
            trie.writeCorrectionsToFile("test_corrections.txt");
        }catch(FileNotFoundException e){
            System.out.println(e.getMessage());
        }
    }

    @Test
    public void test3bua_weirdness_all(){
        try{
            TestKStar.ConfSpaces confSpaces = TestSHARKStar.loadFromCFS("test-resources/3bua_B_10res_4.363E+11.cfs");
            UpdatingEnergyMatrix.TupleTrie trie = new UpdatingEnergyMatrix.TupleTrie(confSpaces.complex.positions);
            trie.readCorrectionsFromFile("3bua_test_corrections.txt");
            System.out.println(String.format("Read %d corrections from file.", trie.size()));

            int[] parent = {4,24,-1,2,13,8,7,4,5,18};
            int[] child_normal = {4,24,126,2,13,8,7,4,5,18};
            int[] child_weird = {4,24,202,2,13,8,7,4,5,18};

            List<TupE> parentList = trie.getCorrections(new RCTuple(parent));
            List<TupE> childList = trie.getCorrections(new RCTuple(child_normal));
            List<TupE> weirdList = trie.getCorrections(new RCTuple(child_weird));

            parentList.sort(TupE::compareTo);
            childList.sort(TupE::compareTo);
            weirdList.sort(TupE::compareTo);

            System.out.println("Corrections for parent:");
            System.out.println(parentList.stream().map(TupE::toString_short).collect(Collectors.joining("\n")));
            System.out.println("Corrections for child:");
            System.out.println(childList.stream().map(TupE::toString_short).collect(Collectors.joining("\n")));
            System.out.println("Corrections for weird child:");
            System.out.println(weirdList.stream().map(TupE::toString_short).collect(Collectors.joining("\n")));


        }catch(FileNotFoundException e){
            System.out.println(e.getMessage());
        }
    }

    @Test
    public void test3bua_weirdness() {
        try {
            TestKStar.ConfSpaces confSpaces = TestSHARKStar.loadFromCFS("test-resources/3bua_B_10res_4.363E+11.cfs");
            UpdatingEnergyMatrix.TupleTrie trie = new UpdatingEnergyMatrix.TupleTrie(confSpaces.complex.positions);
            trie.readCorrectionsFromFile("3bua_test_corrections.txt");
            System.out.println(String.format("Read %d corrections from file.", trie.size()));

            int[] parent = {4, 24, -1, 2, 13, 8, 7, 4, 5, 18};
            int[] child_normal = {4, 24, 126, 2, 13, 8, 7, 4, 5, 18};
            int[] child_weird = {4, 24, 202, 2, 13, 8, 7, 4, 5, 18};

            List<TupE> parentList = trie.getCorrections(new RCTuple(parent));
            List<TupE> childList = trie.getCorrections(new RCTuple(child_normal));
            List<TupE> weirdList = trie.getCorrections(new RCTuple(child_weird));

            List<TupE> parentFinal = greedyGet(parentList, 9);
            List<TupE> childFinal = greedyGet(childList, 9);
            List<TupE> weirdFinal = greedyGet(weirdList, 9);

            System.out.println("Corrections for parent:");
            System.out.println(parentFinal.stream().map(TupE::toString_short).collect(Collectors.joining("\n")));
            System.out.println("Corrections for child:");
            System.out.println(childFinal.stream().map(TupE::toString_short).collect(Collectors.joining("\n")));
            System.out.println("Corrections for weird child:");
            System.out.println(weirdFinal.stream().map(TupE::toString_short).collect(Collectors.joining("\n")));

            System.out.println("And, the million dollar question: Could the weird child get a better energy by using the parent corrections?");
            if(weirdList.containsAll(parentFinal)){
                System.out.println("Yes, greedy is hurting us.");
            }else{
                System.out.println("No, greedy is fine.");
            }

        } catch (FileNotFoundException e) {
            System.out.println(e.getMessage());
        }
    }

    public List<TupE> greedyGet(List<TupE> corrections, int numPos){
        Collections.sort(corrections, (a, b)->-Double.compare(a.E,b.E));
        ArrayList<TupE> sumList = new ArrayList<>();
        double sum = 0;
        // Attempt 1: be greedy and start from the largest correction you
        // can get instead of trying to solve the NP-Complete problem.
        Set<Integer> usedPositions = new HashSet<>();
        List<TupE> usedCorrections = new ArrayList<>();
        int numApplied = 0;
        for(TupE correction: corrections) {
            if (usedPositions.size() >= numPos) {
                break;
            }
            Collection<Integer> positions = correction.tup.pos;
            boolean noIntersections = true;
            for(int position : positions) {
                if(usedPositions.contains(position)) {
                    noIntersections = false;
                    break;
                }
            }
            if(noIntersections) {
                usedPositions.addAll(correction.tup.pos);
                usedCorrections.add(correction);
                //System.out.println("Applying correction "+correction.tup.stringListing()+":"+correction.E);
                numApplied++;
                sum += correction.E;
                sumList.add(correction);
            }
        }
        return sumList;

    }
}
