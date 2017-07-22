package edu.duke.cs.osprey.gmec;

import static edu.duke.cs.osprey.TestBase.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.Test;

import edu.duke.cs.osprey.astar.conf.ConfAStarTree;
import edu.duke.cs.osprey.confspace.ConfSearch.EnergiedConf;
import edu.duke.cs.osprey.confspace.SimpleConfSpace;
import edu.duke.cs.osprey.confspace.Strand;
import edu.duke.cs.osprey.ematrix.EnergyMatrix;
import edu.duke.cs.osprey.ematrix.SimplerEnergyMatrixCalculator;
import edu.duke.cs.osprey.energy.ConfEnergyCalculator;
import edu.duke.cs.osprey.energy.EnergyCalculator;
import edu.duke.cs.osprey.energy.forcefield.ForcefieldParams;
import edu.duke.cs.osprey.externalMemory.ExternalMemory;
import edu.duke.cs.osprey.externalMemory.Queue;
import edu.duke.cs.osprey.structure.Molecule;
import edu.duke.cs.osprey.structure.PDBIO;

public class TestSimpleGMECFinder {
	
	private static final double EnergyEpsilon = 1e-6;
	
	private static class Problem {
		
		public final SimpleConfSpace confSpace;
		public final ForcefieldParams ffparams;
		public final EnergyCalculator ecalc;
		public final ConfEnergyCalculator confEcalc;
		public final EnergyMatrix emat;
		
		public Problem(Strand ... strands) {
			confSpace = new SimpleConfSpace.Builder().addStrands(strands).build();
			ffparams = new ForcefieldParams();
			ecalc = new EnergyCalculator.Builder(confSpace, ffparams).build();
			confEcalc = new ConfEnergyCalculator.Builder(confSpace, ecalc).build();
			emat = new SimplerEnergyMatrixCalculator.Builder(confSpace, ecalc)
				.build()
				.calcEnergyMatrix();
		}
		
		public SimpleGMECFinder.Builder makeBuilder() {
			return new SimpleGMECFinder.Builder(
				new ConfAStarTree.Builder(emat, confSpace).build(),
				confEcalc
			);
		}
	}
	
	private static Problem problemDiscrete;
	private static Problem problemContinuous;
	private static Problem problemMultipleStrands;
	
	@BeforeClass
	public static void beforeClass() {
		
		Molecule mol = PDBIO.readFile("examples/python.GMEC/1CC8.ss.pdb");
		
		Strand strand1 = new Strand.Builder(mol).build();
		strand1.flexibility.get(2).setLibraryRotamers("ALA", "GLY");
		strand1.flexibility.get(3).setLibraryRotamers(Strand.WildType, "VAL");
		strand1.flexibility.get(4).setLibraryRotamers();
		problemDiscrete = new Problem(strand1);
		
		Strand strand2 = new Strand.Builder(mol).setResidues(2, 30).build();
		strand2.flexibility.get(2).setLibraryRotamers("ALA", "GLY");
		strand2.flexibility.get(3).setLibraryRotamers(Strand.WildType, "VAL", "ARG").setContinuous(10);
		strand2.flexibility.get(4).addWildTypeRotamers();
		problemContinuous = new Problem(strand2);
		
		Strand strand3 = new Strand.Builder(mol).setResidues(2, 30).build();
		strand3.flexibility.get(2).addWildTypeRotamers();
		strand3.flexibility.get(3).addWildTypeRotamers();
		strand3.flexibility.get(4).addWildTypeRotamers();
		Strand strand4 = new Strand.Builder(mol).setResidues(31, 60).build();
		strand4.flexibility.get(31).addWildTypeRotamers();
		strand4.flexibility.get(32).addWildTypeRotamers();
		strand4.flexibility.get(33).addWildTypeRotamers();
		problemMultipleStrands = new Problem(strand3, strand4);
	}

	@Test
	public void findDiscrete() {
		EnergiedConf conf = problemDiscrete.makeBuilder().build().find();
		assertThat(conf.getAssignments(), is(new int[] { 1, 3, 4 }));
		assertThat(conf.getEnergy(), isAbsolutely(-30.705504, EnergyEpsilon));
		assertThat(conf.getScore(), isAbsolutely(-30.705504, EnergyEpsilon));
	}
	
	@Test
	public void findDiscreteWindowZero() {
		Queue<EnergiedConf> confs = problemDiscrete.makeBuilder().build().find(0);
		assertThat(confs.size(), is(1L));
		
		EnergiedConf conf = confs.poll();
		assertThat(conf.getAssignments(), is(new int[] { 1, 3, 4 }));
		assertThat(conf.getEnergy(), isAbsolutely(-30.705504, EnergyEpsilon));
		assertThat(conf.getScore(), isAbsolutely(-30.705504, EnergyEpsilon));
	}
	
