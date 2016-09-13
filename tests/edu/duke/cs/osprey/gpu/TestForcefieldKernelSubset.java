package edu.duke.cs.osprey.gpu;

import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.BeforeClass;
import org.junit.Test;

import edu.duke.cs.osprey.TestBase;
import edu.duke.cs.osprey.control.EnvironmentVars;
import edu.duke.cs.osprey.energy.MultiTermEnergyFunction;
import edu.duke.cs.osprey.energy.forcefield.BigForcefieldEnergy;
import edu.duke.cs.osprey.energy.forcefield.ForcefieldInteractions;
import edu.duke.cs.osprey.energy.forcefield.ForcefieldParams;
import edu.duke.cs.osprey.energy.forcefield.GpuForcefieldEnergy;
import edu.duke.cs.osprey.energy.forcefield.ResPairEnergy;
import edu.duke.cs.osprey.energy.forcefield.SingleResEnergy;
import edu.duke.cs.osprey.structure.Molecule;
import edu.duke.cs.osprey.structure.PDBFileReader;
import edu.duke.cs.osprey.structure.Residue;

public class TestForcefieldKernelSubset extends TestBase {
	
	private static class Forcefields {
		public MultiTermEnergyFunction efunc;
		public BigForcefieldEnergy bigff;
		public GpuQueuePool queuePool;
		public GpuForcefieldEnergy gpuff;
		public MultiTermEnergyFunction efuncSub;
		public BigForcefieldEnergy.Subset bigffSub;
		public GpuForcefieldEnergy gpuffSub;
	}
	
	private static Molecule mol;
	private static Residue gly06, gly15, ser17, trp18, trp25, arg22, ala24, ile26, phe31, arg32, glu34,
		val36, leu39, trp47, leu48, ile53, arg55, val56, leu57, ile59, val62, leu64, val65, met66;
	
	@BeforeClass
	public static void before() {
		initDefaultEnvironment();
		
		// read a protein and get some arbitrary residues
		mol = PDBFileReader.readPDBFile("test/DAGK/2KDC.P.forOsprey.pdb");
		gly06 = mol.getResByPDBResNumber("6");
		gly15 = mol.getResByPDBResNumber("15");
		ser17 = mol.getResByPDBResNumber("17");
		trp18 = mol.getResByPDBResNumber("18");
		trp25 = mol.getResByPDBResNumber("25");
		arg22 = mol.getResByPDBResNumber("22");
		ala24 = mol.getResByPDBResNumber("24");
		ile26 = mol.getResByPDBResNumber("26");
		phe31 = mol.getResByPDBResNumber("31");
		arg32 = mol.getResByPDBResNumber("32");
		glu34 = mol.getResByPDBResNumber("34");
		val36 = mol.getResByPDBResNumber("36");
		leu39 = mol.getResByPDBResNumber("39");
		trp47 = mol.getResByPDBResNumber("47");
		leu48 = mol.getResByPDBResNumber("48");
		ile53 = mol.getResByPDBResNumber("53");
		arg55 = mol.getResByPDBResNumber("55");
		val56 = mol.getResByPDBResNumber("56");
		leu57 = mol.getResByPDBResNumber("57");
		ile59 = mol.getResByPDBResNumber("59");
		val62 = mol.getResByPDBResNumber("62");
		leu64 = mol.getResByPDBResNumber("64");
		val65 = mol.getResByPDBResNumber("65");
		met66 = mol.getResByPDBResNumber("66");
	}
	
