molindo-elasticsync
===================

_Attention: raw brain dump ahead_

molindo-elasticsync is a simple command line tool to synchronize [elasticsearch](http://elasticsearch.org) indices
regardless of cluster version. It's built with

- [elasticsearch's Java client API (`TransportClient`)](http://www.elasticsearch.org/guide/reference/java-api/)
- [Aconex/scrutineer](https://github.com/Aconex/scrutineer)
- [OSGI](http://en.wikipedia.org/wiki/OSGi)

It's inteded usecase is upgrading/migrating between elasticsearch versions. A migration with molindo-elasticsync
follows these steps:

1. create a new cluster
1. create required indices and mappings
1. first run of molindo-elasticsync (potentially very slow)
1. (optionally) next run of molindo-elasticsync (very fast)
1. switch writing from old cluster to new
1. next run of molindo-elasticsync (even faster)
1. shutdown old cluster

The first run of molindo-elasticsync writes the complete index. There are other (and faster) ways to do this, but
you might not want to use yet another tool. Consecutive runs of molindo-elasticsync only write changes. Detecting
these changes is extemely fast (mainly depending on scan speed). Switching writes over to the new cluster and doing
a sync afterwards is save due to the versioning. Elasticsearch even keeps versions of deleted documents.

OSGI
----

I've (ab)used OSGI to support multiple elasticsearch versions on the classpath. Each version comes in it's own
OSGI bundle (currently [0.20](https://github.com/molindo/molindo-elasticsync/tree/master/at.molindo.elasticsync.es020)
and [0.90](https://github.com/molindo/molindo-elasticsync/tree/master/at.molindo.elasticsync.es090) supported). It's
my first shot at OSGI so please feel let me know if I'm doing things wrong. I know that my elasticsearch bundles 
contain all dependencies and are therefore not very OSGI-ish but I went this route for sake of simplicity. I don't
even export elasticsearch API. Additionally, my usage of `ServiceTracker` might not be very smart but I've yet to
find a better way to do it with [Blueprint](http://wiki.osgi.org/wiki/Blueprint) (which I preferred to
[DS](http://wiki.osgi.org/wiki/Declarative_Services) mainly due to my Spring background). Additionally, I omitted
[a suggested approach using bndtools](http://stackoverflow.com/questions/17742961/how-to-write-a-an-osgi-command-line-application) 
in order to keep the build fully automated with Maven.

Scrutineer
----

> Scrutineer takes a stream from your primary, and a stream from your secondary store, presumes they are sorted
identically (more on that later) and walks the streams doing a merge comparison.

It does this by doing a full index scan (see 
[search-type 'scan'](http://www.elasticsearch.org/guide/reference/api/search/search-type))

The current implementation contains the necessary sources inside the 
[at.molindo.elasticsync.scrutineer bundle](https://github.com/molindo/molindo-elasticsync/tree/master/at.molindo.elasticsync.scrutineer). 
I do hope to contribute the required changes in order to use it as a 'regular' dependency though.

Building
----

_(Requires Maven 3 and Java 7)_

```
$ git clone https://github.com/molindo/molindo-elasticsync.git
$ cd molindo-elasticsync
$ mvn clean package
$ java -jar at.molindo.elasticsync.launcher/target/molindo-elasticsync-*-full.jar --help
``` 

Usage
----

```
java -jar molindo-elasticsync-*-full.jar --help
Usage: at.molindo.elasticsync.launcher.Launcher [options]
  Options:
    --help, -h
       
       Default: false
  *     --index
       Elasticsearch index name to Verify
        --query
       Elasticsearch query
       Default: *
  *     --source
       Elasticsearch source host(s) [host[:port]]
        --sourceVersion
       Elasticsearch library version for source cluster
       Default: 0.20.*
  *     --target
       Elasticsearch target host(s) [host[:port]]
        --targetVersion
       Elasticsearch library version for target cluster
       Default: 0.90.*
  *     --type
       Elasticsearch type name to verify
        --update
       update target
       Default: false
```

Note that you can specify multiple values for `--source`, `--target`, and `--type`. Source and target are
either a host name (e.g. `localhost`, default port `9300`) or a host and port combination (e.g. `localhost:9300`).
Note that the default is performing a dry-run. Use the `--update` flag to perform an actual synchronisation.

An example invocation would be:

    java -jar molindo-elasticsync-*-full.jar \
      --source localhost:9300 --target localhost:9301 \
      --index twitter --type tweet \
      --update
