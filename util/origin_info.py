# Get the last-modified date of a file as an ISO string

import sys, os, time, json

path_or_date = sys.argv[1]
url = sys.argv[2]

# Get file date from nodes.dmp in downloaddir
# os.path.getmtime(file)   => number of seconds since epoch

if path_or_date[0].isdigit():
    # DWIMming either a path or a date is a kludge.
    iso = path_or_date
else:
    filetime = os.path.getmtime(path)
    tuple_time = time.gmtime(filetime)
    iso = time.strftime("%Y-%m-%d", tuple_time)

blob = {"origin_url": url,
        "origin_date": iso}

json.dump(blob, sys.stdout, indent=1, sort_keys=True)
print
