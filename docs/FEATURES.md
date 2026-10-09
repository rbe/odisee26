# Odisee feature ideas

These are product additions, not bugfixes. They assume the request contract and the office pool from `docs/WAVES.md` are trustworthy. F2 shipped with wave 3. F3, F4, and F10 shipped with wave 6. F9 shipped with wave 4. F11 landed with wave 2. F1, F5, F6, F7, and F8 are not implemented yet.

## Generation API

### F1. Job API

`POST /document/generate` holds the HTTP connection for as long as LibreOffice takes. Return `202` with a job id. `GET /document/jobs/{id}` returns status, the instruction that failed, and the file when it is ready. Callers can retry a `503` without guessing whether the first attempt is still running.

Depends on: wave 2 status codes, wave 3 pool deadlines.

### F2. Readiness and metrics

Status: **landed in wave 3**.

`GET /ready` (context path `/odisee`) is HTTP 200 only when at least one office port accepts a UNO connection, otherwise 503. The JSON body exports pool size, in-use count, the last generation time, instruction failures, and `soffice` restarts. The watchdog becomes something an operator can see.

### F3. Template catalog and dry run

Status: **landed in wave 6**.

`GET /template/{name}` (context path `/odisee`) requires a login. The body is JSON: `name`, `revision`, `userFields`, `bookmarks`, `tables`, and `revisions`. Those come from that user's `var/user/{name}/template` only. Another user's template is HTTP 404. No login is HTTP 401. The caller is the security context (`callerFromContext`), the same source as `POST /document/generate`.

`POST /document/generate?dryRun=true` requires a login. It opens the template, applies the instructions, and does not save a file under `output`. A failed instruction is HTTP 422 and does not drop a pool slot. A UNO deadline still drops the slot. The synchronous `POST /document/generate` response stays the file bytes.

### F4. Working template revisions

Status: **landed in wave 6**.

Revisions live at `var/user/{name}/template/{templateName}/rev/{n}.ott`. `LATEST` is the highest number across that directory and the flat files. A revision that is not on disk is HTTP 404. `Name.ott` and `Name_revN.ott` in the user's flat template directory still resolve. User A cannot see user B's revisions. There is no shared `var/template` directory.

## Documents

### F5. Output options that are not a filename suffix

PDF/A is detected by a `.pdfa` suffix. Expose PDF version, tagged PDF, and a watermark as `format/options`, which the v3 schema already allows and the runtime ignores.

Depends on: wave 2 schema choice.

### F6. Calc and Impress on the same request path

`OdiseeFileFormat` already lists spreadsheet filters. `OfficeDocumentType.SPREADSHEET` is the only Calc entry, and wave 1 only corrects its document extension to `ods`. Instructions are Writer-specific (user fields, text tables, bookmarks). A second instruction set, or a shared "named range / named shape" set, would cover Calc and Impress without a second server.

### F7. Delivery that is not the HTTP body

The response is the file bytes, held in a `byte[]` on the `Document` object. Large batches need "write this PDF to object storage and POST this URL". The web tier then does not keep the document on the heap.

Depends on: B1 (done — the heap leak made this worse).

### F8. Callback when a job finishes

Pair with F1. The request names a URL. Odisee POSTs the status and a download handle when the office process returns. Oracle and PHP callers that already build the XML can stay synchronous; new callers do not have to.

## Operations

### F9. Authenticated multi-tenancy

Wave 4 requires HTTP Basic. Passwords are bcrypt hashes in `$ODISEE_HOME/etc/users`. Each user has `var/user/{name}/template`, `var/user/{name}/work`, and `var/user/{name}/output`. Any authenticated user may run macros. SFTP homes are not implemented. OpenID Connect is not implemented.

Depends on: wave 4 (authentication and per-user directories are done).

### F10. Office pool from configuration, including remote hosts

Status: **landed in wave 6**.

`etc/odiinst` is read again when its text changes. The pool in this JVM picks up a new host. A restart of the JVM is not required.

The optional 8th field is the pool group. A blank field is `group0`. The request's v2 `<group name="..."/>` selects that group. A missing `<group>` is `group0`. The legacy `<ooo group="..."/>` element stays unread.

A failed health check drops a remote host from the pool and does not signal a local `soffice`. A healthy local slot stays in the pool. The probe still uses the wave 3 recover deadline. A deadline on a local slot still drops that slot and signals `soffice`.

### F11. One request schema, published for the clients

Done in wave 2. The server and the Java client use v2 (`template/@outputFormat`). The schema is `webservice/src/main/resources/xml/v2/request.xsd`, copied to `clients/client-java/src/main/schema/request.xsd`. The v3 and v2.6 request schemas are deleted. PHP, VB.NET, and the Oracle package still build that same shape; their examples under `webservice/src/main/docker/var/request` are unchanged.

### F12. Integration suite with a headless LibreOffice

One fixture `.ott`, one XML request, one assertion on the PDF text (PDFBox is already a dependency). Run it in CI against the Docker image. Wave 1 tests the helpers without starting office; they do not prove a document was filled in.

Wave 5 proves that request through the Java client against a local server (`:clients:client-java:javaClientOfficeTest`). CI still does not run that task.
