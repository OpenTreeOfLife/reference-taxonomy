# coding=utf-8

def fixonetaxon(tax, taxon, proposed):
    prop = tax.taxon(proposed)
    if prop != None:
        prop.take(tax.taxon(taxon))

def fixChromista(tax):
    # See WORMS
    fixonetaxon(tax, 'Nodulinella', 'Foraminifera')

    # See http://www.mycobank.org/
    # genus may not be valid
    fixonetaxon(tax, 'Adlerocystis', 'Fungi')

    # See WORMS
    # Proposed parent is not in OTT
    fixonetaxon(tax, 'Alfredinidae', 'Asterigerinidae')

    # See WORMS
    fixonetaxon(tax, 'Almaenidae', 'Nonionoidea')

    # See WORMS
    fixonetaxon(tax, 'Radiozoa', 'Rhizaria')

    # See WORMS
    # Proposed parent is not in OTT
    fixonetaxon(tax, 'Ammolagenidae', 'Hormosinellidae')

    # See WORMS
    # Foraminifera incertae sedis
    fixonetaxon(tax, 'Towella', 'Foraminifera')
    tax.taxon('Towella').incertaeSedis()

    # See WORMS
    # Foraminifera incertae sedis
    fixonetaxon(tax, 'Lekithiammina', 'Foraminifera')
    tax.taxon('Lekithiammina').incertaeSedis()

    # See WORMS
    # Proposed parent is not in OTT
    fixonetaxon(tax, 'Aschemocellidae', 'Hormosinidae')

    # See WORMS
    # Foraminifera incertae sedis
    fixonetaxon(tax, 'Rhaphidoscene', 'Foraminifera')
    tax.taxon('Rhaphidoscene').incertaeSedis()

    # See WORMS
    fixonetaxon(tax, 'Tosaiidae', 'Turrilinacea')

    # See WORMS
    # Ammoniinae
    fixonetaxon(tax, 'Asiarotalia', 'Rotaliidae')

    # See WORMS
    # Foraminifera incertae sedis
    fixonetaxon(tax, 'Xiphophaga', 'Foraminifera')
    tax.taxon('Xiphophaga').incertaeSedis()

    # See WORMS
    # taxon inquirendum ?
    fixonetaxon(tax, 'Mallopela', 'Foraminifera')

    # See WORMS
    fixonetaxon(tax, 'Heterolepidae', 'Chilostomellidae')

    # See WORMS
    # Proposed parent is not in OTT
    fixonetaxon(tax, 'Cystophrys', 'Lagynidae')

    # See WORMS
    # Proposed parent is not in OTT
    fixonetaxon(tax, 'Coscinophragmatidae', 'Coscinophragmatidae')

    # See WORMS
    # Foraminifera incertae sedis
    fixonetaxon(tax, 'Psammolagynis', 'Foraminifera')
    tax.taxon('Psammolagynis').incertaeSedis()

    # See WORMS
    # Proposed parent is not in OTT
    fixonetaxon(tax, 'Phthanotrochidae', 'Allogromida')

    # See WORMS
    # Heterokonta (Infrakingdom) > Ochrophyta (Phylum) > Phaeista (Subphylum) > Limnista (Infraphylum) > Fucistia (Superclass)
    fixonetaxon(tax, 'Schizocladiophyceae', 'Fucistia')

    # See WORMS
    # Foraminifera incertae sedis
    fixonetaxon(tax, 'Echinogromia', 'Foraminifera')
    tax.taxon('Echinogromia').incertaeSedis()

    # See WORMS
    fixonetaxon(tax, 'Bolivinellidae', 'Cassidulinidea')

    # See WORMS
    fixonetaxon(tax, 'Bagginidae', 'Discorbidea')

    # See WORMS
    fixonetaxon(tax, 'Bronnimannidae', 'Discorbidea')

    # See WORMS
    fixonetaxon(tax, 'Apogromia', 'Lagynidae')

    # See WORMS
    fixonetaxon(tax, 'Fabulariidae', 'Alveolinidea')

    # See WORMS
    fixonetaxon(tax, 'Verneuilinidae', 'Verneuilinidea')

    # See WORMS
    # Checked: verified by a taxonomic editorRhizaria (Infrakingdom) > Checked: verified by a taxonomic editorForaminifera (Phylum) > Checked: verified by a taxonomic editorTubothalamea (Class) > Checked: verified by a taxonomic editorMiliolida (Order) > Checked: verified by a taxonomic editorMiliolina (Suborder) > Checked: verified by a taxonomic editorMiliolacea (Superfamily)
    fixonetaxon(tax, 'Tubinellidae', 'Miliolacea')

    # See WORMS
    # Foraminifera incertae sedis
    fixonetaxon(tax, 'Lagunculina', 'Foraminifera')
    tax.taxon('Lagunculina').incertaeSedis()

    # See WORMS
    fixonetaxon(tax, 'Conotrochamminidae', 'Verneuilinidea')

    # See WORMS
    fixonetaxon(tax, 'Heterogromia', 'Lagynidae')

    # See WORMS
    fixonetaxon(tax, 'Cribrolinoididae', 'Miliolida')

    # See WORMS
    fixonetaxon(tax, 'Placentulinidae', 'Discorbidea')

    # See WORMS
    # Hacrobia (Subkingdom) > Checked: verified by a taxonomic editorHeliozoa (Phylum) > Checked: verified by a taxonomic editorHeliozoa incertae sedis (Class)
    fixonetaxon(tax, 'Ciliophrydae', 'Heliozoa')
    tax.taxon('Ciliophrydae').incertaeSedis()

    # See WORMS
    fixonetaxon(tax, 'Haplophragmoididae', 'Lituolida')

    # See WORMS
    fixonetaxon(tax, 'Notorotaliidae', 'Rotaliida')

    # See WORMS
    fixonetaxon(tax, 'Septammina', 'Foraminifera')

    # See WORMS
    fixonetaxon(tax, 'Reophaxopsis', 'Foraminifera')

    # See WORMS
    fixonetaxon(tax, 'Ammosigmoinella', 'Miliolina')

    # See WORMS
    fixonetaxon(tax, 'Notodendrodidae', 'Astrorhizidea')

    # See WORMS
    fixonetaxon(tax, 'Louisianinidae', 'Planorbulinacea')

    # See WORMS
    fixonetaxon(tax, 'Stictogongylus', 'Foraminifera')

    # See WORMS
    fixonetaxon(tax, 'Spiroloculinidae', 'Miliolida')

    # See WORMS
    fixonetaxon(tax, 'Chrysalidinidae', 'Chrysalidinidea')

    # See WORMS
    # Foraminifera incertae sedis
    fixonetaxon(tax, 'Dendropela', 'Foraminifera')
    tax.taxon('Dendropela').incertaeSedis()

    # See WORMS
    fixonetaxon(tax, 'Remaneicidae', 'Trochamminidae')

    # See WORMS
    fixonetaxon(tax, 'Ceratestina', 'Allogromiida')

    # See WORMS
    fixonetaxon(tax, 'Conicotheca', 'Allogromiida')

    # See WORMS
    fixonetaxon(tax, 'Planispirillinidae', 'Spirillinina')

    # See WORMS
    fixonetaxon(tax, 'Rotaliporidae', 'Rotaliporidea')

    # See WORMS
    # *
    fixonetaxon(tax, 'Turriclavula', 'Foraminifera')

    # See WORMS
    # *
    fixonetaxon(tax, 'Camurammina', 'Trochammininae')

    # See WORMS
    fixonetaxon(tax, 'Aperneroum', 'Foraminifera')

    # See WORMS
    fixonetaxon(tax, 'Plectofrondiculariidae', 'Nodosariidea')

    # See WORMS
    fixonetaxon(tax, 'Discospirinidae', 'Nubecularioidea')

    # See WORMS
    fixonetaxon(tax, 'Glandulonodosariidae', 'Nodosariidea')

    # See WORMS
    # Rhizaria (Infrakingdom) > Checked: verified by a taxonomic editorForaminifera (Phylum) > Checked: verified by a taxonomic editorGlobothalamea (Class) > Checked: verified by a taxonomic editorBuliminida (Order) > Checked: verified by a taxonomic editorBolivinoidea (Superfamily)
    fixonetaxon(tax, 'Cheilochanidae', 'Bolivinoidea')

    # See WORMS
    fixonetaxon(tax, 'Stacheia', 'Foraminifera')

    # See WORMS
    fixonetaxon(tax, 'Buccinina', 'Foraminifera')

    # See WORMS
    # *
    fixonetaxon(tax, 'Paratikhinellidae', 'Moravamminoidea')

    # See WORMS
    # Foraminifera incertae sedis
    fixonetaxon(tax, 'Valvorotalia', 'Foraminifera')
    tax.taxon('Valvorotalia').incertaeSedis()

    # See WORMS
    # Ammosphaeroidinidae
    fixonetaxon(tax, 'Ammochilostoma', 'Ammosphaeroidininae')

    # See WORMS
    fixonetaxon(tax, 'Haddoniidae', 'Coscinophragmatidea')

    # See WORMS
    fixonetaxon(tax, 'Komokiidae', 'Komokiidae')

    # See 
    fixonetaxon(tax, 'Bolivinoididae', 'Bolivinidea')

    # See 
    fixonetaxon(tax, 'Diaphoropodon', 'Foraminifera')

    # See 
    fixonetaxon(tax, 'Globanomalinidae', 'Hantkeninidea')

    # See 
    fixonetaxon(tax, 'Lituolina', 'Lituolida')

    # See WORMS
    # Allogromida incertae sedis
    fixonetaxon(tax, 'Bowseria', 'Allogromida')
    tax.taxon('Bowseria').incertaeSedis()

    # See 
    fixonetaxon(tax, 'Ellipsolagenidae', 'Polymorphinidea')

    # See 
    fixonetaxon(tax, 'Stilostomellidae', 'Stilostomellidea')

    # See 
    fixonetaxon(tax, 'Trichohyalidae', 'Chilostomellidea')

    # See 
    fixonetaxon(tax, 'Placidiaceae', 'Placididea')

    # See 
    fixonetaxon(tax, 'Radiatobolivina', 'Foraminifera')

    # See 
    fixonetaxon(tax, 'Dusenburyinidae', 'Dusenburyina')

    # See 
    fixonetaxon(tax, 'Parrelloididae', 'Discorbinellidea')

    # See no records
    fixonetaxon(tax, 'Hyphochytriomycota', 'Fungi')

    # See 
    fixonetaxon(tax, 'Placopsilinidae', 'Lituolidea')

    # See 
    fixonetaxon(tax, 'Arboramminidae', 'Astrorhizidea')

    # See 
    fixonetaxon(tax, 'Rotalites', 'Foraminifera')

    # See 
    fixonetaxon(tax, 'Cuneolinidae', 'Ataxophragmiidea')

    # See 
    fixonetaxon(tax, 'Octonoradiolus', 'Foraminifera')

    # See 
    fixonetaxon(tax, 'Rhapydioninidae', 'Rhapydioninidae')

    # See 
    fixonetaxon(tax, 'Olgiidae', 'Textulariidea')

    # See 
    fixonetaxon(tax, 'Bisacciidae', 'Planorbulinidea')

    # See 
    fixonetaxon(tax, 'Chrysalogoniidae', 'Nodosariidea')

    # See 
    fixonetaxon(tax, 'Trimosinidae', 'Buliminidea')

    # See Allogromida incertae sedis
    fixonetaxon(tax, 'Capsammina', 'Allogromida')

    # See 
    fixonetaxon(tax, 'Scurciatoforamen', 'Foraminifera')

    # See 
    fixonetaxon(tax, 'Heronalleniidae', 'Glabratellidea')

    # See ****
    fixonetaxon(tax, 'Virgulinellidae', 'Fursenkoinoidea')

    # See 
    fixonetaxon(tax, 'Bueningiidae', 'Discorbidae')

    # See 
    fixonetaxon(tax, 'Pninaella', 'Asterigerinidae')

    # See 
    fixonetaxon(tax, 'Umbellina', 'Foraminifera')

    # See 
    fixonetaxon(tax, 'Tortoplectellidae', 'Bolivinitidae')

    # See 
    fixonetaxon(tax, 'Crambis', 'Foraminifera')

    # See WORMS
    # Checked: verified by a taxonomic editorHeterokonta (Infrakingdom) > Checked: verified by a taxonomic editorBigyra (Phylum) > Checked: verified by a taxonomic editorBicosoecia (Subphylum) > Checked: verified by a taxonomic editorBicoecea (Class) > Checked: verified by a taxonomic editorBicosidia (Subclass) > Checked: verified by a taxonomic editorCyathobodoniae (Superorder) > Checked: verified by a taxonomic editorAnoecida (Order)
    fixonetaxon(tax, 'Caecitellidae', 'Anoecida')

    # See 
    fixonetaxon(tax, 'Cornuspiridae', 'Cornuspiridae')

    # See 
    fixonetaxon(tax, 'Halyphysemidae', 'Astrorhizidae')

    # See 
    fixonetaxon(tax, 'Trochamminula', 'Trochamminidae')

    # See 
    fixonetaxon(tax, 'Trochamminoidae', 'Ammodiscidae')

    # See 
    fixonetaxon(tax, 'Spirotectinidae', 'Nonionidea')

    # See 
    # Foraminifera incertae sedis
    fixonetaxon(tax, 'Cactos', 'Foraminifera')
    tax.taxon('Cactos').incertaeSedis()

    # See 
    fixonetaxon(tax, 'Lagynis', 'Lagynidae')

    # See 
    fixonetaxon(tax, 'Pilalla', 'Lagynidae')

    # See 
    fixonetaxon(tax, 'Ophiotuba', 'Lagynidae')

    # See 
    fixonetaxon(tax, 'Rhumblerinella', 'Lagynidae')

    # See 
    fixonetaxon(tax, 'Earlmyersia', 'Discorbinellidae')

    # See 
    fixonetaxon(tax, 'Ungulatellidae', 'Discorbidea')

    # See 
    fixonetaxon(tax, 'Textulariopsidae', 'Spiroplectamminidae')

    # See 
    fixonetaxon(tax, 'Sphaeramminidae', 'Lituolidae')

    # See 
    fixonetaxon(tax, 'Telamminidae', 'Psammosphaeridae')

    # See 
    fixonetaxon(tax, 'Rhaphidohelix', 'Foraminifera')

    # See 
    fixonetaxon(tax, 'Quadrimorphinidae', 'Chilostomellidae')

    # See Caucasinidae
    fixonetaxon(tax, 'Aeolomorphelloides', 'Caucasininae')

    # See Discorbidae
    fixonetaxon(tax, 'Mississippinidae', 'Discorbidea')

    # See 
    fixonetaxon(tax, 'Tritaxiidae', 'Verneuilinidae')

    # See 
    fixonetaxon(tax, 'Seiglieina', 'Foraminifera')

    # See 
    fixonetaxon(tax, 'Jugimuramminidae', 'Hippocrepinidae')

    # See 
    fixonetaxon(tax, 'Cyclamminidae', 'Loftusiidae')

    # See 
    fixonetaxon(tax, 'Pulvinulina', 'Foraminifera')

    # See Bagginidae
    # status--unaccepted??
    fixonetaxon(tax, 'Rotalina', 'Baggininae')

    # See 
    fixonetaxon(tax, 'Ovulida', 'Foraminifera')

    # See 
    fixonetaxon(tax, 'Staphylion', 'Foraminifera')

    # See 
    fixonetaxon(tax, 'Trioxeia', 'Foraminifera')

    # See no records
    fixonetaxon(tax, 'Lituotubidae', 'Lituotuboidea')

    # See 
    fixonetaxon(tax, 'Discamminidae', 'Lituolida')

    # See 
    fixonetaxon(tax, 'Hippocrepinidae', 'Hippocrepinidea')

    # See 
    fixonetaxon(tax, 'Schultzella', 'Lagynidae')

    # See 
    fixonetaxon(tax, 'Hooperellidae', 'Cassidulinidae')

    # See 
    fixonetaxon(tax, 'Polysaccamminidae', 'Psammosphaeridae')

    # See 
    fixonetaxon(tax, 'Valvulamminidae', 'Eggerellidae')

    # See Spiroplectamminidae
    # *
    fixonetaxon(tax, 'Pseudobolivinidae', 'Spiroplectamminidea')

    # See 
    fixonetaxon(tax, 'Neusina', 'Foraminifera')

    # See no results
    fixonetaxon(tax, 'Ammobaculinidae', 'Recurvoidoidea')

    # See 
    fixonetaxon(tax, 'Marenda', 'Foraminifera')

    # See 
    fixonetaxon(tax, 'Urnulina', 'Foraminifera')

    # See Nubecularioidae
    fixonetaxon(tax, 'Zoyaellidae', 'Nubeculariidea')

    # See no results
    fixonetaxon(tax, 'Ammosphaeroidinidae', 'Recurvoidoidea')

    # See 
    fixonetaxon(tax, 'Trilocularenidae', 'Rzehakinidae')

    # See 
    fixonetaxon(tax, 'Skeletonia', 'Komokiidae')

    # See 
    fixonetaxon(tax, 'Reophacellidae', 'Verneuilinidae')

    # See 
    fixonetaxon(tax, 'Prolixoplectidae', 'Verneuilinidae')

    # See 
    fixonetaxon(tax, 'Sardammina', 'Foraminifera')

    # See 
    fixonetaxon(tax, 'Rhaphidodendron', 'Foraminifera')

    # See 
    fixonetaxon(tax, 'Hippocrepinellidae', 'Astrorhizidae')

    # See 
    fixonetaxon(tax, 'Planulinoididae', 'Discorbinellidae')

    # See 
    fixonetaxon(tax, 'Millettella', 'Foraminifera')

    # See 
    fixonetaxon(tax, 'Pseudarcella', 'Foraminifera')

    # See 
    fixonetaxon(tax, 'Sporozoa', 'Apicomplexa')

    # See 
    fixonetaxon(tax, 'Granuloreticulosea', 'Foraminifera')

