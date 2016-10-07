# KC notes

Random notes as I peruse the reference-taxonomy codebase to try and understand how this works.

# Directory structure

* `bin`: scripts to build jython and to publish a taxonomy
* `doc`: release notes for versions of OTT; documentation on scripting with smasher; manuscript
* `feed`: python scripts for processing input taxonomies, plus patches in csv / tsv format
* `log`: log files from OTT versions
* `org`: java code for building taxonomies
* `resources`: json files describing each input to OTT and each download of each input
* `service`: HTTP server and wrapper for conflict service
* `t`: scripts for building the Asterales taxonomy; taxonomy amendments (is this a git submodule?)
* `tax`: taxonomy and synonym files for some input taxonomies
* `util` : jython scripts for various utility functions
* `ws-tests` : tests for the conflict web service

# Building OTT

## Makefile

The section of interest in the Makefile:

```
tax/ott/log.tsv: $(CLASS) make-ott.py assemble_ott.py taxonomies.py \
                    tax/silva/taxonomy.tsv \
		    tax/fung/taxonomy.tsv tax/713/taxonomy.tsv \
		    tax/ncbi/taxonomy.tsv tax/gbif/taxonomy.tsv \
		    tax/irmng/taxonomy.tsv \
		    tax/worms/taxonomy.tsv \
		    feed/ott/edits/ott_edits.tsv \
		    tax/prev_ott/taxonomy.tsv \
		    feed/misc/chromista_spreadsheet.py \
		    ids_that_are_otus.tsv \
		    bin/jython \
		    inclusions.csv
@date
@rm -f *py.class
@mkdir -p tax/ott
time bin/jython make-ott.py
echo $(WHICH) >tax/ott/version.txt
```

## What does make-ott.py do?

* calls three functions in `assemble_ott.py`: `create_ott()` + `dump() ` + `report()`
* `assemble_ott.py` has functions like `align_ncbi`, `align_irmng` in addition to `create_ott` and `report`
* `create_ott` has two major parts: (loading, aligning, absorbing taxonomies) + (assigning ids)
* confused about use of `if True` and `if False` without explicit test throughout code

**Summary of steps in create_ott**

* ott = newTaxonomy()
* set logging for names in manually curated list names_of_interest
* load preferred ids: false if ids are otus; true if ids in synthesis
* create skeleton taxonomy
* deal_with_ctenophora
* load taxonomies:
  * silva: load, align, absorb, check_invariants
  * hibbett: load, absorb
  * index fungorum: split into fungi and non-fungi, align, absorb, check_invariants
  * lamiales: load, align, absorb
  * worms: split into malacostraca and non-malacostraca, absorb
  * ncbi: load, map to silva, align, absorb, compare to silva, check_invariants, maybeTaxon
  * worms-not-malacostraca: special treatment of Glaucophyta, align, absorb
  * index fungorum: absorb
  * gbif: load, align, absorb, special treatment of Cylindrocarpon
  * irmng: load, align, absorb
  * link_to_h2007
* get extinct info from gbif
* deforestate
* id assignment:
  * force ids for manually curated list
  * special treatment of Rhynchonelloidea
  * special treatment of Trichosporon
  * get ids from getTaxonomy
  * ‘undo lossage from 2.9’ ??
  * special treatment of Glaucophyta from 2.9
  * assign old ids to new taxonomy with carryOverIds
  * apply additions with processAdditions
  * mint new ids: assignNewIds
* check
* report on h2007


### Loading, aligning, absorbing

**Loading**

* specific function for each input taxonomy
* functions located in `taxonomies.py`
* use `Taxonomy.getTaxonomy(path,label)` for each input
* some `load_*` functions call helper functions `patch_*`, `analyzeMajorRankConflicts`, `smush`, `fix_basal`, `analyzeOTUs`, `fix_SAR`
* use `maybeTaxon`, `synonym`, `extant`, `rename`, `notCalled`, `trim`, `take`,`prune`, `absorb`, `extinct` for handling special cases

**Alignment**

* for each input taxonomy, call `ott.alignment(taxonomy,ott)`
* then, series of `establish`, `same`, `maybeTaxon`, `prune`, `trouble` (custom function for align_irmng), `hideDescendantsToRank`, `absorb`, `extant` for special cases

**Absorb**

### Assigning ids
