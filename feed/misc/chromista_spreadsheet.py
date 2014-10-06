# coding=utf-8

count = [0]

def fixonetaxon(tax, taxon, proposed):
    euk = tax.taxon("Eukaryota")
    curr = tax.taxon(taxon)
    prop = tax.taxon(proposed)
    if (prop != None) and (curr != None) and (curr.getParent() != prop):
        if (curr.getParent() != euk):
            print "** Parent was probably altered by IRMNG:", curr, curr.getParent(), prop
        else:
            prop.take(curr)
            count[0] += 1

def fixChromista(tax):
    # See WORMS
    fixonetaxon(tax, 'Nodulinella', 'Foraminifera')

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
    fixonetaxon(tax, 'Keramosphaeridae', 'Soritidae')

    # See WORMS
    fixonetaxon(tax, 'Schizocladiophyceae', 'Ochrophyta')

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
    fixonetaxon(tax, 'Tubinellidae', 'Miliolina')

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
    # Asterigerinidea
    fixonetaxon(tax, 'Asterigerinatidae', 'Asterigerinidae')

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
    fixonetaxon(tax, 'Cheilochanidae', 'Buliminidae')

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

    # See WORMS
    fixonetaxon(tax, 'Bolivinoididae', 'Bolivinidea')

    # See WORMS
    fixonetaxon(tax, 'Diaphoropodon', 'Foraminifera')

    # See WORMS
    fixonetaxon(tax, 'Globanomalinidae', 'Hantkeninidea')

    # See WORMS
    fixonetaxon(tax, 'Lituolina', 'Lituolida')

    # See WORMS
    fixonetaxon(tax, 'Bowseria', 'Allogromida')

    # See WORMS
    fixonetaxon(tax, 'Ellipsolagenidae', 'Polymorphinidea')

    # See WORMS
    fixonetaxon(tax, 'Stilostomellidae', 'Stilostomellidea')

    # See WORMS
    fixonetaxon(tax, 'Trichohyalidae', 'Chilostomellidea')

    # See WORMS
    fixonetaxon(tax, 'Placidiaceae', 'Placididea')

    # See WORMS
    fixonetaxon(tax, 'Radiatobolivina', 'Foraminifera')

    # See WORMS
    fixonetaxon(tax, 'Dusenburyinidae', 'Dusenburyina')

    # See WORMS
    fixonetaxon(tax, 'Parrelloididae', 'Discorbinellidea')

    # See no records
    fixonetaxon(tax, 'Hyphochytriomycota', 'Fungi')

    # See WORMS
    fixonetaxon(tax, 'Placopsilinidae', 'Lituolidea')

    # See WORMS
    fixonetaxon(tax, 'Arboramminidae', 'Astrorhizidea')

    # See WORMS
    fixonetaxon(tax, 'Rotalites', 'Foraminifera')

    # See WORMS
    fixonetaxon(tax, 'Cuneolinidae', 'Ataxophragmiidea')

    # See WORMS
    fixonetaxon(tax, 'Octonoradiolus', 'Foraminifera')

    # See WORMS
    fixonetaxon(tax, 'Rhapydioninidae', 'Rhapydioninidae')

    # See WORMS
    fixonetaxon(tax, 'Olgiidae', 'Textulariidea')

    # See WORMS
    fixonetaxon(tax, 'Bisacciidae', 'Planorbulinidea')

    # See WORMS
    fixonetaxon(tax, 'Chrysalogoniidae', 'Nodosariidea')

    # See WORMS
    fixonetaxon(tax, 'Trimosinidae', 'Buliminidea')

    # See WORMS
    fixonetaxon(tax, 'Capsammina', 'Allogromida')

    # See WORMS
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
    fixonetaxon(tax, 'Caecitellidae', 'Bicosoecida')

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

    # See 
    fixonetaxon(tax, 'Aeolomorphelloides', 'Caucasinidae')

    # See 
    fixonetaxon(tax, 'Mississippinidae', 'Discorbidae')

    # See 
    fixonetaxon(tax, 'Tritaxiidae', 'Verneuilinidae')

    # See 
    fixonetaxon(tax, 'Seiglieina', 'Foraminifera')

    # See 
    fixonetaxon(tax, 'Jugimuramminidae', 'Hippocrepinidae')

    # See 
    fixonetaxon(tax, 'Ochrophyta', 'Stramenopile')

    # See 
    fixonetaxon(tax, 'Cyclamminidae', 'Loftusiidae')

    # See 
    fixonetaxon(tax, 'Pulvinulina', 'Foraminifera')

    # See 
    # status--unaccepted??
    fixonetaxon(tax, 'Rotalina', 'Bagginidae')

    # See 
    fixonetaxon(tax, 'Ovulida', 'Foraminifera')

    # See 
    fixonetaxon(tax, 'Staphylion', 'Foraminifera')

    # See 
    fixonetaxon(tax, 'Trioxeia', 'Foraminifera')

    # See 
    fixonetaxon(tax, 'Lituotubidae', 'Lituolida')

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

    # See 
    # *
    fixonetaxon(tax, 'Pseudobolivinidae', 'Spiroplectamminidae')

    # See 
    fixonetaxon(tax, 'Neusina', 'Foraminifera')

    # See 
    fixonetaxon(tax, 'Ammobaculinidae', 'Recurvididae')

    # See 
    fixonetaxon(tax, 'Marenda', 'Foraminifera')

    # See 
    fixonetaxon(tax, 'Urnulina', 'Foraminifera')

    # See 
    fixonetaxon(tax, 'Zoyaellidae', 'Nubecularioidae')

    # See 
    fixonetaxon(tax, 'Ammosphaeroidinidae', 'Recurvididae')

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
    fixonetaxon(tax, 'Pseudospora', 'Callithamniaceae')

    # See 
    # Proposed parent is not in OTT
    fixonetaxon(tax, 'Coccidium', 'Eimeriidae')

    # See 
    fixonetaxon(tax, 'Ellobiopsea', 'Protalveolata')

    # See 
    fixonetaxon(tax, 'Perkinsea', 'Protalveolata')

    # See 
    fixonetaxon(tax, 'Percolozoa', 'Excavata')

    # See 
    fixonetaxon(tax, 'Microsporidia', 'Fungi')

    # See 
    fixonetaxon(tax, 'Hyalochlorella', 'Trebouxiophyceae')

    # See 
    fixonetaxon(tax, 'Choanozoa', 'Sarcomastigota')

    # See 
    fixonetaxon(tax, 'Enigma', 'Polychaeta')

    # See 
    fixonetaxon(tax, 'Acantharia', 'Radiozoa')

    # See 
    fixonetaxon(tax, 'Heliozoa', 'Hacrobia')

    # See EOL
    fixonetaxon(tax, 'Haplosporea', 'Acanthophractida')

    # See SILVA
    fixonetaxon(tax, 'Dinophyta', 'Alveolata')

    # See NCBI
    fixonetaxon(tax, 'Jakobaceae', 'Jakobida')

    # See 
    fixonetaxon(tax, 'Sporozoa', 'Apicomplexa')

    # See 
    fixonetaxon(tax, 'Coccolithophora', 'Coccosphaerales')

    # See NCBI
    fixonetaxon(tax, 'Heteromitus', 'Heteromitidae')

    # See NCBI
    fixonetaxon(tax, 'Maissisteria', 'Cercomonadidae')

    # See 
    fixonetaxon(tax, 'Granuloreticulosea', 'Foraminifera')


print "Successes:", count[0]
