#!/bin/bash

if [ -d tax/ott -a ! -e tax/ott/.incomplete ]; then
    echo saving old tax/ott
    mv tax/ott tax/ott.save
fi

echo copying
mkdir tax/ott
touch tax/ott/.incomplete
cp -p old/ott2.10draft11/* tax/ott/

echo fixing synonyms
python fix_synonyms.py < tax/ott/synonyms.tsv > tax/ott/synonyms.tsv.new
mv -f tax/ott/synonyms.tsv.new tax/ott/synonyms.tsv

echo fixing Eukaryota record
sed -i "" -e s/,worms:Eukaryota,if:Eukaryota,gbif:Eukaryota,irmng:Eukaryota// tax/ott/taxonomy.tsv

echo fixing SAR record
sed -i "" -e s/,ncbi:SAR,worms:SAR// tax/ott/taxonomy.tsv

rm tax/ott/.incomplete
