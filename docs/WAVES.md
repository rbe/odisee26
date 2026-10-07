# Improvement waves

Work is ordered so each wave leaves the server able to generate a document. Later waves assume the helpers introduced in wave 1.

## Wave 1 — Correctness of one request

Status: **landed in this change**.

Scope:

- A new map and a new document list per call (`RequestContext`). Documents produced inside one XML body accumulate. The next HTTP call does not see them.
- Template lookup honors a numeric revision and `LATEST` (`TemplateLocator`). `Name.ott` remains revision 1 when no `_revN` file exists.
- `etc/odiinst` host and port are the addresses the pool dials (`OdiinstParser`).
- v2 `template/@outputFormat` and v3 `output/format/@type` both select the file extension (`OutputFormats`).
- A failed instruction fails the request (HTTP 422) after the office document is closed.
- One dead office connection is returned to the pool and the next connection is tried. Startup fails when zero connections bootstrap.
- Template names and merge inputs must stay inside their root (`SafePaths`).
- Post-process actions are `merge-with` and `merge-results` only.
- HTTP 503 when the failure is an `OdiseeServerException`. HTTP 404 when the template is missing.
- Spreadsheet document extension is `ods`.
- The legacy `OOoConnection` branch no longer references a missing variable.

Out of scope on purpose: authentication enforcement, schema validation, UNO deadlines, deleting the unused office stack, a real LibreOffice test.

Build, so this wave can be compiled here:

- UNO classes come from Maven Central (`org.libreoffice:juh`, `jurt`, `ridl`, `unoil`, `unoloader` at 6.3.3). `compileJava` no longer downloads a LibreOffice `.deb`. The unpack task is still there for an offline deb.
- The extension project skips its Ant import when `oxt/antlib` has no jars, so a missing ant-contrib does not stop the webservice from configuring. Building the extension still asks for those jars.

## Wave 2 — One request contract

- Choose v2 or v3 as the schema the server validates, and reject the other with 400.
- Disable external entities. Cap the compressed and the decompressed body (B9, B19).
- Map the remaining client errors to 400 or 422 in one place. Stop closing the response twice (B20).
- Publish the chosen schema to `clients/client-java` and delete the unused XSD copies (F11).

## Wave 3 — Pool stays up

- Deadline on UNO `open`, instruction, and `save`. On deadline, close the document, drop the slot, and let `odiwatchdog` restart `soffice` (B14).
- `/ready` and pool gauges (F2).
- Delete or quarantine `OOoConnection`, `OOoConnectionManager`, and `OOoProcess` so the next edit cannot land in the unused stack (B26).
- Stop using `com.sun.org.apache.xerces.internal.dom.DeferredNode` (B27).

## Wave 4 — Tenancy

- Require a principal. Resolve templates only under that user's directory (B21, B22, F9).
- Macro execution is off unless the principal has that role (B25).
- Fix `OdiseePath` so a missing `ODISEE_HOME` is an `OdiseeException` (B28).

## Wave 5 — Prove a document

Tests 1–5 from the basis list:

| Test | Where | Needs LibreOffice |
|---|---|---|
| 1 Two calls do not share documents | `GenerationBasisTest` | yes |
| 2 Two requests stay in order, then merge | `GenerationBasisTest` | yes |
| 3 A bad instruction does not save, and the next request still runs | `GenerationBasisTest` | yes |
| 4 Status 404 / 400 / 503, body is the message | `HttpStatusTest` | no |
| 5 The pool skips a dead slot | `OfficeConnectionPoolTest` | no |

Tests 1–3 use a Writer template with the user field `Hallo` and read the text back out of the PDF. LibreOffice runs in Docker, headless, on port 2002. The test home is mounted into the container at the same path, so the file URL the server sends is the file LibreOffice opens.

```
./gradlew :webservice:libreOfficeTest
```

That builds `webservice/src/test/docker/libreoffice`, starts the container, and runs tests 1–3. `./gradlew :webservice:test` runs tests 4 and 5 with the other unit tests and does not start Docker.

Still open: the same request through the Java client, and Calc/Impress.

## Wave 6 — Features

F1, F3, F4 (directory layout), F5, F6, F7, F8, F10. Each one ships behind the contract from wave 2 and the pool from wave 3.

## Refactorings

The request path was a singleton map, stringly-typed method names, and two copies of "where is the template". Wave 1 pulls the pure decisions into small types that unit tests can call without LibreOffice and without `ODISEE_HOME`.

### Per-request state

Before, every call cloned one template map. The lists inside it were shared, and the clone also dropped documents that belonged to the same XML body.

```groovy
private final Map<String, Object> emptyArg = [
        document: [],
        result: []
]
Map<String, Object> arg = (Map<String, Object>) emptyArg.clone()

private static void resetRequest(final Map arg) {
    [..., 'document', ...].each { arg.remove(it) }
}
```

After, `RequestContext.create()` builds a new map. `resetForNextRequest` clears template fields and keeps `document`.

```groovy
Map<String, Object> arg = RequestContext.create()
if (i > 0) RequestContext.resetForNextRequest(arg)
```

`deepcopy` is gone. It was unused, and its streams were fields of the singleton.

### Template files

Before, the service overwrote the revision and probed two filenames.

```groovy
arg.revision = 1
Path localTemplate = arg.templateDir.resolve("${arg.template}.ott")
if (!Files.exists(localTemplate)) {
    localTemplate = arg.templateDir.resolve("${arg.template}_rev${arg.revision}.ott")
}
```

After, `TemplateLocator.locate(dir, name, revision)` rejects a name that is not a single path segment, picks the highest `_revN` for `LATEST`, and uses `Name.ott` only as revision 1.

### Office addresses

Before, the factory was always `2001 + index` on the first host.

```groovy
final String localhost = odiinst[0][1]
final int portbase = 2001
officeConnectionFactory = OfficeConnectionFactory.getInstance(group, localhost, portbase, odiinst.size())
```

After, `OdiinstParser` reads `name|host|port|...` and the existing `getInstance(group, List<InetSocketAddress>)` is the one `RequestService` calls.

### Output format

Before, only the v2 attribute was read, and an empty GPath still produced a blank token.

```groovy
template.'@outputFormat'?.toString()?.split(',')?.each { format ->
    output << outputDir.resolve("${documentBasename}.${format}")
}
```

After, `OutputFormats.fromRequest` returns the attribute when it is non-blank, otherwise each v3 `output/format/@type`.

### Dispatch

Before, a tag or an action type was capitalized and invoked.

```groovy
methodName = tagName[0].toUpperCase() + tagName[1..-1]
OdiseeXmlCategory."process${methodName}"(xComponent, arg, instr)
```

After, instruction names must be in `Userfield`, `Texttable`, `Image`, `Autotext`, `Bookmark`, `Macro`. Post-process types must be `merge-with` or `merge-results`. Anything else is an error, and an instruction error still closes the `XComponent`.

### Paths

Before, merge inputs were `Path.of(ODISEE_VAR, filename)`. An absolute filename replaced the root.

After, `SafePaths.resolveInside(root, relative)` normalizes and requires the candidate to stay under `root`. `SafePaths.requireSimpleName` is what template names and request names go through.

### Pool fetch

Before, a failed `connect()` put the connection back and returned null. The caller threw, and the other slots were never asked.

After, `fetchConnection` tries up to one slot per configured address, then throws `OdiseeServerException`. `initializeConnections` throws when the queue is empty, and the log placeholder is `{}`.
