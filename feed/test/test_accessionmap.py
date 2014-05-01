import unittest
import os
from feed.ncbi import map_ncbi_accessions

class testAccessionMapping(unittest.TestCase):
    def setUp(self):    
        self.accessionNumber = "AF025822"
        pass

    def tearDown(self):
        pass

    def testDoesEutilCreatesOutputFile(self):
        tempfilename = "efetchtest.tmp"
        batch = [self.accessionNumber]
        mna.callEutils(tempfilename,batch)
        self.assertTrue(os.path.isfile(tempfilename))
        if os.path.isfile(tempfilename):
            os.unlink(tempfilename)
    
    def testCorrectlyMapSingleAccession(self):
        taxonID = '67760'
        strain='ME'
        map={self.accessionNumber:'2356'}
        batch = [self.accessionNumber]
        map = mna.do_one_batch(batch,map)
        self.assertTrue(map[self.accessionNumber]==taxonID)
            
if __name__ == "__main__":
    unittest.main()

