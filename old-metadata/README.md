
# Resources - inputs and outputs of taxonomy synthesis

A 'resource' as used here is something that changes over time, but
whose state can be 'captured' at a point in time.  An example is NCBI
Taxonomy, which changes all the time, but a snapshot can be downloaded
at any point.

File `resources.json` lists resources that are inputs to OTT builds.
The fields for each entry are:

* `name`
* `retrieved_from` - download URL
* `legal` - 'pd' = public domain, 'cc0' = CC0 waiver, 'public' = on the web without license, 
  'handoff' = we got it from someone, etc.
* `filename_template` - for creating filenames for new captures of this resource
* `name_template` - for creating names for new captures of this resource
* `ott_idspace` - prefix to be used in the taxonomy 'sources' field (a.k.a. `tax_sources`)
* `date` - date from which we first started capturing this resource
* `capture_description` - generic description that can be applied to any capture
* `description` - description of this resource

File `captures.json` lists all captures of all resources.

* `name`
* `capture_of` - name of resource of which this is a capture
* `label` - indicator specific to this capture (date or version number)
* `bytes` - number of octets in downloaded file
* `legal`
* `filename`
* `retrieved_from` - download URL
* `date` - date of creation, publication, or download
* `last_modified` - date from Last-modified: HTTP response header
* `generated_on` - date on which this capture was generated
* `sources` (for OTT versions) - dict of idspace: capturename for inputs to that version
* `commit` (for OTT versions) - git commit for code that generated that version

All captures are archived on http://files.opentreeoflife.org/{name}
where {name} is a resource name,
e.g. http://files.opentreeoflife.org/ncbi/
