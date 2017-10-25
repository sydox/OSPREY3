
import osprey
osprey.start()

# choose a forcefield
ffparams = osprey.ForcefieldParams()

# read a PDB file for molecular info
mol = osprey.readPdb("2RL0.min.reduce.pdb")

# make sure all strands share the same template library (including wild-type rotamers)
templateLib = osprey.TemplateLibrary(ffparams.forcefld, moleculesForWildTypeRotamers=[mol])

# define the protein strand
protein = osprey.Strand(mol, templateLib=templateLib, residues=[648, 654])
protein.flexibility[649].setLibraryRotamers(osprey.WILD_TYPE, 'TYR', 'ALA', 'VAL', 'ILE', 'LEU').addWildTypeRotamers().setContinuous()
protein.flexibility[650].setLibraryRotamers(osprey.WILD_TYPE).addWildTypeRotamers().setContinuous()
protein.flexibility[651].setLibraryRotamers(osprey.WILD_TYPE).addWildTypeRotamers().setContinuous()
protein.flexibility[654].setLibraryRotamers(osprey.WILD_TYPE).addWildTypeRotamers().setContinuous()

# define the ligand strand
ligand = osprey.Strand(mol, templateLib=templateLib, residues=[155, 194])
ligand.flexibility[156].setLibraryRotamers(osprey.WILD_TYPE).addWildTypeRotamers().setContinuous()
ligand.flexibility[172].setLibraryRotamers(osprey.WILD_TYPE).addWildTypeRotamers().setContinuous()
ligand.flexibility[192].setLibraryRotamers(osprey.WILD_TYPE).addWildTypeRotamers().setContinuous()
ligand.flexibility[193].setLibraryRotamers(osprey.WILD_TYPE).addWildTypeRotamers().setContinuous()

# make the conf space for the protein
proteinConfSpace = osprey.ConfSpace(protein)

# make the conf space for the ligand
ligandConfSpace = osprey.ConfSpace(ligand)

# make the conf space for the protein+ligand complex
complexConfSpace = osprey.ConfSpace([protein, ligand])

# how should we compute energies of molecules?
# (give the complex conf space to the ecalc since it knows about all the templates and degrees of freedom)
parallelism = osprey.Parallelism(cpuCores=4)
minimizingEcalc = osprey.EnergyCalculator(complexConfSpace, ffparams, parallelism=parallelism, isMinimizing=True)

# BBK* needs a rigid energy calculator too, for multi-sequence bounds on K*
rigidEcalc = osprey.SharedEnergyCalculator(minimizingEcalc, isMinimizing=False)

# how should we define energies of conformations?
def confEcalcFactory(confSpace, ecalc):
	eref = osprey.ReferenceEnergies(confSpace, ecalc)
	return osprey.ConfEnergyCalculator(confSpace, ecalc, referenceEnergies=eref)

# how should confs be ordered and searched?
def astarFactory(emat, rcs):
	return osprey.AStarTraditional(emat, rcs, showProgress=False)
	# or
	# return osprey.AStarMPLP(emat, rcs, numIterations=5)

# run K*
bbkstar = osprey.BBKStar(
	proteinConfSpace,
	ligandConfSpace,
	complexConfSpace,
	rigidEcalc,
	minimizingEcalc,
	confEcalcFactory,
	astarFactory,
	numBestSequences=2,
	epsilon=0.99, # you proabably want something more precise in your real designs
	energyMatrixCachePattern='emat.*.dat',
	writeSequencesToConsole=True,
	writeSequencesToFile='bbkstar.results.tsv'
)
scoredSequences = bbkstar.run()

# use results
wildtype = osprey.Sequence.makeWildType(complexConfSpace)
for sequence in scoredSequences:
	print(sequence.toString(wildtype))
