
The `Services` class provides an HTTP server and a wrapper for the
conflict analysis feature (ConflictAnalysis class).  The wrapper reads
a study from a phylesystem and writes a conflict report formatted as
JSON.

Maybe later there will be other services.

The `run_service` script prepares the arguments to Services.main and
starts it up.  It unpacks the taxonomy and synthetic tree if they're
compressed; this logic coordinates with
germinator/deploy/install-smasher.

The `service` script is an init.d-style daemon manager taking as its
argument one of `start`, `stop`, `restart`, or `status`.

The service is configured through service.config and/or through
symbolic links 'taxanomy' or 'synth.tre' in the service directory.
Get started by copying service.config.example to service.config, then
modify service.config as appropriate to local circumstances.

The service easily runs locally.  It listens on port 8081 (currently
hardwired, should be configurable).  After securing and configuring a
local taxonomy and synthetic tree, test the service with something
like:

    wget "http://localhost:8081/compare?tree1=pg_2448%23tree5223&tree2=ott"

Maybe later this will be a servlet in a proper web server container
such as Tomcat, but I wanted to keep it simple for now.  I believe the
HttpServer class can accommodate multiple concurrent connections, but
for now this is turned off.