	private Forcefields makeForcefields(Residue[] residues)
	throws IOException {
		
		ForcefieldParams ffparams = EnvironmentVars.curEFcnGenerator.ffParams;
		
		Forcefields ff = new Forcefields();
		ff.queuePool = new GpuQueuePool(1, 2);
		
		// make the all pairs energy functions
		ff.efunc = new MultiTermEnergyFunction();
		ForcefieldInteractions interactions = new ForcefieldInteractions();
		for (int pos1=0; pos1<residues.length; pos1++) {
			
			ff.efunc.addTerm(new SingleResEnergy(residues[pos1], ffparams));
			interactions.addResidue(residues[pos1]);
			
			for (int pos2=0; pos2<pos1; pos2++) {
				
				ff.efunc.addTerm(new ResPairEnergy(residues[pos1], residues[pos2], ffparams));
				interactions.addResiduePair(residues[pos1], residues[pos2]);
			}
		}
		
		ff.bigff = new BigForcefieldEnergy(ffparams, interactions);
		ff.gpuff = new GpuForcefieldEnergy(ffparams, interactions, ff.queuePool);
		
		// make the subset energy functions (first residue against the rest)
		ff.efuncSub = new MultiTermEnergyFunction();
		ff.efuncSub.addTerm(new SingleResEnergy(residues[0], ffparams));
		for (int pos1=1; pos1<residues.length; pos1++) {
			ff.efuncSub.addTerm(new ResPairEnergy(residues[0], residues[pos1], ffparams));
		}
		
		ForcefieldInteractions subsetInteractions = interactions.makeSubsetByResidue(residues[0]);
		ff.bigffSub = ff.bigff.new Subset(subsetInteractions);
		ff.gpuffSub = new GpuForcefieldEnergy(ff.gpuff, subsetInteractions);
		
		return ff;
	}
	
	private void checkEnergies(Residue[] residues, double allPairsEnergy, double subsetEnergy)
	throws IOException {
		
		Forcefields ff = makeForcefields(residues);
		
		// check the all pairs energy functions
		assertThat(ff.efunc.getEnergy(), isRelatively(allPairsEnergy));
		assertThat(ff.bigff.getEnergy(), isRelatively(allPairsEnergy));
		assertThat(ff.gpuff.getEnergy(), isRelatively(allPairsEnergy));
		
		// check the subset energy functions
		assertThat(ff.efuncSub.getEnergy(), isRelatively(subsetEnergy));
		assertThat(ff.bigff.getEnergy(ff.bigffSub), isRelatively(subsetEnergy));
		assertThat(ff.gpuffSub.getEnergy(), isRelatively(subsetEnergy));
		
		ff.gpuff.cleanup();
		ff.queuePool.cleanup();
	}

	@Test
	public void testSingleGly()
	throws Exception {
		Residue[] residues = { gly15 };
		checkEnergies(residues, -4.572136255843063, -4.572136255843063);
	}
	
	@Test
	public void testGlyPair()
	throws Exception {
		Residue[] residues = { gly06, gly15 };
		checkEnergies(residues, -9.17380398335906, -4.601667727515996);
	}
	
	@Test
	public void testGlySerPair()
	throws Exception {
		Residue[] residues = { gly15, ser17 };
		checkEnergies(residues, -9.48559560659799, -2.6911081922156552);
	}
	
	@Test
	public void testTrpPair()
	throws Exception {
		Residue[] residues = { trp18, trp25 };
		checkEnergies(residues, -12.625574526252965, -6.218018599252964);
	}
	
	@Test
	public void test4Residues()
	throws Exception {
		Residue[] residues = { gly15, ser17, trp18, trp25 };
		checkEnergies(residues, -23.31199205572296, -2.756905624257449);
	}
	
	@Test
	public void test6Residues()
	throws Exception {
		Residue[] residues = { gly15, ser17, trp18, trp25, arg22, ala24 };
		checkEnergies(residues, -52.316176530733166, -2.7906943839799343);
	}
	
	@Test
	public void test10Residues()
	throws Exception {
		Residue[] residues = { gly15, ser17, trp18, trp25, arg22, ala24, ile26, phe31, arg32, glu34 };
		checkEnergies(residues, -93.33337795127768, -2.7991581273906516);
	}
	
	@Test
	public void test14Residues()
	throws Exception {
		Residue[] residues = { gly15, ser17, trp18, trp25, arg22, ala24, ile26, phe31, arg32, glu34, val36, leu39, trp47, leu48 };
		checkEnergies(residues, -112.44246575304817, -2.799964297346741);
	}
	
	@Test
	public void test24Residues()
	throws Exception {
		Residue[] residues = {
			gly06, gly15, ser17, trp18, trp25, arg22, ala24, ile26, phe31, arg32, glu34, val36,
			leu39, trp47, leu48, ile53, arg55, val56, leu57, ile59, val62, leu64, val65, met66
		};
		checkEnergies(residues, -163.74206898485193, -4.612537058185951);
	}
}