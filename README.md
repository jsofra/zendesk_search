# Zendesk Search

*Zendesk Search* is an interactive command-line application that allows an operator to search through catalogues of Zendesk data and return the results in a human readable format.

## Requirements
The development of the program requires:
- GraalVM (Java 11)
    - The program has been written in Clojure and requires Java 11 since that is the highest version supported by GraalVM.
- GraalVm `native-image`
    - GraalVM has been used to compile the program to a native image. This is to improve the start-up time which is important for a command-line program.
- Clojure CLI tools
    - Used for dependency resolution and building.

## Installation

### Java SDK + GraalVM native-image

The Java and GraalVM requirements can be installed using [SDKMAN!](https://sdkman.io/)

Install SDKMAN! first by following these instructions https://sdkman.io/install

- GraalVM
    - Install a version of GraalVM compatible with Java 11:

        `sdk install java 20.0.0.r11-grl`

- GraalVM `native-image`
    - Install the `native-image` component from the GitHub catalog by its name:

        `gu install native-image`

### Clojure CLI tool

Follow the (Clojure installer and CLI tools)[https://clojure.org/guides/getting_started#_clojure_installer_and_cli_tools] getting started guide to install.

## Usage

### Tests

Run the project's tests:

    `$ clojure -A:test:runner`

Run just the unit tests:

    `$ clojure -A:test:runner -i unit`

Run just the integration tests:

    `$ clojure -A:test:runner -i integration`

### Build

There are two options for packaging the application. Either a regular Java jar file or a native image build using GraalVM.

The native image will have a faster start up time and so is the preferred method of packaging.
You may want to build a jar if this application is used in an environment that does not support the native image build process.

Build a jar:

    `$ clojure -A:uberjar`

Build a native image:

    `$ clojure -A:native-image --verbose`

### Run

The program does not take any arguments. The catalogues data is configured in `.zendesk_search/catalogues.clj`.

Run the project directly using Clojure CLI tools:

    `$ clojure -m jsofra.zendesk-search.cli`

Run that jar:

    `$ java -jar zendesk_search.jar`

Run the native image:

    `$ ./zendesk-search`

## Design

As the Zendesk catalogues of data grow larger doing a full scan to search through the data will not achieve reasonable performance, with O(n) linear search. This gets worse when resolving relationships, O(n) * r (where r is the number of relationships to resolve). Instead if we pay an upfront cost, and memory cost, we can build an index to improve the search performance.

### Inverted Index

This application implements an (inverted index)[https://en.wikipedia.org/wiki/Inverted_index]. This is implemented by transforming the list of entities (hash maps of keys and values) to one large nested hash map that allow us to look up a value and find a indexes that can be mapped back to the entities.

An example of such a transformation can be seen below.

``` clojure
;; List of entities

[;; index 0
 {"_id"             1,
  "name"            "Francisca Rasmussen",
  "organization_id" 119,
  "role"            "admin",
  "created_at"      "2016-04-15T05:19:46 -10:00",
  "tags"            ["Springville", "Sutton", "Hartsville/Hartley", "Diaperville"]}
 ;; index 1
 {"_id"             2,
  "name"            "Cross Barlow",
  "organization_id" 106,
  "role"            "admin",
  "created_at"      "2016-06-23T10:31:39 -10:00",
  "tags"            ["Foxworth" "Woodlands" "Herlong" "Henrietta"]}]
```

``` clojure
;; Inverted index

{"_id"             {"1"                          [0],
                    "2"                          [1]},
 "name"            {"francisca rasmussen"        [0],
                    "francisca"                  [0],
                    "rasmussen"                  [0],
                    "cross barlow"               [1],
                    "cross"                      [1],
                    "barlow"                     [1]},
 "organization_id" {"119"                        [0],
                    "106"                        [1]},
 "role"            {"admin"                      [0, 1]},
 "created_at"      {"2016-04-15t05:19:46 -10:00" [0],
                    "2016-04-15"                 [0],
                    "2016-06-23t10:31:39 -10:00" [1],
                    "2016-06-23"                 [1]},
 "tags"            {"springville"                [0],
                    "sutton"                     [0],
                    "hartsville/hartley"         [0],
                    "diaperville"                [0],
                    "foxworth"                   [1],
                    "woodlands"                  [1],
                    "herlong"                    [1],
                    "henrietta"                  [1]}}
```

With such a transformation we can now do searches by value. e.g.

`["tags" "sutton"]` returns index `0` which we can then map to the first entity.
`["role" "admin"]` returns both `0` and `1` since both entities share the `"admin"` role.

So rather than a O(n) linear search we now have a O(1) two constant time hash map look-ups and one (or more) constant time index lookup in a vector.

Other things to note in the inverted index above are:

    * Normalized values
      - All the values have been converted to strings and lower cased.
        The search terms are also normalized in this way, allowing greater flexibility during search, searching is not case sensitive.

    * Analysers
      - Some values have been transformed into more than one value in the inverted index.
        This also gives greater search flexibility, search by first or last name for example.
        The process is controlled by *analysers* defined as regexes. e.g.
        `{"created_at" "\\d{4}-\\d{1,2}-\\d{1,2}", "name" "\\w+"}`

    * Collection values
      - For collection values such as `"tags"` above, each element in the collection is mapped seperately in the inverted index.
        This allows each value to be searched for individually.

This indexing process was designed so that it does not require knowledge of the data. Any list of entities can be indexed in this manner. The only piece of contextual information is the *analysers*, which are specified per field. The *analysers* are configured externally from the indexing code. This maintains a good separation of concerns between the indexing and the specifics of the data in the catalogues. New catalogues, and *analysers*, may be added to the search application through external configuration without needing changes to the indexing.

The inverted index also enables efficient introspection. We can easily find a list of all the fields available in a catalogue, simply the keys in the hash map. The search interface makes use of this, and like the index is agnostic to the catalogue data. New data may be add and the interface will continue to work and present the new options.

This indexing scheme lends it self well to resolving relationships between the data catalogues. Searches into the index can be written to join on specific keys and each join will result in one more lookup. This is quite efficient.

The empty may be searched for and they are handled but finding all the fields when building the index and if any entity is missing a field it is given one with an empty string in the index.

### Command line interface

The command line interface is implemented as a simple finite state machine. Each state specifies a `message` and a `response`. When the state is entered the message gets printed and user input expected, the input is then processed by the `response` and the next state is returned. The loop is as follows:

   * print `message`
   * capture input
   * run `response`
   * go to the next state returned be `response`

### Testing

As the inverted index is written as a simple transformation of the data using pure functions, with not contextual information about the data, it is very readily unit tested. There is good coverage of all the search capabilities with not mocking required.

It is still important to verify the basic behaviour with real data, and the integration with the loading up of the queries, and data, from configuration. Integration tests have been put in place to test this. Loading the default data and queries from configuration and checking for some know examples.

Integration tests are also provided for testing of the interface. They written to mock out just the user input and capture everything printed to standard out. Through this method we are able to play out scenarios to test and test that we receive the expected out. These types of tests have the tendency to be quite brittle, since any little change in wording can break them. For this reason the messages for each step are captured within functions in the `cli` namespace. The tests are written to match on the output of the message functions, allowing the exact wording to change but still check that the order of the messages is correct. They are still somewhat fragile tests but they read nicely and allow a developer to see at glance the scenario that is being played out so the trade-off is a good one.
