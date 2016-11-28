I did a 2.11 assembly and, as usually, eyeballed the deprecated.tsv
file afterward.

One thing that stood out was many lines like the following:

903304	1	Calathea variegata	ncbi:210049,gbif:2761854	newly-hidden[extinct_inherited]	Goeppertia	=	synthesis	
903308	1	Calathea bella	ncbi:210045,gbif:2761800	newly-hidden[extinct_inherited]	Goeppertia	=	synthesis	
903312	1	Calathea mirabilis	ncbi:210044,gbif:2761847	newly-hidden[extinct_inherited]	Goeppertia	=	synthesis	
903316	1	Calathea pallidicosta	ncbi:210046,gbif:2761615	newly-hidden[extinct_inherited]	Goeppertia	=	synthesis	

These are alarming because here are many taxa in this genus, all
contributed by a study in synthesis and available to the previous
synthesis, that will be filtered out in the next synthesis run,
because the taxa are seen to be extinct in 2.11 (they were extant in
2.10 - that's what "newly" means).  This looks a lot like a
mistake, because very few source trees contain extinct taxa.  So it
deserves investigation.

First, to find out what taxon is annotated extinct (not just inferred
to be, i.e. extinct_inherited).  I just picked one at random and
chased the ancestor chain; fortunately the problem is at the parent:

bash-3.2$ grep "^903320	" tax/ott/taxonomy.tsv 
903320	|	9059434	|	Goeppertia ecuadoriana	|	species	|	ncbi:210040,gbif:2761839	|		|	extinct_inherited	|	
bash-3.2$ grep "^9059434	" tax/ott/taxonomy.tsv 
9059434	|	627012	|	Goeppertia	|	genus	|	ncbi:1756151,gbif:7304686,gbif:7269824,irmng:1087538,irmng:1090327	|		|	extinct	|	

The annnotation must be coming from IRMNG, since neither NCBI nor GBIF
provides extinct annotations.  Check:

bash-3.2$ grep "Goeppertia	" tax/irmng/taxonomy.tsv 
1090327	|	113104	|	Goeppertia	|	genus	|		|	
1087538	|	227	|	Goeppertia	|	genus	|	extinct	|	

If these two are actually different taxa (as is suggested by their
having different parents), they need to be separated somehow, or else
one of them has to be suppressed from the taxonomy.  (Because they get
merged into a single NCBI taxon, the extinctness flag gets wrongly
transferred.)

They both show up as "accepted" in the IRMNG web interface
(authorities: Nees and Presl).  Looking at the IRMNG_DWC.csv file we
have

"1090327","Goeppertia C.G.D. Nees, 1831","C.G.D. Nees, 1831","Goeppertia",,"Marantaceae","genus",,,"Index Nominum Genericorum",,,,"Marantaceae","113104",,"01-01-2012","ICBN"
"1087538","Goeppertia K.B. Presl in Sternberg, 1838","K.B. Presl in Sternberg, 1838","Goeppertia",,"Pteridophyta (awaiting allocation)","genus",,,"Index Nominum Genericorum",,,,"Pteridophyta","227",,"17-02-2012","ICBN"

Since the extinct one is a pteridophyte, and the other is a
magnoliophyte, they are almost certainly different plants.

There's nothing in IRMNG to suggest either is less "accepted" than the
other.  (See feed/irmng/status_values.md for what I have figured out
about status values.)  Even if we obtained a recent IRMNG dump, we
would have no way to distinguish the two by their metadata, e.g. by
changing the IRMNG import logic.


Several possible solutions:

1. Add disoint taxa containing them to the skeleton taxonomy, forcing
them to be separated at alignment time.  This is not really possible
since there is no taxon containing the 'pteridophyte' that is in both
NCBI and IRMNG (they would have to align in order for the separation
to have any effect).  Anyhow, this is both risky (could have
unexpected fallout for other taxa) and unreliable (might not work in
other similar cases).

2. For OTT 2.10 I had suppressed records with empty taxonomic status
field.  (Implemented in feed/irmng/process_irmng.py.)  It turned out
this went too far; as I remember there were complaints about missing
IRMNG taxa in OTT 2.10.  But we could go back to this choice, causing
both records to go away.  However this may not be viable after an
IRMNG refresh, which might change the status fields.

3. Perhaps there is a way to distinguish 'good' records with empty
status (the ones for the genus in synthesis) from 'bad' records with
empty status (such as the extinct one that's causing trouble)?  I
can't think of one.  Perhaps the "awaiting allocation" in the IRMNG
record could be helpful, but, again, this won't help if we ever
refresh IRMNG.

4. Perhaps alignment could be improved so that it doesn't merge these
two genera.  For example, alignment could refuse to equate genera
solely on the basis of their name - additional proof, such as a
species in common, could be demanded.  This is the 'alignment by
membership' approach I've talked about.

5. There is currently no easy way to just force the two IRMNG genera
to align to different OTT taxa.  If two appropriate OTT taxa are
already present before IRMNG gets aligned, then we could use `.same()`
directives to align the homonyms manually to different targets.  But
only one exists at that point.  It is possible to create the second
target in `align_irmng` (using `establish`), but the procedure is
ugly.  There are examples of `establish` in function
`deal_with_polysemies()` in `assemble_ott.py`.

6. It might also work to return to the approach toward merges used
through about OTT 2.5, which is to not do them.  Then the two IRMNG
taxa would automatically become separate OTT taxa, and probably the
IRMNG mangoliophyte would be the taxon aligned with the existing OTT
mangoliophyte.  (But there might be an ambiguity.)  The problem is
that there are many cases where you _want_ to do a merge, such as
names declared synonymous in a high-priority source that are separate
in a lower-priority source.  So there would have to be some
complicated logic to decide whether the merge was OK.

7. The easiest fix is simply to prune the extinct genus before the
IRMNG merge.  This would be done in `align_irmng` in assemble_ott.py.

