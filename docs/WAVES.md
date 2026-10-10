# Improvement waves

Work is ordered so each wave leaves the server able to generate a document. Later waves assume the helpers introduced in wave 1.

## Wave 1 — Correctness of one request

Status: **landed**.

Scope:

- A new map and a new document list per call (`RequestContext`). Documents produced inside one XML body accumulate. The next HTTP call does not see them.
- Template lookup honors a numeric revision and `LATEST` (`TemplateLocator`). `Name.ott` remains revision 1 when no `_revN` file exists.
- `etc/odiinst` host and port are the addresses the pool dials (`OdiinstParser`).
- This wave read v2 `template/@outputFormat` and v3 `output/format/@type`. Wave 2 rejected the v3 namespace. Wave 6 reads v2 `output/format` (`OutputFormats`). `template/@outputFormat` applies when `output` is absent. A v3 namespace is HTTP 400.
- A failed instruction fails the request (HTTP 422) after the office document is closed.
- One dead office connection is returned to the pool and the next connection is tried. Startup fails when zero connections bootstrap.
- Template names and merge inputs must stay inside their root (`SafePaths`).
- Post-process actions are `merge-with` and `merge-results` only.
- HTTP 503 when the failure is an `OdiseeServerException`. HTTP 404 when the template is missing.
- Spreadsheet document extension is `ods`.
- The legacy `OOoConnection` branch no longer references a missing variable.

Out of scope on purpose: authentication enforcement, schema validation, UNO deadlines, deleting the unused office stack, a real LibreOffice test.

Build:

- JDK 21, Gradle 8.14.5, Grails 7.2.4.
- UNO classes come from Maven Central (`org.libreoffice:libreoffice` and `org.libreoffice:unoloader` at 26.2.2, the newest 26.2 still jars published there). The older `juh`, `jurt`, `ridl`, and `unoil` coordinates are empty stubs at that version. `compileJava` does not download a LibreOffice `.deb`.
- `./gradlew build` compiles, runs the unit tests, and writes `build/distributions/odisee-2.6-linux-x86_64.zip`. GitHub Actions does the same on JDK 21 and publishes the zip on a `v*` tag.
- `./gradlew :webservice:test` does not start Docker. `./gradlew :webservice:libreOfficeTest` builds `webservice/src/test/docker/libreoffice` (Ubuntu 26.04, headless LibreOffice Writer) and runs tests 1–3.
- The service image is `./gradlew :webservice:buildOdiseeImage` (Ubuntu 26.04, OpenJDK 21, headless LibreOffice Writer). It is not part of `build`.
- `oxt/buildExtension` downloads ant-contrib and xmltask, then runs the Ant `world-production` target. Configuration of the webservice does not import that Ant build. Gradle is started with `--add-exports` for `java.xml/com.sun.org.apache.xpath.internal` and `java.xml/com.sun.org.apache.xpath.internal.objects` because xmltask 1.16 calls those JDK-internal XPath classes.

## Wave 2 — One request contract

Status: **landed**.

The schema is v2 (`webservice/src/main/resources/xml/v2/request.xsd`). v3 and v2.6 are HTTP 400. A document with no namespace is the same v2 shape. Wave 2 rejected `output/format`. Wave 6 added `output/format` to this same v2 schema. `template/@outputFormat` applies when `output` is absent. When `output/format/@type` is present, that type is the format. Instructions may appear in any order. The legacy `ooo` element is accepted and ignored.

- `RequestSchema` validates the body before generation. The parser rejects a `DOCTYPE` and does not read external entities (B8, B9).
- `Compression.readLimited` caps the raw body at 8 MiB and the expanded body at 32 MiB (B19).
- `HttpStatuses` is the only status decision: 400 for a bad request, 422 for an instruction the schema allowed, 404 for a missing template, 503 for the office pool. The controller does not close the stream after that error body is written (B20).
- The Java client is generated from that schema (`clients/client-java/src/main/schema/request.xsd`). The v3 and v2.6 request schemas are gone (F11).

## Wave 3 — Pool stays up

Status: **landed**.

