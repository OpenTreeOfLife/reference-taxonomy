[JAR: Nico asked for description of the exchange format, but I think
it's completely uninteresting.  I think it should be flushed, but
maybe information about what's in a taxon record / node should be
given somewhere.]

A taxonomy in the exchange format has the following parts:

 * A taxonomy table, with one row (record) per putative taxon.  Important columns are:
     * An identifier that is unique with this taxonomy
     * The identifier of its parent taxon record
     * The taxon's primary name-string
     * The taxon's designated rank (optional)
     * Optional annotations i.e. 'flags'
 * An optional synonyms table.
     * The identifier of a taxon
     * A synonym name-string for that taxon
 * An optional set of identifier merges.  A merge gives the identifier for a 
   taxon from a previous version of this taxonomy 
   that has been merged with another taxon, usually to repair
   what was an undetected synonymy in the previous version.

[NMF: Would be helpful to have 2-5 rows deep example, for 3 tables.
JAR: it's pretty boring.  Here are a few rows from the NCBI import:

Taxonomy -

    uid     parent  name            rank
    141976  8335    Plethodon cinereus      species 
    8335    269181  Plethodon       genus   
    269181  8332    Plethodontinae  subfamily       

Synonyms -

    uid     name                    type
    141976  Plethodon cinerea       synonym 
    73625   Lycopodium alpina       misspelling     
    73625   Lycopodium alpinum      synonym

Merges -

    uid     replacement
    12      74109
    30      29
    36      184914

end]

