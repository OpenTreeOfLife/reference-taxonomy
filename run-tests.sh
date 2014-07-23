#!/bin/bash
num_t=0
num_p=0
failed=''
scriptdir=$(dirname "$0")
pushd .

cd $scriptdir
export PYTHONPATH="${PWD}${PYTHONPATH}"
for fn in $(ls feed/test/test_*.py)
do
    d=$(basename $fn)
    if python "$fn" > "feed/test/.out_${d}.py"
    then
        num_p=$(expr 1 + $num_p)
        echo -n "."
    else
        echo -n "F"
        failed="$failed $fn"
    fi
    num_t=$(expr 1 + $num_t)
done
echo
echo "Passed $num_p out of $num_t test scripts"
if test $num_t -ne $num_p
then
    echo "Failures: $failed"
    exit 1
fi

popd