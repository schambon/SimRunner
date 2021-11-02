SimRunner
=========

This is an attempt at building a tool that can reproduce more or less faithfully "realistic" workloads on MongoDB - so you can model as accurately as possible a given workload to test infrastructure changes, or workload changes, before going live.

It should be considered a "work in progress" and comes without any support or guarantee, either from MongoDB, Inc. or myself.

TL;DR
-----

Build with `mvn package` and run with `java -jar SimRunner.jar <config file>`.

The config file specifies:
* a connection string to MongoDB
* a number of `templates` which specify document shapes and map them to collections
* a number of `workloads` which specify actions to take on those templates

Optionnally, the config file may also specify:
* a reporting interval (default 1000ms)
* an HTTP reporting server configuration

Look at the provided `sample.json` for a more or less complete example of what you can do.

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
            "remember": ["_id"],
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
* templates can `remember` top-level fields it has generated. This is useful for generating workloads later on. When the system starts, the `remember`ed fields are pre-populated from the existing collection (if it exists).
* templates can specify indexes (use normal MongoDB syntax) to create at startup.
* workloads run independently in their own threads. They can also be multi-threaded, if you want to model specific parallelism condition. __SimRunner__ doesn't support distributed execution (yet!), though, so make sure you don't provision more threads than your hardware can handle. If omitted, `threads` defaults to 1.
* workloads can be `pace`d, that is, you can specify that the operation should run every `n` milliseconds. For instance, if you want an aggregation to run every second and it takes 300ms, the thread will sleep for 700ms before running again. _Note that pacing is on a per-thread basis_: if you have 4 threads running ops at a 100ms pace, you should expect more or less 40 operations per second (10 per thread). If omitted, `pace` defaults to 0 - ie the thread will never sleep.
* workloads can use the same template language as templates. They can also refer to `remember`ed fields.

Template expressions
--------------------

The following template expressions are supported:

* `%objectid`: generate a new ObjectId
* `%integer` / `%number`: generate an int. Optionally use this form: `{"%integer": {"min": 0, "max": 50}}` to specify bounds
* `%natural`: generate a positive int. Optionally use this form: `{"%natural": {"min": 400, "max": 5000}}` to specify bounds
* `%now`: current date
* `%date`: create a date between the Unix Epoch and 10 years in the future, or specify `min`/`max` bounds in a subdocument, either as ISO8601 or as EJSON dates (hint: `{$date: "2021-01-01"}` works but `"2021-01-01"` doesn't as it's not valid ISO8601).
* `%binary`: create random blob of bytes. Use this form: `{"%binary": {"size": 1024}}` to specify the size (default 512 bytes)
* `%sequence`: create a sequential number. Note - there is only one sequence.
* `%uuidString`: random UUID, as String
* `%uuidBinary`: random UUID, as native MongoDB UUID (binary subtype 4)
* `{"%array": {"min": integer, "max": integer, "of": { template }}}`: variable-length array (min/max elements, of subtemplate).

Any other expression will be passed to JavaFaker - to call `lordOfTheRings().character()` just write `%lordOfTheRings.character`. You can only call faker methods that take no arguments. Note that this uses reflection, which is fairly slow.

Template variables (interdependant fields)
------------------------------------------

It is also possible to create _variables_, which you can reuse multiple times in a template.

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
            "birth": "##birthday",
            "death": {"%date": {"min": "##birthday", "max": {"$date": "1950-01-01"}}}
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

... and ensures that `death` is in fact posterior to `birth`. Such cross-field dependencies (within a single document) is possible by creating a variable _birthday_ (using the normal templates) and generating the field `death` by referencing it (using the `##` prefix) in the parameters of the `%date` generator.

Note: you can't reference a variable in another variable declaration.


Supported workload operations
-----------------------------

## Insert

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

## find

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

## updateOne and updateMany

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

## replaceOne

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

## replaceWithNew

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

## aggregate

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

Limitations
-----------

* Does not support arrayfilters, hint
* Only top-level fields can be remembered
* No support for transactions or indeed, multi-operation workflows ("read one doc and update another")

Plans
-----

If time allows, I'd like to implement the following features:
* more generic, path-aware "remember" feature, that can work with embedded/array
* HTTP monitoring console (dynamic graphs!)
* HTTP command interface (start / stop jobs...)
* distributed mode (building on the above)
