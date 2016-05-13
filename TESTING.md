
## Testing the conflict service

If it's running on devapi:

    ../germinator/ws-tests/run_tests.sh https://devapi.opentreeoflife.org

It's also possible to just run it locally, something like this:

    (create files or symlinks 'ott' and 'synth.tre' in services directory)
    services/service start
    ../germinator/ws-tests/run_tests.sh localhost: host:translate=true