- Deadline on UNO `open`, instruction, and `save` (`UnoCall`, default 120s, `odisee.uno.deadline.ms`). On deadline, close the document (2s, `odisee.uno.close.deadline.ms`), drop the slot, and stop the local `soffice` so `odiwatchdog` sees the port close and restarts it (B14). A dropped slot rejoins after `connect()` succeeds again. `/ready` pings an idle slot the same way and drops it when the ping does not return (`odisee.uno.recover.deadline.ms`, default 500ms).
- `GET /ready` is HTTP 200 only when at least one office port accepts a UNO connection, otherwise 503. The JSON body is the pool gauges: pool size, in-use count, last generation time, instruction failures, and `soffice` restarts (F2).
- `OOoConnection`, `OOoConnectionManager`, and `OOoProcess` are deleted, so the next edit cannot land in the unused stack (B26).
- `DeferredNode` stays gone (B27, toolchain update).

## Wave 4 — Tenancy

Status: **landed**.

- `POST /document/generate` requires a logged-in user. No login is HTTP 401. The hardcoded `odisee` principal is gone (B21, B22).
- Spring Security reads bcrypt hashes from `$ODISEE_HOME/etc/users`. Embedded Tomcat stays. `java -jar` does not read `odisee-users.xml`.
- The first admin is created only when that file is missing and both `ODISEE_BOOTSTRAP_USER` and `ODISEE_BOOTSTRAP_PASSWORD` are set. Otherwise there are no users.
- `odictl user NAME PASSWORD` writes a password-file line and creates `var/user/NAME/template`, `var/user/NAME/work`, and `var/user/NAME/output`. A bad name fails. An existing user fails. There is no default password. The person who runs `odictl` is the admin. `POST /user` is gone.
- Templates, merge inputs, and output resolve only under that user. There is no shared `var/template` fallback (F9).
- Any authenticated user may run macros. Macro name, library, and language must be plain names. A bad name is HTTP 400 and does not drop an office slot (B25).
- A missing or blank `ODISEE_HOME` is an `OdiseeException` (B28).
- `GET /ready` stays anonymous.

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

Status: **landed**.

The Java client proof is `OdiseeClientLocalTest`, task `:clients:client-java:javaClientOfficeTest`. It is not part of `:webservice:test` or `:clients:client-java:test`.

- The task starts the same LibreOffice container as tests 1–3, on port 2002, with `ODISEE_HOME` mounted at the same path.
- It starts the webservice against that home. `etc/users` has the caller. `Letter.ott` (user field `Hallo`) is in `var/user/odisee/template`.
- `OdiseeClient` posts to `http://127.0.0.1:<port>/odisee/document/generate` with the username and password constructor. The request is template `Letter`, output format PDF, and user field `Hallo`.
- The PDF text contains the value written.
- The same request with no credentials is HTTP 401, and that user's output directory does not gain a file.
- The client sends HTTP Basic on that request. It does not call `Authenticator.setDefault`.

The proof found two failures and fixed them. A logged-in call had a null `request.userPrincipal`, so generate returned 401 (B31). Saving the active DOM request cast it to `GPathResult` and failed before LibreOffice (B32).

Calc and Impress stay out of this wave. Wave 6 adds their instruction sets (F6). The remote tests in `OdiseeClientTest` stay `@Ignore`. They still point at `service3.odisee.de`.

## Wave 6 — Features

Status: **landed** for F1, F3, F4, F5, F6, F7, F8, and F10. F4, F3, and F10 landed in pull request 8. F1, F5, F6, and F8 landed in pull request 9 (`d8db005`). F7 stores into the one bucket configured for that user. The caller chooses stream, store, or both. The store is S3-compatible. MinIO is the development server. Odisee does not expire or delete objects.

F4. `TemplateLocator` reads `var/user/{name}/template/{templateName}/rev/{n}.ott`. `LATEST` is the highest number there or in the flat `Name.ott` / `Name_revN.ott` files. A missing revision is HTTP 404. One user cannot read another user's revisions.

F3. `GET /template/{name}` requires a login and lists that user's fields, bookmarks, tables, and revisions. Another user's template is HTTP 404. `POST /document/generate?dryRun=true` requires a login, resolves instructions, and writes no file under `output`. An instruction failure is HTTP 422 and does not drop a pool slot unless a UNO deadline fires. `GET /ready` stays anonymous. `POST /document/generate` stays synchronous and still returns the file bytes for `stream` and `both`.