	@Test
	public void findDiscreteWindowOne() {
		Queue<EnergiedConf> confs = problemDiscrete.makeBuilder().build().find(1);
		assertThat(confs.size(), is(4L));
		
		EnergiedConf conf = confs.poll();
		assertThat(conf.getAssignments(), is(new int[] { 1, 3, 4 }));
		assertThat(conf.getEnergy(), isAbsolutely(-30.705504, EnergyEpsilon));
		assertThat(conf.getScore(), isAbsolutely(-30.705504, EnergyEpsilon));
		
		conf = confs.poll();
		assertThat(conf.getAssignments(), is(new int[] { 1, 3, 5 }));
		assertThat(conf.getEnergy(), isAbsolutely(-30.241032, EnergyEpsilon));
		assertThat(conf.getScore(), isAbsolutely(-30.241032, EnergyEpsilon));
		
		conf = confs.poll();
		assertThat(conf.getAssignments(), is(new int[] { 1, 6, 4 }));
		assertThat(conf.getEnergy(), isAbsolutely(-29.981955, EnergyEpsilon));
		assertThat(conf.getScore(), isAbsolutely(-29.981955, EnergyEpsilon));
		
		conf = confs.poll();
		assertThat(conf.getAssignments(), is(new int[] { 1, 7, 4 }));
		assertThat(conf.getEnergy(), isAbsolutely(-29.748971, EnergyEpsilon));
		assertThat(conf.getScore(), isAbsolutely(-29.748971, EnergyEpsilon));
	}
	
	@Test
	public void findContinuous() {
		EnergiedConf conf = problemContinuous.makeBuilder().build().find();
		assertThat(conf.getAssignments(), is(new int[] { 1, 26, 0 }));
		assertThat(conf.getEnergy(), isAbsolutely(-38.465807, EnergyEpsilon));
		assertThat(conf.getScore(), isAbsolutely(-38.566297, EnergyEpsilon));
	}
	
	@Test
	public void findContinuousWindow() {
		Queue<EnergiedConf> confs = problemContinuous.makeBuilder().build().find(0.3);
		assertThat(confs.size(), is(3L));
		
		EnergiedConf conf = confs.poll();
		assertThat(conf.getAssignments(), is(new int[] { 1, 26, 0 }));
		assertThat(conf.getEnergy(), isAbsolutely(-38.465807, EnergyEpsilon));
		assertThat(conf.getScore(), isAbsolutely(-38.566297, EnergyEpsilon));
		
		conf = confs.poll();
		assertThat(conf.getAssignments(), is(new int[] { 1, 25, 0 }));
		assertThat(conf.getEnergy(), isAbsolutely(-38.243730, EnergyEpsilon));
		assertThat(conf.getScore(), isAbsolutely(-38.391590, EnergyEpsilon));
		
		conf = confs.poll();
		assertThat(conf.getAssignments(), is(new int[] { 1, 29, 0 }));
		assertThat(conf.getEnergy(), isAbsolutely(-38.166219, EnergyEpsilon));
		assertThat(conf.getScore(), isAbsolutely(-38.254643, EnergyEpsilon));
	}
	
	@Test
	public void findContinuousWindowExternal() {
		ExternalMemory.use(64, () -> {
			Queue<EnergiedConf> confs = problemContinuous.makeBuilder()
				.useExternalMemory()
				.build().find(0.3);
			assertThat(confs.size(), is(3L));
			
			EnergiedConf conf = confs.poll();
			assertThat(conf.getAssignments(), is(new int[] { 1, 26, 0 }));
			assertThat(conf.getEnergy(), isAbsolutely(-38.465807, EnergyEpsilon));
			assertThat(conf.getScore(), isAbsolutely(-38.566297, EnergyEpsilon));
			
			conf = confs.poll();
			assertThat(conf.getAssignments(), is(new int[] { 1, 25, 0 }));
			assertThat(conf.getEnergy(), isAbsolutely(-38.243730, EnergyEpsilon));
			assertThat(conf.getScore(), isAbsolutely(-38.391590, EnergyEpsilon));
			
			conf = confs.poll();
			assertThat(conf.getAssignments(), is(new int[] { 1, 29, 0 }));
			assertThat(conf.getEnergy(), isAbsolutely(-38.166219, EnergyEpsilon));
			assertThat(conf.getScore(), isAbsolutely(-38.254643, EnergyEpsilon));
		});
	}
	
	@Test
	public void findMultipleStrands() {
		EnergiedConf conf = problemMultipleStrands.makeBuilder().build().find();
		assertThat(conf.getAssignments(), is(new int[] { 0, 0, 0, 0, 0, 0 }));
		assertThat(conf.getEnergy(), isAbsolutely(-84.428153, EnergyEpsilon));
		assertThat(conf.getScore(), isAbsolutely(-84.428153, EnergyEpsilon));
	}
}
