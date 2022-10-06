SimRunner
=========

SimRunner is a tool that binds:
- a powerful data generator for MongoDB
- a declarative and highly scalable workload generator

You can think of SimRunner as (a bit more than) the sum of [mgeneratejs](https://www.npmjs.com/package/mgeneratejs) and [POCDriver](https://github.com/johnlpage/pocdriver). Generate documents as you would with mgenerate, and inject them to MongoDB with a super-fast multithreaded workload framework.

Workloads are declarative in that you describe them in JSON. Just write your queries, your threading options, and you're set. No code to write. Just make sure you close all your curly brackets. Of course, since MongoDB queries are themselves BSON Documents, you can use the same expression language as in the data generator to introduce some variability. Workloads have a few tricks up their sleeve - for example you can build a "dictionary" of known or generated values that you can reuse in your queries.

Thanks to all this, SimRunner can reproduce fairly closely realistic workloads on MongoDB - so you can model as accurately as possible a given workload to test infrastructure changes, or workload changes, before going live.

It should be considered a "work in progress" and comes without any support or guarantee, either from MongoDB, Inc. or myself.


TL;DR
-----

Build with `mvn package` and run with `java -jar SimRunner.jar <config file>`. Needs at least Java 11 (tested with 17 as well).

The config file specifies:
* a connection string to MongoDB
* a number of `templates` which specify document shapes and map them to collections
* a number of `workloads` which specify actions to take on those templates

Optionnally, the config file may also specify:
* a reporting interval (default 1000ms)
* an HTTP reporting server configuration

Look at the provided `sample.json` for a more or less complete example of what you can do.

If you enable the HTTP interface in the config file, point your browser at http://(host):(port) to view a dynamic graph of throughput and latency.
    
For distributed metrics collection (aggregate results from multiple SimRunners, if you have a very intensive workload) take a look at https://github.com/schambon/SimRunner-Collector

For easy setup in EC2, a quick and dirty script to provision a machine etc. is at https://github.com/schambon/launchSimRunner

Config file
-----------

The config file is parsed as Extended JSON - so you can specify things like dates or specific numeric types if you want to.

Let's look at an example:
```
{
    "connectionString": "mongodb://localhost:27017",
    "reportInterval": 1000,
    "http": {
        "enabled": false,
        "port": 3000,
        "host": "localhost"
    },
    "mongoReporter": {
        "enabled": true,
        "connectionString": "mongodb://localhost:27017",
        "database": "simrunner",
        "collection": "report",
        "drop": false,
        "runtimeSuffix": false
    },
    "templates": [
        {
            "name": "person",
            "database": "test",
            "collection": "people",
            "drop": false,
            "template": {
                "_id": "%objectid",
                "first": "%name.firstName",
                "last": "%name.lastName",
                "birthday": "%date.birthday"
            },
            "remember": ["_id", { "field": "first", "preload": false}],
            "indexes": []
        }
    ],
    "workloads": [
        {
            "name": "Insert some people",
            "template": "person",
            "op": "insert",
            "threads": 1,
            "pace": 100,
            "batch": 1000
        },
        {
            "name": "Find people by key",
            "template": "person",
            "op": "find",
            "params": {
                "filter": { "_id": "#_id" }
            },
            "threads": 4
        }
    ]
}
```

This config specifies a connection to a local MongoDB without authentication, a simple "person" template that maps to the `test.people` collection, and two workloads: one that inserts new people in the database at a rate of one batch of 1000 every 100ms, and one that looks up a single person by `_id`.

A few things can be seen already:
* templates are _named_ and referenced by name in the workloads.
* workloads are also named. This is for collecting statistics.
* templates are pretty flexible. You can use all normal EJSON expressions (like `{"$date": "2021-10-27T00:00:00Z"}`) as well as _generator expressions_ prefixed by `%`. Generator expressions allow you to randomly generate objectids, dates, and almost everything that is supported by [JavaFaker](https://github.com/DiUS/java-faker). 
* templates can `remember` fields it has generated, in order to create libraries of valid data. This is useful for generating workloads later on. When the system starts, the `remember`ed fields are pre-populated from the existing collection (if it exists) by default. See further down for advanced remember features.
* templates can specify indexes (use normal MongoDB syntax) to create at startup.
* workloads run independently in their own threads. They can also be multi-threaded, if you want to model specific parallelism condition. If omitted, `threads` defaults to 1.
* workloads can be `pace`d, that is, you can specify that the operation should run every `n` milliseconds. For instance, if you want an aggregation to run every second and it takes 300ms, the thread will sleep for 700ms before running again. _Note that pacing is on a per-thread basis_: if you have 4 threads running ops at a 100ms pace, you should expect more or less 40 operations per second (10 per thread). If omitted, `pace` defaults to 0 - ie the thread will never sleep.
* workloads can use the same template language as templates. They can also refer to `remember`ed fields.


Templates
---------

### Template expressions

The following template expressions are supported:

* `%objectid`: generate a new ObjectId
* `%integer` / `%number`: generate an int. Optionally use this form: `{"%integer": {"min": 0, "max": 50}}` to specify bounds
* `%natural`: generate a positive int. Optionally use this form: `{"%natural": {"min": 400, "max": 5000}}` to specify bounds
* `%long`, `%double`, and `%decimal` work as `%number` and yield longs, doubles, and decimals. Note that BSON doesn't have a 32 bit float type, so we don't support floats.
* `%gaussian`: generate a number following an approximate Gaussian distribution. Specify `mean`, `sd` for the mean / standard deviation of the Gaussian. Optionally, set `type` to `int` or `long` for integer values (any other value is understood as double)
* `%product`: product of number array specified by `of`. Parameter `type` (either `long` or `double`, default `long`) specifies how to cast the result
* `%sum`: like `%product` but sums the `of` array
* `%abs`: absolute value
* `{"%mod": {"of": number, "by": number}}`: modulus (`of` mod `by`)
* `%now`: current date
* `%date`: create a date between the Unix Epoch and 10 years in the future, or specify `min`/`max` bounds in a subdocument, either as ISO8601 or as EJSON dates (hint: `{$date: "2021-01-01"}` works but `"2021-01-01"` doesn't as it's not valid ISO8601).
* `{"%plusDate": {"base": date, "plus": amount, "unit": unit}}`: adds some time to a date. `unit` is either: `year`, `month`, `day`, `minute`
* `{"%ceilDate": {"base": date, "unit": unit}}`: align date to the next unit (eg next hour, day...) - default unit is `day`
* `{"%floorDate": {"base": date, "unit": unit}}`: truncate date to the unit (eg hour, day...) - default unit is `day`
* `%binary`: create random blob of bytes. Use this form: `{"%binary": {"size": 1024}}` to specify the size (default 512 bytes). Use `"as": "hex"` to encode in a hex string rather than a binary array
* `%sequence`: create a sequential number from a *global* sequence.
* `%threadSequence`: create a sequential number from a *per-thread* sequence.
* `%uuidString`: random UUID, as String
* `%uuidBinary`: random UUID, as native MongoDB UUID (binary subtype 4)
* `{"%array": {"min": integer, "max": integer, "of": { template }}}`: variable-length array (min/max elements, of subtemplate).
* `{"%keyValueMap": {"min": integer, "max": integer, "key": { template resolving to string }, "value": { template } }}`: variable-length subdocument with keys/values generated from the provided templates. Key uniqueness is enforced at generation time.
* `{"%dictionary": {"name": "dictionary name"}}`: pick a value from a dictionary (synonym with `"#dictionary name"`)
* `{"%dictionaryConcact": {"from": "dictionary name", "length": (number), "sep": "separator}}`: string _length_ values from a dictionary, separated by _sep_
* `{"%longlat": {"countries": ["FR", "DE"], "jitter": 0.5}}`: create a longitude / latitude pair in one of the provided countries. `jitter` adds some randomness - there are only 30ish places per country at most in the dataset, so if you want locations to have a bit of variability, this picks a random location within `jitter` nautical miles (1/60th of a degree) of the raw selection. A nautical mile equals roughly 1800 metres.
* `{"%coordLine": {"from": [x, y], "to": [x, y]}}`: create a long,lat pair (really an x,y pair) that is on the line between `from` and `to`.
* `{"%stringTemplate": {"template": "some string}}`: string based on a template, where `&` is a random digit, `?` is a random lowercase letter and `!` is a random uppercase letter. All other characters in the template are copied as-is.
* `{"%stringConcat": {"of": [x, y, z, ...]}}`: concatenate as string the list of inputs.
* `{"%toString": {"of": template}}`: make "template" into a string (eg long -> string)
* `{"%descend": {"in": {document}, "path": "dot.delimited.path"}}` is used to traverse documents. This should be mostly legacy, as `#document.dot.delimited.path` is equivalent.
* `%workloadName`: name of the current workload
* `%threadNumber`: number of the thread in the current workload

Any other expression will be passed to JavaFaker - to call `lordOfTheRings().character()` just write `%lordOfTheRings.character`. You can only call faker methods that take no arguments.

The best way to generate random text is to use `%lorem.word` or `%lorem.sentence`.

### Template variables (interdependant fields)

It is possible to create _variables_, which are evaluated once and reused multiple times in a template.

For example, look at this `templates` section:

```
"templates": [
    {
        "name": "19th century people",
        "database": "simrunner",
        "collection": "19th_century",
        "variables": {
            "birthday": {"%date": {"min": {"$date": "1800-01-01"}, "max": {"$date": "1900-01-01"}}}
        },
        "template": {
            "first": "%name.firstName",
            "last": "%name.lastName",
            "birth": "#birthday",
            "death": {"%date": {"min": "#birthday", "max": {"$date": "1950-01-01"}}}
        }
    }
]
```

This creates records like this one:

```
{
    _id: ObjectId("61815f70cb4ef14d9a4a28f5"),
    first: 'Zenaida',
    last: 'Barton',
    birth: ISODate("1807-06-12T17:28:35.949Z"),
    death: ISODate("1865-04-15T15:05:13.892Z")
}
```

... and ensures that `death` is in fact posterior to `birth`. Such cross-field dependencies (within a single document) is possible by creating a variable _birthday_ (using the normal templates) and generating the field `death` by referencing it (using the `#` prefix) in the parameters of the `%date` generator.

Note: you can't reference a variable in another variable declaration.

Note: variables can also be defined in a workload definition, and used in templated expressions within that workload.

### Dictionaries

Dictionaries let you create custom sets of data that the template system will pick into. Dictionaries can be a static list, a JSON file read on disk, a plain text file read on disk, or even a MongoDB collection from your cluster.

Example:

```
"dictionaries": {
    "names": ["John", "Jill", "Joe", "Janet"],
    "statuses": ["RUNNING", {"status": "DONE", "substatus": "OK"}, {"status": "DONE", "substatus": "NOK"}],
    "characters": {"file": "characters.json", "type": "json"},
    "locations": {"file": "locations.txt", "type": "text"},
    "identifiers": {"type": "collection", "collection": "referenceIdentifiers", "db": "refdb", "query": {"valid": true}, "limit": 1000, "attribute": "name"}
}
```

This creates five dictionaries:
- `names` is an inline list of strings
- `statuses` is an inline list of BSON values - this shows strings and documents, but it could be anything that is expressible in Extended JSON
- `characters` is a JSON file read from disk. The file __must__ contain a single document with an array field called `data` that contains the dictionary (similar to inline dictionaries)
- `locations` is a plain text file, a dictionary entry per line (only strings, no other or mixed types)
- `identifiers` is read from `refdb.referenceIdentifiers` collection on the cluster. Only `collection` is mandatory, for the other parameters default values are:
  * `db`: inherited from template definition
  * `query`: `{}`
  * `limit`: 1,000,000 (same as remembered field prefetching)
  * `attribute`: attribute to use for the dictionary

Dictionaries can be used in templates:
- either directly (pick a word in the dict) with the `"#dict"` or `{"%dictionary": {"name": "dict"}}` syntaxes.
- or by concatenating multiple entries of a dictionary. This is useful to create variable-length text based out of real words, rather than Lorem Ipsum. Most UNIX/Linux systems (including macOS) have a dictionary for spell checking at /usr/share/dict/words, that can be read directly by SimRunner to make a (nonsensical) text that you can query from, for example using Atlas Search.

### Advanced remembered fields

At its simplest, you can create a library of values by specifying `"remember": ["value"]` in the template. "value" can be a top-level or nested field (with `dotted.path` syntax), however note that the template manager will not traverse arrays (so if you have `{ a: [ {b:1}, {b:2}]})` you _cannot_ say `"remember": ["a.b"]`).

Beyond listing fields, you can use the following long form: `"remember": [ {"field": "x", "compound": ["x", "y"], "name": "name", "preload": true, "number": 10000} ]`. This long form provides the following features:
- `field`: field name or field path, like simply listing in `remember`
- `compound`: instead of managing a single field, generate a document by compounding several fields. For example, `"compound": [ "x", "y.z" ]` will remember a value of the form `{"x": ..., "y_z": ...}`. Note that paths are descended for identifying values, but keys are named by substituting "_" for ".". If `compound` is present, `field` is ignored.
- `name`: this is the name of the value library, which will be used in queries. By default, it is the same as `field`. If using `compound`, it is mandatory to specify a name.
- `preload`: should we load values from the existing collection at startup (default: true)?
- `number`: how many distinct values should we preload from the existing collection at startup (default: one million)?

Compounding is useful when you want to run complex queries and still ensure they do match some existing records. For example, with the following template:
```
{
    "name": "person",
    "template": { 
        "_id": "%objectid",
        "first": "%name.first",
        "last": "%name.last",
        "date of birth": "%date.birthday"
        (...)
    },
    "remember": ["first", "last"]
}

```

If you want to run a query on both `first` and `last`, you could do that by `remember`ing both fields and running a workload like:

```
{
    "template": "person",
    "op": "find",
    "params": {
        "filter": {
            "first": "#first",
            "last": "#last"
        }
    }
}
```

but it would pick a first name and a last name at random - most of the time yielding a combination that doesn't actually exist in the database. Instead, use a compound rembember specification:

```
{
    "name": "person",
    "template": { 
        "_id": "%objectid",
        "first": "%name.first",
        "last": "%name.last",
        "date of birth": "%date.birthday"
        (...)
    },
    "remember": [{"compound": ["first", "last"], "name": "firstAndLast"}]
}
```

and query it like this:

```
{
    "template": "person",
    "op": "find",
    "variables": {
        "compoundVar": "#firstAndLast"
    },
    "params": {
        "filter": {
            "first": "#compoundVar.first",
            "last":  "#compoundVar.last"
        }
    }
}
```

### Hash-name evaluation

When the system encounters a `#name` token, it is resolved in the following order:

1. Variables
2. Remembered fields
3. Dictionaries

For compatibility's sake, the `##name` syntax can still be used to refer to variables.

When a token like `#name.sub.document` is found, documents are descended as expected. Arrays are not descended.

### Create Options

A template can define a `createOptions` document with the same syntax as in the [create database command](https://docs.mongodb.com/manual/reference/command/create/). This is useful to create capped collections, or timeseries, or validation.

Note that views and collation options are not supported.

### Sharding

A template can define basic sharding options:
- shard key
- presplit chunks

Example configuration:
```
"sharding": {
    "key": { "first": 1 },
    "presplit": [
        { "point": {"first": "A"}, "shard": "shard01" },
        { "point": {"first": "M"}, "shard": "shard02" }
    ]
}
```

Presplit is optional. It is not possible to presplit a hashed sharded collection.

Some notes:
- the first chunk (from minKey to the first point) remains on the primary shard of the collection.
- if a collection is already sharded, all sharding options are ignored (no resplitting or anything like that).
- if a collection is sharded, even if sharding isn't configured, the `drop` option is reinterpreted to be `deleteMany({})` (delete all documents rather than dropping). This is because dropping a collection drop requires flushing the cache on all routers, which is not practical from the client side.
- it is up to the user to make sure the cluster configuration (like the number and names of the shards) aligns with the sharding configuration here. No checks are made.

Workloads
---------

### Common parameters

* threads: number of worker threads for this workload
* batch: (insert or updateOne only) bulk write batch size
* pace: operations should run every _pace_ milliseconds (on each thread)
* readPreference: primary, secondary, primaryPreferred, secondaryPreferred, nearest (no tag sets)
* readConcern: majority, local, available, linearizable, snapshot
* writeConcern: majority, w1, w2 (no tag sets)
* stopAfter: stop after n iterations, for each thread.

Note that stopAfter counts full iterations of the workload on a single thread - e.g. if you're inserting documents in batches of 100 on 10 threads, and you want 1,000,000 documents in the collection, then you need to set `"stopAfter": 1000`. Said another way, total docs = stopAfter * threads * batch.

### Insert

```
{
    "name": "Insert a person",
    "template": "person",
    "op": "insert",
    "threads": 1,
    "batch": 0
}
```

Inserts a new instance of the named template.

Options:
* batch: bulk insert. Omit or specify `"batch": 0` for unit insert.

### find

```
{
    "name": "Find by first name",
    "template": "person",
    "op": "find",
    "params": {
        "filter": { "first": "#first" },
        "sort": { "birthday": -1 },
        "project": { "age": { "$subtract": [{"$year": "$$NOW"}, {"$year": "$birthday"}]}},
        "limit": 100
    }
}
```

Executes a simple query. `filter` can use the template language (`{"first": "%name.first"}`  looks for a random first name) including references to `remember`ed fields (`{"first": "#first"}`). The result is passed to MongoDB as is, so you can use normal operators (`$lt`, `$gte`, etc.) including `$expr` for pretty complex searches.

`find` workloads always fetch all the results that match. Use `limit` to simulate `findOne`.

Options:
* filter: the query filter, can use templates
* sort: sorting specification, does _not_ use templates
* project: projection specification, does _not_ use templates
* limit: number of documents to fetch

### updateOne and updateMany

```
{
    "name": "Update last name",
    "template": "person",
    "op": "updateOne",
    "params": {
        "filter": { "_id": "#_id"},
        "update": { "$set": { "last": "%name.lastName"}},
        "upsert": true
    }
}
```

Performs an update (one or many).

Options:
* filter: the query filter, can use templates
* update: the update specification, must include mutation operators ($). `update` can be a pipeline for expressive updates.
* upsert: is this an upsert? (defaults to `false`)

### replaceOne

```
{
    "name": "Replace one person",
    "template": "person",
    "op": "replaceOne",
    "params": {
        "filter": { "_id": "#_id" },
        "update": { "first": "%name.firstName", "last": "#last" },
        "upsert": true
    }
}
```

Replaces a document with an inline template. Note, if the `update` field specifies an `_id` field, that is stripped. Also note that fields generated inline will **not** be remembered.

Options:
* filter: the query filter, can use templates
* update: the replacement document, can use templates (including references)
* upsert: is this an upsert (defaults to `false`)

### replaceWithNew

```
{
    "name": "Replace one person with a new template instance",
    "template": "person",
    "op": "replaceWithNew",
    "params": {
        "filter": { "first": "%name.firstName", "last": "%name.lastName" },
        "upsert": true
    }
}
```

Replaces a document with a new instance of its original template. The same notes as `replaceOne` apply.

### aggregate

```
{
    "name": "sum numbers of random name",
    "template": "person",
    "op": "aggregate",
    "params": {
        "pipeline": [
            {"$match": {"first": "#first"}},
            {"$group": {"_id": null, "count": {"$sum": "$embed.number"}}}
        ]
    }
}
```

Run an aggregation pipeline.

Options:
* pipeline: the pipeline to run. No particular restrictions (if on Atlas, this can call Atlas search `$search` indexes for example). All stages are run through the template generator.

Output
------

A report is printed on stdout (as well as in a `simrunner.log` file) every second. For each specified workload, it will print the following:

```
18:18:59.403 [main] INFO  org.schambon.loadsimrunner.Reporter - 43 - Insert:
584 ops per second
58400 records per second
3.311301 ms mean duration
3.000000 ms median
4.000000 ms 95th percentile
(sum of batch durations): 19338.000000
100.000000 / 100.000000 / 100.000000 Batch size avg / min / max
```

Line by line, this consists of:
* timestamp, workload name (here: "Insert")
* total number of operations per second (if doing bulk writes, it's the number of batches we sent)
* total number of records per second - for insert, that's number of records inserted, for find it's the number of records fetched, etc.
* mean duration of an operation
* median duration of an operation
* 95th percentile duration of an operation
* average / min / max batch size - this is mostly useful for `find` and `updateMany`, tells you how many records are returned / updated per operation. For `insert` it should be exactly equal to your specified batch size.
* util% - this tells you approximately the percentage of time spent interacting with the database (if you have multiple threads running, it can be more than 100%). This is useful to decide if apparent poor performance is due to the DB or to the test harness itself)

HTTP interface
--------------

In the `http` section you can configure a REST interface to get periodic reports as JSON.

```
"http": {
    "enabled": false,
    "port": 3000,
    "host": "localhost"
}
```

* `enabled`: boolean, enable the HTTP server (default: false)
* `port`: int, what port to listen to (default: 3000),
* `host`: string, what host/IP to bind to (default: "localhost"). If you want to listen to the wider network, set a hostname / IP here, or "0.0.0.0".

Once the system has started, use `curl host:port/report` for a list of all reports since the beginning, or `curl host:port/report\?since=<ISO date>` for a list of all reports since the provided date. This answers with JSON similar to this:

```
[
    {
        "report": {
            "Find by key": {
                "95th percentile": 1.0,
                "client util": 96.99823425544469,
                "max batch size": 1.0,
                "mean batch size": 0.98914696529794,
                "mean duration": 0.2278446011336935,
                "median duration": 0.0,
                "min batch size": 0.0,
                "ops": 4339,
                "records": 4292
            },
            "Insert": {
                "95th percentile": 83.3,
                "client util": 1.5597410241318423,
                "max batch size": 1.0,
                "mean batch size": 1.0,
                "mean duration": 15.9,
                "median duration": 1.0,
                "min batch size": 1.0,
                "ops": 1,
                "records": 1
            }
        },
        "time": "2021-11-02T12:46:23.908Z"
    }
]
```

MongoDB Reporting
--------------

The reports SimRunner builds can add up to a bunch of data. Of course you can visualise them in the http interface (and download as CSV to work in your favourite spreadsheet program), but at some point it would be nice to send it to something more robust, like... a database?

If you add a `mongoReporter` section to your config file, SimRunner will write all reports to the provided MongoDB collection. Note that if the collection doesn't exist (or if you specify `"drop": true`), then it will create it as a [time series collection](https://www.mongodb.com/docs/manual/core/timeseries-collections/). This requires at least MongoDB 5.0. If you're running on older versions, just create your report collection manually.

Configuration:

```
"mongoReporter": {
    "enabled": true,
    "connectionString": "mongodb://localhost:27017",
    "database": "simrunner",
    "collection": "report",
    "drop": false,
    "runtimeSuffix": true
}
```

* `enabled`: should we log results to MongoDB ? (default: `false`)
* `connectionString`: MongoDB connection string. Does not have to be the same as the tested cluster (arguably, should not.)
* `database`: database in which to store the reports
* `collection`: collection in which to store the reports
* `drop`: drop the collection? (default: `false`)
* `runtimeSuffix`: if `true`, the UTC date and time when SimRunner starts up is appended to the collection name. This creates in effect one collection per test run. (default: `false`)

Note that the HTTP interface doesn't need to be running for the MongoReporter to work. They are two completely different subsystems.

Tips and tricks
---------------

### Preloading remembered nested fields

Only top-level fields can be remembered at insert time. However, one neat trick is to first insert a lot of data, then apply the read patterns in a second step. If the fields are single-valued then you can use "remember" to preload nested data by using the dotted syntax.

For instance:

```
{
    "templates": [{
        "name": "person",
        "template": { ... },
        "remember": [ "sub.doc" ]
    }],
    "workloads": [{
        "name": "find by subdoc",
        "template": "person",
        "op": "find",
        "params": {
            "filter": { "sub.doc": "#sub.doc" }
        }
    }]
}
```

This will NOT remember fields as it inserts them, but it will preload them all right! (Thanks to Khalid Dar for pointing that out)

### Mix remembered fields and variables

The `#field` expression takes a value from the "field" bag at random every time it is executed. If you need the same value twice, use a variable (which is set once per template). For instance, to create a workload that finds a document by first name and creates a new field by appending a random number to the first name, you can do this:

```
"workloads": [{
    "name": "fancy update",
    "template": "sometemplate",
    "variables": {
        "first": "#first"
    },
    "op": "updateOne",
    "params": {
        "filter": { "first": "##first" },
        "update": { "$set": {"newfield": {"%stringConcat": ["##first", " - ", "%natural"]}}}
    }
}]
```

### Comment your code!

JSON has no syntax for comments... but SimRunner will happily ignore configuration keys it doesn't recognise. We consider it good practice to add a `comment` key to your workload definitions.

Limitations
-----------

* Does not support arrayfilters, hint
* No declarative support for transactions or indeed, multi-operation workflows ("read one doc and update another") - you have to use custom runners for that.

