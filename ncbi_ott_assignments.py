
# (ncbi_id, ott_id, name)

ncbi_assignments_list = [
    ('3071', '339002', 'Chlorella'),
    ('56708', '342868', 'Tetraphyllidea'),
    ('1883', '772892', 'Streptomyces'),

    # Looking at deprecated ids list for OTT 2.9 draft JAR 2015-06-27
    ('28995', '17230', 'Pleurotus pulmonarius'),      # 
    ('110229', '539160', 'Crepidotus applanatus'),    # 
    ('67700', '1067404', 'Amanita citrina'),    # 
    ('182065', '242147', 'Hygrocybe spadicea'),
    ('206324', '51753', 'Gymnopus luxurians'),
    ('163799', '527601', 'Holwaya mucida'),  # pg_2607,pg_1040,pg_438,pg_854,pg_241,pg_517,pg_862,pg_2390	
    ('165588', '356618', 'Inocybe lanuginosa'),
    ('467885', '1027637', 'Inocybe hirtella'),   # 
    ('227429', '1097623', 'Gymnopus nonnullus'),   # 
    ('165398', '974129', 'Cortinarius infractus'),
    ('730', '1047050', 'Haemophilus ducreyi'),
    ('738', '1047118', 'Haemophilus parasuis'),      # 
    ('366833', '691803', 'Puccinia andropogonis'),    # 
    ('112419', '314112', 'Ramaria flavobrunnescens'),    # 
    ('131542', '41735', 'Russula fragilis'),     # 

    # Fix SILVA lossage (more sample contamination)
    ('208348', '890898', 'Puccinia triticina'),

    # This set results from placement in wrong division
    # that got fixed
    ('324755', '422518', 'Bostrychia tenella'),  # 
    ('324754', '422519', 'Bostrychia simpliciuscula'),  # 
    ('324756', '422521', 'Bostrychia arbuscula'),  # 
    ('161377', '115255', 'Bostrychia calliptera'),  # 
    ('103714', '782488', 'Bostrychia radicans'),  # 
    ('103713', '782487', 'Bostrychia moritziana'),  # 
    ('324761', '948215', 'Bostrychia montagnei'),  # 
    ('324760', '948214', 'Bostrychia flagellifera'),  # 
    ('324764', '948204', 'Bostrychia scorpioides'),  # 
    ('92392', '71632', 'Lamprospora kristiansenii'),    # 	pg_409	
    ('352994', '269886', 'Lamprospora dictydiola'),  # 	(many)	
    ('1647800', '5293089', 'Terana coerulea'),#  (many)	Terana coerulea = Byssus phosphorea

    # This is a funny one.  
    # OTT 2.8: 935049 = Ochrothallus multipetalus = ncbi:550753,irmng:10923427
    # ncbi:550753 = replaced by ncbi:441510 = Pycnandra fastuosa = ott:453118
    # ott:935049 used by pg_1017, ott:453118 used by pg_227,pg_1017,pg_188
    # Ambiguity with ott:3900562 = Niemeyera multipetala = gbif:5333730,irmng:10245174
    # Do nothing, allow deprecation to happen normally.
    # ('441510', '935049', 'Ochrothallus multipetalus'),  # 	pg_1017	 - was 550753

    # More from the deprecated list
    ('40296', '6232', 'Penicillium italicum'),
    ('40296', '6232', 'Penicillium italicum'),
    ('40520', '695480', '[Ruminococcus] obeum'),  # 	pg_2448		silva:X85101,

    # OTT id 395048 = ncbi:6239 (5252840 is the SILVA one)
    ('6239', '395048', 'Caenorhabditis elegans'),  # 	pg_2925,pg_2886		silva:JN975069,

    ('38483', '5252708', 'Sclerotinia homoeocarpa'),  # 	pg_2229,pg_2462		silva:JU096305,
    ('352995', '5303440', 'Lamprospora crouanii'),  # 	pg_791,pg_949		if:356889,
    ('171480', '350620', 'Cortinarius glaucopus'),  # 	pg_1240,pg_1610,pg_438,pg_1060	
    ('279506', '1029985', 'Boletus bicolor'),  # 	pg_2597,pg_438	
    ('5066', '749901', 'Aspergillus wentii'),  # 	pg_2427,pg_438	

    # JAR 2014-05-13
    ('214887', '4773', 'Phytopythium montanum'),
    ('317423', '289517', 'Rinorea dimakoensis'),
]

def checkem():
    for (ncbi_id, ott_id, name) in ncbi_assignments_list:
        print 'grep "^%s	" tax/ott2.8/taxonomy.tsv || echo No row with OTT id %s - %s' % (ott_id, ott_id, name)
        print 'grep "^%s	" tax/ncbi/taxonomy.tsv || echo No row with NCBI id %s - %s' % (ncbi_id, ncbi_id, name)
        print 'echo'

if __name__ == '__main__':
    checkem()
