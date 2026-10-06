# Odisee bugs

Findings from a code review of the document-generation path. Status is against `master` plus the wave that closed the item.

Severity: **blocker** (wrong document, leak, or the service cannot do what it claims), **major** (failures are silent or one bad instance takes the pool down), **minor** (wrong metadata, dead code, misleading errors).

## Request state

| ID | Severity | Status | Bug |
|---|---|---|---|
| B1 | blocker | fixed in wave 1 | `OdiseeService` is a singleton and cloned `emptyArg` with `Map.clone()`. The `document` list was shared, so every request appended to one list kept for the life of the JVM. Merge post-processing could include another caller's PDFs. |
| B2 | blocker | fixed in wave 1 | `resetRequest` deleted `document` at the start of request 2..N inside one XML body. A multi-request merge only saw the last request. |
| B3 | minor | fixed in wave 1 | `OdiseeService.deepcopy` assigned `bos`, `oos`, `bin`, and `ois` without `def`, so they became fields on the singleton. The method was unused. |

## Templates and output

| ID | Severity | Status | Bug |
|---|---|---|---|
| B4 | blocker | fixed in wave 1 | `TemplateService` rewrote a missing or `LATEST` revision to `1`, then assigned `arg.revision = 1` again. `Contract_rev3.ott` was reported as missing. `LATEST` did not pick the highest revision. |
| B5 | major | fixed in wave 1 | `OdiseeXmlCategory.findLatestRevision` called `Path.listFiles()`, which does not exist, then indexed `[0]` on an empty directory. Revision strings were compared lexicographically (`rev9` > `rev10`). `findTemplate` also dereferenced a null path when revision was `LATEST`. |
| B6 | major | fixed in wave 1 | The runtime only read `template/@outputFormat` (v2). A v3 request that uses `output/format/@type` produced no file and failed with "Got zero bytes from office process". |
| B7 | minor | fixed in wave 1 | `OfficeDocumentType.SPREADSHEET` sets the document extension to `ots` (the template extension). It is `ods`. |
| B8 | major | open | XML is not validated. The schema check in `readRequest` is commented out. v2, v2.6, and v3 schemas still ship together. |
| B9 | major | open | `DOMBuilder.parse` and `XmlSlurper` are used with parser defaults. External entities are not explicitly disabled. |

## Office pool

| ID | Severity | Status | Bug |
|---|---|---|---|
| B10 | blocker | fixed in wave 1 | `RequestService` connected to `host-of-line-1` and ports `2001..2001+n`. Ports in `etc/odiinst` were ignored. The sample file uses 2001 and 2002, so a default install hid the bug. |
| B11 | major | fixed in wave 1 | `OdiseeInstance.readOdiinst` replaced `oooGroup['group0']` on every host, so only the last host remained. Blank lines were parsed as rows. |
| B12 | major | fixed in wave 1 | `OfficeConnectionFactory.fetchConnection` returned null after a connect failure. `toDocument` then failed even when another instance in the pool was healthy. |
| B13 | major | fixed in wave 1 | If every bootstrap failed, the factory still started with an empty queue. The SLF4J message used `%{}` instead of `{}`. |
| B14 | major | open | UNO calls have no deadline. A live but wedged `soffice` holds a pool slot until the JVM exits. `odiwatchdog` only helps after the process has exited. |
| B15 | major | fixed in wave 1 | `processTemplate` called `xComponent.close()` only on the success path. A failure in `open` or `saveAs` left the document open inside LibreOffice. |
| B16 | minor | fixed in wave 1 | `OOoConnection.connect` referenced `diff` in a branch that Groovy compiles but cannot run (`MissingPropertyException`). `isUsable()` already throws before that branch. |

## Failures the client cannot see

| ID | Severity | Status | Bug |
|---|---|---|---|
| B17 | major | fixed in wave 1 | A failed instruction was logged and the document was still saved and returned as HTTP 200. |
| B18 | major | fixed in wave 1 | Every error, including "office unreachable", was HTTP 400. |
| B19 | major | open | `Compression.decompress` used to ignore a short read. Wave 1 fixes the short read. There is still no cap on the decompressed body, so a gzip bomb is limited only by the heap. |
| B20 | minor | open | `DocumentController` closes `response.outputStream` in `finally` even after `processThrowable` already wrote the error body. |

## Security and tenancy

| ID | Severity | Status | Bug |
|---|---|---|---|
| B21 | blocker | open | The Grails app has no authentication. `java -jar` (the Docker entrypoint) does not load Tomcat's `odisee-users.xml`. The endpoint runs StarBasic macros named by the client. |
| B22 | major | partial in wave 1 | `DocumentController` ignored `request.userPrincipal`. Wave 1 uses the container principal when one exists, and still falls back to a hardcoded user named `odisee` when it does not. |
| B23 | major | fixed in wave 1 | Template names were joined onto the template directory with `Path.resolve`. A name containing `..` or a separator could escape that directory. Merge `input/@filename` accepted an absolute path because `Path.resolve` replaces the base. A client-supplied `outputPath` is overwritten with the request directory. |
| B24 | major | fixed in wave 1 | Post-process `action/@type` was turned into a method name (`process${Type}`). An unexpected type failed at runtime or could hit an existing method. Instruction tag names were dispatched the same way. |
| B25 | major | open | Request `@name` is used as a filename. Wave 1 rejects separators and `..`. A full allow-list (and the same check on macro URLs) is still open. |

## Structure

| ID | Severity | Status | Bug |
|---|---|---|---|
| B26 | major | open | Two office stacks are compiled: `OfficeConnection` / `OfficeConnectionFactory` (used) and `OOoConnection` / `OOoConnectionManager` / `OOoProcess` (not used by the request path). |
| B27 | major | open | The service depends on `com.sun.org.apache.xerces.internal.dom.DeferredNode` (`RequestService`). That package is encapsulated on current JDKs. |
| B28 | minor | open | `OdiseePath` calls `Path.of(System.getProperty("ODISEE_HOME"))` when the environment variable is unset. A missing property throws `NullPointerException` instead of `OdiseeException`. |
| B29 | minor | open | `SPREADSHEET` is the only Calc hint, and the instruction set is Writer-only. Calc/Impress are listed in `OdiseeFileFormat` and then stop. |
| B30 | major | open | Automated coverage is `CoordinateTestCase` and `OfficeProcessTest`. Wave 1 adds unit tests for the helpers below. The generation path still has no test that opens a real template. |

## Tests added in wave 1

- `TemplateLocatorTest` — `LATEST` is numeric, revision 1 falls back to `Name.ott`, path escape is rejected.
- `OdiinstParserTest` — host and port come from the file, comments and blank lines are skipped.
- `OutputFormatsTest` — v2 attribute and v3 `output/format` both resolve.
- `SafePathsTest` — relative merge paths stay under the root; absolute paths and `..` do not.
- `RequestContextTest` — each call gets its own document list; resetting for the next request keeps documents already produced.
- `CompressionTest` — gzip round-trip and a one-byte body.
- `PostProcessServiceTest` — unknown actions and merge paths that leave the data directory are rejected.
