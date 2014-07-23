import unittest
import os
import map_ncbi_accessions as mna

class testAccessionMapping(unittest.TestCase):
	def setUp(self):	
		pass

	def tearDown(self):
		pass

	def testDoesEutilCreatesOutputFile(self):
		accessionNumber = "AF025822"
		tempfilename = "efetchtest.tmp"
		batch = [accessionNumber]
		mna.callEutils(tempfilename,batch)
		self.assertTrue(os.path.isfile(tempfilename))
		if os.path.isfile(tempfilename):
			os.unlink(tempfilename)
	
	# this probably shouldn't actually call NCBI, but it is only one
	# accession and I haven't looked into mock objects enough yet
	def testCorrectlyMapSingleAccession(self):
		accessionNumber = "AF025822"
		taxonID = '67760'
		strain='ME'
		map={'AF23456':'2356'}
		batch = [accessionNumber]
		map = mna.do_one_batch(batch,map)
		self.assertTrue(map[accessionNumber]==taxonID)
			

if __name__ == "__main__":
    unittest.main()

