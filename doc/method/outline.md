
### Proposed outline (somewhat different from current text)

* Abstract
* Introduction
    * How Open Tree works overall (new)
    * Open Tree's taxonomy needs
    * Requirements of method; what some of the difficulties are
* Materials and methods
    * Terminology
    * Simple intro, with examples, without details
    * Details of assembly not covered in simple intro
        * The source taxonomies
        * Source taxonomy ingest
        * Taxon alignment
            * Ad hoc alignment adjustments
            * Candidate identification
            * Heuristics
                * Separate taxa that are in disjoint separation taxa
                * Prefer taxon with shared lineage
                * Prefer taxon with overlapping membership
                * Prefer taxon in same separation taxon
                * Prefer non-synonym
        * Merge unaligned taxa
            * Copy grafts over; alignments tell you the attachment points
            * Insert some internal nodes for more resolution
            * Don't insert inconsistent taxa
        * Finishing up the assembly
* Results
    * Description of assembly process
        * Alignment stats
        * Merge stats
    * Description of assembly product
        * General metrics
        * Annotations
        * What comes from where
        * Topology
        * Homonym analysis
    * Evaluating the assembly product
        * Evaluation relative to Open Tree taxonomy needs (see intro)
        * (I don't think there's any other meaningful evaluation)
* Discussion
    * (some selection of the 18+ topics listed)
    * Annotations
    * File formats (put in footnote, not interesting)
    * Curator provided patches
    * Comparison with other projects
    * Future work
* Conclusions
