Metadata file format (about.json in each taxonomy directory)

Requirements:

  - Ideal: Explicate the precise meaning of any name or identifier
    occurring in the taxonomy files.
       . Most important: 
       	   . the prefixes occurring in union taxonomy

  - Ideal: Give provenance for any claims made by the files.
       . Most important:
       	   . URLs and dates of any web resources consulted

  - Ideal: enable reproduction (and therefore reproduction with
    variation).

  - For source and derived taxonomies:
    For any id prefix occurring in the sourceinfo field, we need a
    definition for that prefix, consisting of
       . the prefix
       . the URL prefix for single id lookups
       . source and version information, if available
           . URL from which taxonomy dump was retrieved
	   . date+time when retrieved
	   . prose description
    If there is contention - well, who knows what to do.  Ought to
    force collision avoidance by renaming.  Later.

  - For source taxonomies:
       . source and version information, if available
           . URL from which taxonomy dump was retrieved
	   . date+time when retrieved
	   . prose description

  - For source taxonomies:
    We need to be able to suggest a prefix that can be prepended to
    ids defined in this taxonomy when creating qualified ids.  Similar
    to (same as?) above.

  - For source taxonomies:
    A suggestion as to whether sibling homonyms should be smushed,
    when this taxonomy is loaded.

  - For derived taxonomies:
      . The smasher command line (or script) that was used to create this
      	taxonomy.
      . Details about source taxonomies that were input to synthesis,
        e.g. copies of their metadata files.  These sources are all
        mentioned on the command line.

  - For derived taxonomies:
    The version number attached to this version by the command line.



    about-file =
        {"sources": [source-descr, ...],
	     The original sources (premises) from which this taxonomy was derived.
         "formula": formula,
	     The process that was invoked to compute this taxonomy.
	 }

    source-descr =
        {"prefix": source-prefix,
  	      This prefix is implicit for any unprefixed ids in this taxonomy.
         "prefixDefinition": url-prefix,
 	      Replace prefix with this to get a URL.
         "url": url,
              The URL from which the original taxonomy dump was fetched.
         "lastModified": datetime,
	      ISO 8601 date with optional 'T' + time, from
	      Last-modified: header in GET request for url.
         "smushp": boolean,
 	      Controls sibling homonym folding.  Default false.
         "description": string}

    formula =
        source-prefix
	     Cf. field in source-descr, above
      | union-formula
      | selection-formula

    union-formula =
        {"type": "union",
         "name": name_with_version,
	     E.g. "ott2.7" or "ott2.8.draft5"
         "inputs": [formula, ...]}

    selection-formula
        {"type": "selection",
         "taxon": taxon-name,
         "inputs": [formula]}