F10. A changed `etc/odiinst` reloads the pool in this JVM. The 8th field is the group; a blank group is `group0`. The v2 `<group name="..."/>` value selects that group. A failed health check drops a remote host and does not signal a healthy local `soffice`. The probe keeps the wave 3 deadline.

F1. `POST /document/generate` stays synchronous and still returns the file bytes for `stream` and `both`. `POST /document/jobs` returns HTTP 202 and a job id. `GET /document/jobs/{id}` returns `status`, `failedInstruction`, and `file`. Jobs are private. The list is `var/user/{name}/jobs.json`. The caller is `callerFromContext`. `GET /ready` stays anonymous.

F8. The callback is a query parameter on `POST /document/jobs`, not on the synchronous POST. Odisee POSTs the status only when that host is on `$ODISEE_HOME/etc/callback-hosts`. `odictl callback-host HOST` appends a host. A URL off the list does not get a POST.

F5. v2 `output/format/@type` is the extension. `format/options/option` carries PDF version, tagged PDF, and a watermark. The namespace stays `http://xmlns.odisee.de/v2/request`. A v3 namespace is still HTTP 400. `template/@outputFormat` applies when `output` is absent. When `output/format/@type` is present, that type is the format. The `.pdfa` suffix remains the PDF/A fallback. The Java client schema is regenerated from the v2 file.

F6. One instruction set per application. Writer keeps `Userfield`, `Texttable`, `Image`, `Autotext`, `Bookmark`, and `Macro`. Calc starts with `cell` (sheet and coordinate). Impress starts with `shape` (name). A set can grow on its own. There is no shared `named` tag. A Calc save with no instructions is not this feature.

F7 is implemented. Each user has one bucket in `$ODISEE_HOME/etc/buckets`, read on each store. `odictl bucket USERNAME ENDPOINT REGION BUCKET ACCESSKEY SECRET` writes that file. The v2 field is `odisee/@delivery` (`stream`, `store`, or `both`; the default is `stream`). JSON uses `delivery`. A v3 namespace is still HTTP 400. Stream keeps the response body as the file, and `POST /document/generate` stays synchronous. Store writes to that user's bucket and the body is the bucket and the key. Both returns the file and stores a copy, identified in the `X-Odisee-Object` header. The request cannot name a bucket. The server chooses the key. Endpoint, region, bucket, access key, and secret come from that user's line. The store is S3-compatible. MinIO is the development server. Odisee does not expire, delete, or set a lifecycle on the object. Retention belongs to the bucket. `POST /document/jobs` uses the same choice, and `var/user/{name}/jobs.json` stores the bucket and the key. Jobs stay private to the user. A callback still goes only to a host on `$ODISEE_HOME/etc/callback-hosts`. `GET /ready` stays anonymous.

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

After, `OutputFormats.fromRequest` uses v2 `output/format/@type` when it is present. Otherwise it uses `template/@outputFormat`. A v3 namespace is HTTP 400. The `.pdfa` suffix remains the PDF/A fallback when the request did not set a PDF version.

### Dispatch

Before, a tag or an action type was capitalized and invoked.

```groovy
methodName = tagName[0].toUpperCase() + tagName[1..-1]
OdiseeXmlCategory."process${methodName}"(xComponent, arg, instr)
```

After, each application has its own instruction set. Writer keeps `Userfield`, `Texttable`, `Image`, `Autotext`, `Bookmark`, and `Macro`. Calc uses `Cell`. Impress uses `Shape`. Post-process types must be `merge-with` or `merge-results`. A tag from another application's set is an error, and an instruction error still closes the `XComponent`.

### Paths

Before, merge inputs were `Path.of(ODISEE_VAR, filename)`. An absolute filename replaced the root.

After, `SafePaths.resolveInside(root, relative)` normalizes and requires the candidate to stay under `root`. `SafePaths.requireSimpleName` is what template names and request names go through.

### Pool fetch

Before, a failed `connect()` put the connection back and returned null. The caller threw, and the other slots were never asked.

After, `fetchConnection` tries up to one slot per configured address, then throws `OdiseeServerException`. `initializeConnections` throws when the queue is empty, and the log placeholder is `{}`.
