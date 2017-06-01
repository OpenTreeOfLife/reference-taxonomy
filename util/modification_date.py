# Print modification date in ISO format

import sys, os, time

ncbitime = os.path.getmtime(sys.argv[1])
tuple_time = time.gmtime(ncbitime)
iso_time = time.strftime("%Y%m%d", tuple_time)

print iso_time
