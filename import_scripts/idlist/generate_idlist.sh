# Reconstruct the entire id index from scratch by looking at all old
# OTT versions.  This script relies on all taxonomy versions being
# present and unpacked locally.

set -e

csvdir=resource/idlist/idlist-3.0/regs
captures=resources/captures.json

[ -e $captures ] || (echo Missing $captures ; exit 1)

mkdir -p $csvdir

root=../files.opentreeoflife.org/ott

function doit {
    dir=$1
    if [ ${dir:0:3} = ott ]; then
        v=${dir:3}
        if [ -d $root/$dir/ott ]; then
            tax=$root/$dir/ott
        elif [ -d $root/$dir/ott*[0-9] ]; then
            tax=`echo $root/$dir/ott*[0-9]`
        else
            echo "No taxonomy directory $tax"
            exit 1
        fi
        if [ ! -e $tax/taxonomy* ]; then
            echo "No taxonomy file $tax/taxonomy*"
            exit 1
        elif [ -e $csvdir/$dir.csv ]; then
            echo "Skipping $tax - $dir.csv exists"
        else
            python import_scripts/idlist/extend_idlist.py \
                        $csvdir \
                        $tax \
                        $dir \
                        $captures \
                        $csvdir/$dir.csv
            echo
        fi
    fi

}

for dir in `cd $root; ls -d ott1.?`; do
    doit $dir
done

for dir in `cd $root; ls -d ott2.?`; do
    doit $dir
done

for dir in `cd $root; ls -d ott2.??`; do
    doit $dir
done
