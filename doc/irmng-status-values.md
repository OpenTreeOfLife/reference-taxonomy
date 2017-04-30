## Status for non-synonyms

Taxonomic status values:

* 'accepted' seems good.
* 'valid'  examples: Rana pipiens, Homo sapiens
* 'synonym' is obviously wrong if it's not a synonym.
* '' - who knows.  Research this.

Nomenclatural status values:

* 'conservandum' - keep
* 'nom. cons.' - keep
* 'Nom. cons.'
* 'nom. cons. des.'
* 'nom. cons. prop.'
* 'protectum'
* 'Nomen novum'
* 'correctum'
* 'orth. cons.'
* ''  e.g. Canidae
* 'legitimate' ??

* 'later usage'
* 'orthographia' - ?? how can this be a non-synonym ?

* 'www.nearctica.com/nomina/beetle/colteneb.htm'
* 'Ruhberg et al., 1988'

* 'novum'
* 'available'
* 'unavailable'
* 'nudum'
* 'invalid'
* 'dubium'
* 'rejiciendum'
* 'unavailable (except homonymy)'
* 'rejiciandum'
* 'nom. rev.'
* 'nom. conf.'
* 'dubimum'
* 'invalidum'
* 'superfluum'
* 'unjustified emendation'
* 'nom. rej.'
* 'illegitimate'
* 'nom. illeg.'
* 'anticipatione'
* 'misspelling / subsequent emendation'
* 'Available'
* 'Nom. rej.'
* 'ICZN'
* 'oblitum'
* 'illegitimum'
* 'nom. rej. prop.'
* 'rejected name (non binomial work)'
* 'junior homonym'


## Status for synonyms

Synonym taxonomic status values: 
* ''
* 'synonym'
* 'valid'
* 'accepted'

what does it mean for a synonym to be 'valid' or 'accepted'?

Synonym nomenclatural status values: (keep all synonyms, no need to
distinguish among these) (these could become the 'type' of the synonym)
* ''
* 'orthographia'
* 'nom. rej.'
* 'invalid'
* 'novum'
* 'available'
* 'unavailable'
* 'conservandum'
* 'rejiciendum'
* 'nudum'
* 'invalidum'
* 'unavailable (except homonymy)'
* 'nom. cons.'
* 'nom. illeg.'
* 'later usage'
* 'synonym'
* 'superfluum'
* 'illegitimum'
* 'oblitum'
* 'nom. rev.'
* 'misspelling / subsequent emendation'
* 'illegitimate'
* 'dubium'
* 'unjustified emendation'
* 'Nom. rej.'
* 'Nomen illegitimum'
* 'correctum'
* 'unavailable (except for homonymy)'
* 'dubimum'
* 'Later usage'
* 'Subsequent use of valid name'
* 'junior homonym'
* 'Nomen nudum'
* 'Unavailable homonym'
* 'Available'



    # valid ~= correctly published
    # available
    # accepted

    tstatus_values = []
    nstatus_values = []
    syn_tstatus_values = []
    syn_nstatus_values = []


            if not tstatus in syn_tstatus_values:
                syn_tstatus_values.append(tstatus)
            if not nstatus in syn_nstatus_values:
                syn_nstatus_values.append(nstatus)

            if not tstatus in tstatus_values:
                tstatus_values.append(tstatus)
            if not nstatus in nstatus_values:
                nstatus_values.append(nstatus)

    print >>sys.stderr, "Taxonomic status values:", tstatus_values
    print >>sys.stderr, "Nomenclatural status values:", nstatus_values
    print >>sys.stderr, "Synonym taxonomic status values:", syn_tstatus_values
    print >>sys.stderr, "Synonym nomenclatural status values:", syn_nstatus_values

