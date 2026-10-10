# Odisee feature ideas

These are product additions, not bugfixes. They assume the request contract and the office pool from `docs/WAVES.md` are trustworthy. F2 shipped with wave 3. F1, F3, F4, F5, F6, F8, and F10 shipped with wave 6. F9 shipped with wave 4. F11 landed with wave 2. F7 is not implemented yet.

## Generation API

### F1. Job API

Status: **landed in wave 6**.

`POST /document/generate` stays synchronous. The response body stays the file bytes. `POST /document/jobs` returns HTTP 202 and a JSON job id. `GET /document/jobs/{id}` returns JSON: `status`, `failedInstruction`, and `file` (base64, or null until the job has a file). The caller is `callerFromContext`, the same source as generate. No login is HTTP 401. A job id that is not in that user's list is HTTP 404.

The list is `var/user/{name}/jobs.json`. One file per user. It is not process memory and not a shared table. The generated file stays under that user's `output` directory. Object storage is F7 and is not this change.

Depends on: wave 2 status codes, wave 3 pool deadlines.

### F2. Readiness and metrics

Status: **landed in wave 3**.

`GET /ready` (context path `/odisee`) is anonymous. It is HTTP 200 only when at least one office port accepts a UNO connection, otherwise 503. The JSON body exports pool size, in-use count, the last generation time, instruction failures, and `soffice` restarts. The watchdog becomes something an operator can see.

### F3. Template catalog and dry run

Status: **landed in wave 6**.

`GET /template/{name}` (context path `/odisee`) requires a login. The body is JSON: `name`, `revision`, `userFields`, `bookmarks`, `tables`, and `revisions`. Those come from that user's `var/user/{name}/template` only. Another user's template is HTTP 404. No login is HTTP 401. The caller is the security context (`callerFromContext`), the same source as `POST /document/generate`.

`POST /document/generate?dryRun=true` requires a login. It opens the template, applies the instructions, and does not save a file under `output`. A failed instruction is HTTP 422 and does not drop a pool slot. A UNO deadline still drops the slot. The synchronous `POST /document/generate` response stays the file bytes.

### F4. Working template revisions

Status: **landed in wave 6**.

Revisions live at `var/user/{name}/template/{templateName}/rev/{n}.ott`. `LATEST` is the highest number across that directory and the flat files. A revision that is not on disk is HTTP 404. `Name.ott` and `Name_revN.ott` in the user's flat template directory still resolve. User A cannot see user B's revisions. There is no shared `var/template` directory.

## Documents

### F5. Output options that are not a filename suffix

Status: **landed in wave 6**.

The v2 request (`http://xmlns.odisee.de/v2/request`) includes `output/format`. `format/@type` is the file extension. `format/options/option` is name/value pairs: `pdf-version` (`SelectPdfVersion`), `tagged` (`UseTaggedPDF`), and `watermark` (`Watermark`). A v3 namespace is still HTTP 400. `template/@outputFormat` selects the format when `output` is absent. When `output/format/@type` is present, that type is the format. A file whose name ends in `.pdfa` still uses the PDF/A save path when the request did not set a PDF version. The Java client schema is that same v2 file.

Depends on: wave 2 schema choice.

### F6. Calc and Impress on the same request path

Status: **landed in wave 6**.

Each application has its own instruction set, and a set can grow without adding the tag to the others. Writer keeps `Userfield`, `Texttable`, `Image`, `Autotext`, `Bookmark`, and `Macro`. Calc starts with `cell` (`sheet` and `coordinate`). Impress starts with `shape` (`name`). There is no shared `named` tag. A tag from another application's set is HTTP 422. A Calc save with no instructions is not this feature. `Budget.ots` and `Deck.otp` resolve when no Writer file of that name is present. `Name.ott` still wins when both a Writer file and a Calc file exist.

### F7. Delivery that is not the HTTP body

Status: **not implemented**.

The response is still the file bytes, held in a `byte[]` on the `Document` object.

Each user has a bucket configured ahead of time. The request cannot name an arbitrary bucket. The bucket must be one already allowed for that user.

On the POST, the caller chooses delivery: stream the file, store it, or both. Stream keeps the response body as the file. `POST /document/generate` stays synchronous. Store writes to that user's allowed bucket and does not force the bytes back as the HTTP body. Both streams the body and stores a copy.

The store is S3-compatible. MinIO is the development server. Odisee does not expire or delete objects. Retention belongs to the bucket.

Jobs stay private to the user. A callback still goes only to a host on `$ODISEE_HOME/etc/callback-hosts`. `GET /ready` stays anonymous.

Depends on: B1 (done — the heap leak made this worse).

### F8. Callback when a job finishes

Status: **landed in wave 6**.

The callback belongs on the job, not on `POST /document/generate`. `POST /document/jobs?callback=https://host/path` stores that URL on the job. When the job finishes, Odisee POSTs JSON (`id`, `status`, `failedInstruction`, and `file` set to `/odisee/document/jobs/{id}`) only if the URL's host is on the server allow-list. An admin adds a host with `POST /callback-host`. The list is `$ODISEE_HOME/etc/callback-hosts`. A URL whose host is off the list does not get a POST. The synchronous generate route does not read a callback.

## Operations

### F9. Authenticated multi-tenancy

Status: **landed in wave 4**.

Wave 4 requires HTTP Basic. Passwords are bcrypt hashes in `$ODISEE_HOME/etc/users`. `java -jar` does not read `odisee-users.xml`. Each user has `var/user/{name}/template`, `var/user/{name}/work`, and `var/user/{name}/output`. Any authenticated user may run macros. `GET /ready` is anonymous. SFTP homes are not implemented. OpenID Connect is not implemented.

Depends on: wave 4 (authentication and per-user directories are done).

### F10. Office pool from configuration, including remote hosts

Status: **landed in wave 6**.

`etc/odiinst` is read again when its text changes. The pool in this JVM picks up a new host. A restart of the JVM is not required.

The optional 8th field is the pool group. A blank field is `group0`. The request's v2 `<group name="..."/>` selects that group. A missing `<group>` is `group0`. The legacy `<ooo group="..."/>` element stays unread.

A failed health check drops a remote host from the pool and does not signal a local `soffice`. A healthy local slot stays in the pool. The probe still uses the wave 3 recover deadline. A deadline on a local slot still drops that slot and signals `soffice`.

### F11. One request schema, published for the clients

Done in wave 2. The server and the Java client use v2. Wave 6 adds `output/format` to that same namespace. The schema is `webservice/src/main/resources/xml/v2/request.xsd`, copied to `clients/client-java/src/main/schema/request.xsd`. The v3 and v2.6 request schemas stay deleted. A v3 namespace is still HTTP 400. PHP, VB.NET, and the Oracle package still build the v2 shape; their examples under `webservice/src/main/docker/var/request` are unchanged.

### F12. Integration suite with a headless LibreOffice

One fixture `.ott`, one XML request, one assertion on the PDF text (PDFBox is already a dependency). Run it in CI against the Docker image. Wave 1 tests the helpers without starting office; they do not prove a document was filled in.

Wave 5 proves that request through the Java client against a local server (`:clients:client-java:javaClientOfficeTest`). CI still does not run that task.
