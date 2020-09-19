# Version History

[TOC]: # " "

- [TODO](#todo)
    - [High Priority](#high-priority)
- [0.5.0 API Breaking Release](#050-api-breaking-release)
- [0.5.0-beta-8](#050-beta-8)
- [0.5.0-beta-7](#050-beta-7)
- [0.5.0-beta-6](#050-beta-6)
- [0.5.0-beta-5](#050-beta-5)
- [0.5.0-beta-4](#050-beta-4)
- [0.5.0-beta-3](#050-beta-3)
- [0.5.0-beta-2](#050-beta-2)
- [0.5.0-beta-1](#050-beta-1)
- [0.4.10](#0410)
- [0.4.8](#048)
- [0.4.6](#046)
- [0.4.4](#044)
- [0.4.2](#042)
- [0.4.0](#040)
- [0.3.4](#034)
- [0.3.2](#032)
- [0.3.0](#030)
- [0.2.26](#0226)
- [0.2.24](#0224)
- [0.2.22](#0222)
- [0.2.20](#0220)
- [0.2.18](#0218)
- [0.2.16](#0216)
- [0.2.14](#0214)
- [0.2.12](#0212)
- [0.2.10](#0210)
- [0.2.8](#028)
- [0.2.6](#026)
- [0.2.4](#024)
- [0.2.2](#022)
- [0.2.0](#020)
- [0.1.8](#018)
- [0.1.6](#016)
- [0.1.4](#014)
- [0.1.2](#012)
- [0.1.0](#010)


## TODO

* [ ] Add: tests for all type conversions
  * [ ] Result set
  * [ ] Json
  * [ ] Kotlin types
* [ ] Add case sensitive database entity aware migration
  * [ ] Tables
  * [ ] Others?

### High Priority

* [ ] Test: query generating functions
  * [ ] validate quote parameter is used for table and column names
  * [ ] alias parameter
    * [ ] `""`
    * [ ] same as table name
    * [ ] other string `"a"`
* [ ] Test: list generation functions
* [ ] Fix: Readme docs, consider creating a wiki

## 0.5.0 API Breaking Release

:information_source: This branch is a rework with breaking changes to refactor models and
related classes to eliminate having to specify identifier quoting in the model by creating the
model for a database session. Which makes sense for a database model class.

Biggest change is that the model now takes two template arguments: main model class and its
associated data class with an optional session instance and identifier quoting string.

If `session` is not given or `null` then default session will be used.

If `quote` if not given or `null` then the connection `metaData.identifierQuoteString` will be
used, anything else will use whatever string is passed in. Unless your jdbc driver does not
provide identifier quoting, there is no need to use anything but the default.

Companion object now only has the table name constant string.

All other functions implemented in the `Model` with two abstract members: `toData()` returning
the data class for the model and `operator invoke` for the factory function for the model. To
get another instance of a model `myModel` invoke the model instance as a function `myModel()`.

For models that do not need a data class `ModelNoData` is also available which only takes a
single template argument, as was the case for the `Model` class in previous releases.

Having the session instance information in the model simplifies using models because session no
longer has to be specified for every method performing database access.

Additionally, list results are simplified because neither the session nor the extractor needs to
be passed, with `myModel.listData()` variations can be used or `myModel.listModel()` variations.

Additionally there is now an `alias:String? = null` argument available for sql generating
functions which will add a table alias to the table name and use the alias for disambiguating
column names. If generating queries with multiple tables, set the `alias` to empty string `""`
or the table name to have it added to the column references. An empty table alias or one equal
to the table name will only be used for column references.

[`Generate Kotlin-Model.groovy`] has been updated to generate the new model format from tables
in the database.

## 0.5.0-beta-8

* Fix: `SqlCall` out params are now passed by index instead of name to make them independent of
  actual sql procedure parameter names.
* Fix: `param()` to work for non-null values when calling context's capture type is `*`,
  previous implementation would always result in `Any` type, fixed implementation uses the
  value's class for type's class.
* Fix: `param()` to not construct a new instance if passed value is already `Parameter<*>`
* Fix: SQL generation for model `listQuery` with where clause and/or alias
* Add: `InOut` type to `Parameter()`, defaults to `InOut.IN` so parameter carries direction of
  parameter and type.
* Add: `inTo`, `outTo` and `inOutTo` infix functions to use instead of `to` for param creation.
  These create parameters with in/inOut/out type eliminating the need for multiple `params()`
  functions for directional information. Default direction is `in` if not provided.

  They also create an instance of `Parameter` when the captured type is known. Functions using
  `param()` from a collection iteration have `*` for type capture and loose actual type,
  resorting to `Any` (ie. `Object`).

  These also have `Collection<T>` versions which create a Parameter() with a collection value
  but type is `T::class.java`. When generating parameters for prepared stmt this type is used to
  determine sql type for the element of the collection instead of generic `Any` as was before,
  again because of `*` captured type from a collection.
* Fix: extract `SqlQueryBase<T>` for `SqlQuery` and `SqlCall` to eliminate the need to override
  function just to cast them to the right class. `SqlCall` no longer extends `SqlQuery`.
* Fix: `SqlQueryBase` now stores each parameter in `Parameter()` data class format. Eliminates
  creating a new instance with param() since if it is already parameter it just returns the
  instance.
* Fix: deprecate old methods in favor of directional parameter declaration.
* Fix: change `Session` methods to use `SqlQueryBase<*>` when either `SqlQuery` or `SqlCall` can
  be used. For some, like updates with get keys, `SqlCall` never returns any keys even when
  stored procedure executes DML that does, so these are now `SqlQuery` only. Similarly, those
  only applicable to `SqlCall` are now `SqlCall` typed and will not take `SqlQuery`
* Fix: deprecate `Session.forEach(SqlCall, (stmt: CallableStatement) -> Unit, (rs: ResultSet,
  index: Int) -> Unit)`
* Add: `Session.executeCall(SqlCall, (results: SqlCallResults) -> Unit)` to replace
  `Session.forEach(SqlCall, (stmt: CallableStatement) -> Unit, (rs: ResultSet, index: Int) ->
  Unit)`. Use `SqlCallResults.forEach` to process result sets returned by the procedure call. To
  get values of out params use `SqlCallResults.get{Type}(paramName)` to get non-null values or
  `SqlCallResults.get{Type}OrNull(paramName)` to get nullable values.

  For example, when using old `forEach` the code was:

  ```kotlin
  val sqlQuery = sqlCall("""CALL processInstances(:clientId, :types)""")
    .inParams("clientId" to 35)
    .inOutParams("types" to "")

  val jsonResult = MutableJsObject()
  var types: List<String> = listOf()

  session.forEach(sqlQuery, { stmt ->
      types = stmt.getString("types").split(',')
  }) { rs, index ->
      val key = types[index]
      jsonResult[key] = MutableJsArray(Rows(rs).map(toJsonObject).toList())
  }
  ```

  Using the new `executeCall` and directional param declarations, the code changes to:

  ```kotlin
  val sqlQuery = sqlCall("""CALL processInstances(:clientId, :types)""",
            mapOf("clientId" inTo 35, "types" inOutTo ""))

  val jsonResult = MutableJsObject()

  session.executeCall(sqlQuery) { results:SqlCallResults ->
      val types = results.getString("types").split(',')
      results.forEach { rs, index ->
          val key = types[index]
          jsonResult[key] = MutableJsArray(Rows(rs).map(toJsonObject).toList())
      }
  }
  ```

## 0.5.0-beta-7

* Add: import evolutions to also accept `# -- !Ups` and `# -- !Downs` as valid Scala play
  evolution markers
* Fix: merge fixes made by [Nick Johnson](https://github.com/Sylvyrfysh) in master

## 0.5.0-beta-6

* Break: Add profile name after `db/` to allow multi-database migrations.
  * :warning: To migrate previous `db/` structure move all directories other than `templates` of
    `db/` to `db/default`
  * default profile is in `default` directory
  * templates remain under `db/templates` and will apply to all profiles
  * new `Migrations` constructor takes maps for session arguments indexed by profile string
  * old `Migrations` constructor now uses `default` for profile name
  * `profile` command line option specifies which profile is to be used. Some commands like
    `rollback` require an explicit profile name, others will use `default` when one is not
    given, some like `init` and `migrate` will apply the command over all profiles

## 0.5.0-beta-5

* Fix: Rollback used string compare instead of version compare, causing 0_7_2 to match 0_7_20
  rollback.

## 0.5.0-beta-4

* Fix: [`JavaScript-Enumerated-Value-Type.js`] for latest version of [`enumerated-type`] with
  objects for values and `dropdownChoices` property of `{ value: xxx, label: "yyy", }`
  automatically generated from the enum id column and enum type column.

## 0.5.0-beta-3

* Fix: [`JavaScript-Enumerated-Value-Type.js`] for latest version of [`enumerated-type`] with
  object value containing the id and type instead of constants.
* Add: option to Scala model script to generate a separate Database model and an Api Model used
  for REST api data exchange.
* Fix: `Generate Kotlin-Model.groovy` now will look for the model map file starting from the
  directory selected for generation and go up, until encountering directory with sub-directory
  `.idea`, hitting the root directory or finding `model-config.json` file. Mappings in this file
  are now relative to the directory of the `model-config.json` file.

  if `.idea` sub-directory was seen then will assume that this is the project root and if no
  `model-config.json` file is provided then will use the destination directory, without the
  project directory prefix and one more directory removed to generate the package for generated
  files. If the first sub-directory is marked as `sources root` then the package will be
  correct. Otherwise create a `model-config.json`.

  Intended use case is to place the `model-config.json` at the project root and map all files in
  it with path relative to project root. Generating models then is not dependent on which
  directory in the project is selected as the directory for generating models.
* Fix: `Generate Scala-Slick-Model.groovy` to work like `Generate Kotlin-Model.groovy`
* Fix: make `Generate Kotlin-Model.groovy` and `Generate Scala-Slick-Model.groovy` more generic
  with the differences isolated to the last generate method.
* Fix: model generation scripts to get nullability and default value for column from database
  information. No longer kludging based on column names.
* Add: `Scala-Object-Enum.kt.js` to generate an `object` with fields mimicking enum values,
  needs work to make real Scala enums.
* Add: `import-evolutions path min {max}` where path is to the directory holding evolutions and
  `min` is the minimum evolution to import. `max` is optional and if provided gives the maximum
  evolution number to import.
* Fix: `new-evolution` to put down migration script right after the up migration script instead
  of all up migrations followed by reversed down migrations.

## 0.5.0-beta-2

* Add: `jsonArray` functions to `Model` to list query results directly to `JsonArray` of
  `JsonObject`.

  :warning: will convert all columns of the query to properties of the `JsonObject` without
  filtering by model properties.

* Add: `listData` and `listModel` variants with parameter list and no where clause to have the
  where clause generated from passed parameters.

* Add: optional `model-config.json` to be used by Kotlin model generating script to control
  location and name of generated models.

## 0.5.0-beta-1

* Fix: API Change

## 0.4.10

* Fix: merge #7, isLast and isAfterLast checking removed for TYPE_FORWARD_ONLY ResultSet thanks
  to [@s5r3](https://github.com/s5r3)

## 0.4.8

* Fix: migrate was using string compare for current version and versions for finding later ones
* Fix: parameter extraction should take quoted, double quoted and back-quoted strings into
  account. Otherwise a string with `:\w+` will be treated as a parameter.

## 0.4.6

* Fix: migrate command was string sorting versions instead of using versionCompare()

## 0.4.4

* Add: multiple list parameter handling, PR by **[root-talis](https://github.com/root-talis)**

## 0.4.2

* Fix: factor out `Session` and `Transaction` functions into interfaces. Implementation renamed
  to `SessionImpl` and `TransactionImpl`

## 0.4.0

* Fix: remove database dependent quoting of table name and column names and replace it with an
  optional `quote` argument to `ModelProperties` and `Model` constructor argument with default
  from `ModelProperties.databaseQuoting` which can be set to any desired string. If the same
  database type is used on all connections then quoting should be set during initialization
  through `Model.databaseQuoting`.

  However, if multiple databases with different quoting requirements are used then `quote`
  argument will need to be passed to model constructor based on the database connection.

  * companion functions requiring quoting now take an optional argument `quote` with default of
    `ModelProperties.databaseQuoting`
  * model companion abstract `createModel()` now has `createModel(quote: String?)` signature so
    it can pass `quote` argument to model constructor.

* Fix: list query with where clause parameters to use name parameters instead of plain
  parameters since parameters are passed as a map.

* Fix: list queries with where clause parameters having collection values to use `IN (:name)`
  instead of `name = :name` for condition

* Add: automatic expansion of `sqlQuery` and `sqlCall` collection valued parameters into their
  contained values. This allows lists to be used where individual parameters are expected:

```kotlin
sqlQuery("SELECT * FROM Table WHERE column in (:list)").inParams("list" to listOf(1,2,3))
```

To expand to `SELECT * FROM Table WHERE column in (?,?,?)` with parameters of `1, 2, 3`.

* Change: refactor `sqlQuery` to substitute named parameters and compute named parameter mapping
  as needed and after any parameter changes. Required to have access to the parameter's type to
  figure out if it is a list or a single value.

## 0.3.4

* Add: variations of `list` and `listJson` to `ModelCompanion` taking where clause and vararg
  `Pair` or map argument for parameters for the where clause for more flexibility of generating
  list queries.

## 0.3.2

* Add: `Model.appendWhereClause()` to simplify creating list queries

## 0.3.0

* Change: from `by model` to `by db` for model property definitions
* Add: `Model.listQuery` companion methods to list model instances with or without conditions
* Add: `ModelCompanion` to make companion methods of real models simpler to update without code
  editing
* Add: separate session for updating migrations table to keep separate from schema modification
  session.
* Fix: add trimming of table sql string for comparing by line re-ordering before splitting into
  lines.
* Fix: JS enum type script for better WebStorm code completion functionality

## 0.2.26

* Add: copying any extra files under `/db/template` resources directory to new version directory
  on creation. Intended for things like change notes or other common file templates.
* Fix: `model`: `auto`, `key`, `default`, `autoKey` combinations
* Add: `model.default(Any?)` default property type definition with a provided default value.
  Will set properties not provided or if their value is null on insert to this value
* Add: insert and update queries to skip passing `null` value parameters which have defaults.
  `null` effectively means the same thing as not setting the parameter, using the db defined
  default instead for insert and no-op for update.
* Add: update query which has no non-null non-default modified parameters will update key column
  to itself to trigger auto updated fields.
* Add: migration verbose `-v` and detailed `-d` flags for table validation
* Add: ignoring named param pattern in query lines that are commented out with `-- ` or `#` as
  first non-blank of the line. Otherwise too many parameters will be passed for the query since
  SQL will not see the named param uses or comments in sql might me interpreted as parameter
  names.
* Add: IntelliJ Ultimate Database Tools data extraction script to generate JavaScript enum from
  table data. See:
* Add: IntelliJ Ultimate Database Tools data extraction script to generate Kotlin enum from
  table data. See:
  [Installing IntelliJ Ultimate Database Tools Extension Scripts](README.md#installing-database-tools-extension-scripts)
* Fix: table validation for out of order lines to ignore trailing , otherwise the last line out
  of order does not match.
* Fix: init command when run on a database without `migrations` table will use db versions table
  schema to determine latest matched version as the current version
* Add: validation for table names in table sql to match file name when doing init.
* Fix: table validation to properly detect when resource is missing
* Add: migrations to play evolutions conversion command: `new-evolution
  "play/evolutions/directory"` which will convert the current or requested version migrations
  and rollbacks to the next play evolutions file in the requested directory.

## 0.2.24

* Fix: model generating script to convert table name to singular form for use as the model class
  name.
* Add: `new-...` commands for: function, procedure, trigger and view.
* Add: built in templates for creating new entities with placeholders `__VERSION__` replaced by
  the version for which they are created and `__TITLE__` for migration/rollback or `__NAME__`
  for all others to reflect the parameter passed to the command

## 0.2.22

* Change: `usingSession` to `usingDefault` as a short form of `using(session())`

## 0.2.20

* Add: `Session.defaultDataSource` lambda to generate default data source for use with
  `session()` version of the function to reduce default session arguments

## 0.2.18

* Fix: commit before doing `System.exit(1)` for exit command

## 0.2.16

* Fix: migrations rollback filter condition reversed for multiple version rollback

## 0.2.14

* Add: migrations `update-schema` command to copy all current version entities to the `schema`
  version directory to allow VCS version to version change tracking.

## 0.2.12

* Fix: npe when trying to access non existent resource

## 0.2.10

* Add: migration convenience commands
  * new-major - create a new version directory with major version incremented.

  * new-minor - create a new version directory with minor version incremented.

  * new-patch - create a new version directory with patch version incremented.

  * new-version - create a new version directory with minor version incremented. The directory
    cannot already exist. If the version is not provided then the next version with its minor
    version number incremented will be used.

    All entity directories will be created, including migrations.

    If there is a previous version to the one requested then its entity scripts will be copied
    to the new version directory, including tables which are used for validation after
    rollback/migration

  * new-migration "title" - create a new up/down migration script files in the requested (or
    current) version's migrations directory. The file name will be in the form: N.title.D.sql
    where N is numeric integer 1..., D is up or down and title is the title passed command.

## 0.2.8

* Add: `Model(val sqlTable: String, dbCase: Boolean, allowSetAuto: Boolean = true)`, `dbCase` to
  mark all properties without specific column name as having database case. When false, database
  columns are assumed to be (snake case) versions of the property names. Available on all
  property providers: `auto`, `key`, `default`

* Add: `model.column(String)` to provide arbitrary column name for the property. Available on
  all property providers: `auto`, `key`, `default`

## 0.2.6

* Add: migration table compare will match if differences are out of order lines between two
  tables. This addresses validation failure for tables which are changed due to rollback causing
  keys and constraints to be in a different order.

## 0.2.4

* Add: `Model.toJson()` method for converting a model to `JsonObject`

## 0.2.2

* Fix: migration command to not apply versions higher than requested
* Add: IntelliJ Database script extension to create Models from database tables
* Fix: Apply all migrations that exist from start. First version under migrations should have
  initial database creation without migrations.
* Change: refactor code to clean up duplication
* Add: `Model.appendKeys()` to append model's keys for query generation
* Fix: wrong conversion for jodaLocalDateTime and a few others. Added casts to catch these
  errors during compilation
* Fix: add snapshot to `Model.update(session:Session)` after successful update.

## 0.2.0

* Add: abstract `Model` class for creating models to reduce boiler plate code.

## 0.1.8

* Fix: wrong filter on get entity resource files list
* Change: change `updateGetId` and `updateGetIds` to return `Int` and `List<Int>` respectively
* Add: add `updateGetLongId` and `updateGetLongIds` to return `Long` and `List<Long>`
  respectively

## 0.1.6

* Fix: iteration over empty result set caused exception, due to insufficient conditions present
  in result set to know there is no next row until next() is invoked.
* Add: rudimentary SQL based migrations with migrate/rollback with up/down sql scripts, table,
  procedure, function, view and trigger script running. Migrations are for data and tables. The
  rest of the scripts are applied for a given version. Needs docs and tests.

## 0.1.4

* Fix: pom packaging to jar

## 0.1.2

* Fix: pom missing info
* Fix: pom and travis errors
* Fix: pom for dokka plugin javadoc generation

## 0.1.0

* Initial release

[`enumerated-type`]: https://github.com/vsch/enumerated-type
[`Generate Kotlin-Model.groovy`]: extensions/com.intellij.database/schema/Generate%20Kotlin-Model.groovy
[`JavaScript-Enumerated-Value-Type.js`]: extensions/com.intellij.database/data/extractors/JavaScript-Enumerated-Value-Type.js

