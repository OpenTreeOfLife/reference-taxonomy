
R=../..

echo "Number of curated source taxonomy adjustments (pre-alignment):"
python $R/util/count_patches.py $R/adjustments.py | tail -1 | \
  (read nn mm; echo $nn)

echo

n1=`grep "^[a-z]" $R/feed/ott/edits/*.tsv | wc | (read nn mm; echo $nn)`
n2=`cat $R/feed/amendments/amendments-1/amendments/*.json | \
      grep original_label | wc | (read nn mm; echo $nn)`
n3=`python $R/util/count_patches.py $R/amendments.py | tail -1 | \
  (read nn mm; echo $nn)`
echo "Number of curated taxonomy patches (post-merges):"
echo $((n1 + n2 + n3))
echo "  of which feed/ott/edits: $n1"
echo "           amendments: $n2"
echo "           amendments.py: $n3"

echo

echo "Number of separation taxa"
tail +2 $R/tax/separation/taxonomy.tsv | wc | \
  (read nn mm; echo $nn)
