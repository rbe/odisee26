# Odisee feature ideas

These are product additions, not bugfixes. They assume the request contract and the office pool from `docs/WAVES.md` are trustworthy. F2 shipped with wave 3. F11 landed with wave 2. The rest are not implemented yet.

## Generation API

### F1. Job API

`POST /document/generate` holds the HTTP connection for as long as LibreOffice takes. Return `202` with a job id. `GET /document/jobs/{id}` returns status, the instruction that failed, and the file when it is ready. Callers can retry a `503` without guessing whether the first attempt is still running.

Depends on: wave 2 status codes, wave 3 pool deadlines.

### F2. Readiness and metrics

Status: **landed in wave 3**.

`GET /ready` (context path `/odisee`) is HTTP 200 only when at least one office port accepts a UNO connection, otherwise 503. The JSON body exports pool size, in-use count, the last generation time, instruction failures, and `soffice` restarts. The watchdog becomes something an operator can see.

### F3. Template catalog and dry run

`GET /template/{name}` returns the user fields, bookmarks, tables, and the revisions on disk. `POST /document/generate?dryRun=true` resolves instructions and does not save. People building templates in the `.oxt` extension can see a misspelled field before a batch run.

Depends on: wave 1 template locator (done), wave 4 per-user directories.

### F4. Working template revisions

Store `var/template/{name}/rev/{n}.ott`, honor `LATEST`, and refuse a revision that is not on disk. Wave 1 locates `Name.ott` and `Name_revN.ott` in the flat directory the server already uses. A directory per template is the layout the class comment in `OdiseeXmlCategory` already describes.

Depends on: wave 1 locator (done).

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

The admin guide describes per-user SFTP homes and HTTP Basic Auth. The running service does not enforce either (B21, B22). Each principal gets a template directory under `var/user/{name}/template`, and macro execution is a separate privilege.

Depends on: wave 4.

### F10. Office pool from configuration, including remote hosts

Wave 1 reads host and port from `etc/odiinst`. A follow-on is hot reload, a per-group name (the v2 `<group name="..."/>` element is unused), and a health check that removes a remote host without a restart.

Depends on: wave 1 parser (done), wave 3 deadlines.

### F11. One request schema, published for the clients

Done in wave 2. The server and the Java client use v2 (`template/@outputFormat`). The schema is `webservice/src/main/resources/xml/v2/request.xsd`, copied to `clients/client-java/src/main/schema/request.xsd`. The v3 and v2.6 request schemas are deleted. PHP, VB.NET, and the Oracle package still build that same shape; their examples under `webservice/src/main/docker/var/request` are unchanged.

### F12. Integration suite with a headless LibreOffice

One fixture `.ott`, one XML request, one assertion on the PDF text (PDFBox is already a dependency). Run it in CI against the Docker image. Wave 1 tests the helpers without starting office; they do not prove a document was filled in.
