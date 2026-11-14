# Odisee bugs

Findings from a code review of the document-generation path. Status is against `master` plus the wave that closed the item.

Wave 6 closes B29. B25's stricter allow-list stays open.

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
| B6 | major | fixed in wave 1 | The runtime only read `template/@outputFormat` (v2). A v3 request that uses `output/format/@type` produced no file and failed with "Got zero bytes from office process". The current request reads v2 `output/format`. A v3 namespace is HTTP 400. `template/@outputFormat` applies when `output` is absent. |
| B7 | minor | fixed in wave 1 | `OfficeDocumentType.SPREADSHEET` sets the document extension to `ots` (the template extension). It is `ods`. |
| B8 | major | fixed in wave 2 | XML is validated against the v2 request schema before generation. v3 and v2.6 are HTTP 400. Those request schemas are no longer shipped. |
| B9 | major | fixed in wave 2 | The request parser rejects a `DOCTYPE` and does not resolve external entities. |

## Office pool

| ID | Severity | Status | Bug |
|---|---|---|---|
| B10 | blocker | fixed in wave 1 | `RequestService` connected to `host-of-line-1` and ports `2001..2001+n`. Ports in `etc/odiinst` were ignored. The sample file uses 2001 and 2002, so a default install hid the bug. |
| B11 | major | fixed in wave 1 | `OdiseeInstance.readOdiinst` replaced `oooGroup['group0']` on every host, so only the last host remained. Blank lines were parsed as rows. |
| B12 | major | fixed in wave 1 | `OfficeConnectionFactory.fetchConnection` returned null after a connect failure. `toDocument` then failed even when another instance in the pool was healthy. |
| B13 | major | fixed in wave 1 | If every bootstrap failed, the factory still started with an empty queue. The SLF4J message used `%{}` instead of `{}`. |
| B14 | major | fixed in wave 3 | UNO calls had no deadline. A live but wedged `soffice` held a pool slot until the JVM exits. `odiwatchdog` only helps after the process has exited. Wave 3 bounds the call and drops the slot. |
| B15 | major | fixed in wave 1 | `processTemplate` called `xComponent.close()` only on the success path. A failure in `open` or `saveAs` left the document open inside LibreOffice. |
| B16 | minor | fixed in wave 1 | `OOoConnection.connect` referenced `diff` in a branch that Groovy compiles but cannot run (`MissingPropertyException`). `isUsable()` already throws before that branch. |

## Failures the client cannot see

| ID | Severity | Status | Bug |
|---|---|---|---|
| B17 | major | fixed in wave 1 | A failed instruction was logged and the document was still saved and returned as HTTP 200. |
| B18 | major | fixed in wave 1 | Every error, including "office unreachable", was HTTP 400. |
| B19 | major | fixed in wave 2 | The raw body is capped at 8 MiB and the expanded body at 32 MiB. Either cap is HTTP 400. |
| B20 | minor | fixed in wave 2 | `DocumentController` closes `response.outputStream` only on the success path. The error body written by `HttpStatuses` is left in place. |

## Security and tenancy

| ID | Severity | Status | Bug |
|---|---|---|---|
| B21 | blocker | fixed in wave 4 | The Grails app had no authentication. `java -jar` does not load Tomcat's `odisee-users.xml`. Spring Security now reads bcrypt hashes from `$ODISEE_HOME/etc/users`. Any authenticated user may run a macro. The wave 4 fix was a password file, and F9 removed it. |
| B22 | major | fixed in wave 4 | `DocumentController` fell back to a hardcoded user named `odisee`. No login is HTTP 401 and does not generate a document. |
| B23 | major | fixed in wave 1 | Template names were joined onto the template directory with `Path.resolve`. A name containing `..` or a separator could escape that directory. Merge `input/@filename` accepted an absolute path because `Path.resolve` replaces the base. A client-supplied `outputPath` is overwritten with the request directory. |
| B24 | major | fixed in wave 1 | Post-process `action/@type` was turned into a method name (`process${Type}`). An unexpected type failed at runtime or could hit an existing method. Instruction tag names were dispatched the same way. |
| B25 | major | partial in wave 4 | Request `@name` is used as a filename. Wave 1 rejects separators and `..`. Macro name, library, and language use that same check and a bad name is HTTP 400. A stricter allow-list is still open. |

## Structure

| ID | Severity | Status | Bug |
|---|---|---|---|
| B26 | major | fixed in wave 3 | Two office stacks are compiled: `OfficeConnection` / `OfficeConnectionFactory` (used) and `OOoConnection` / `OOoConnectionManager` / `OOoProcess` (not used by the request path). The unused stack is deleted. |
| B27 | major | fixed with the toolchain update | `RequestService` no longer uses `com.sun.org.apache.xerces.internal.dom.DeferredNode`. XML is written through `GPathResult` and `org.w3c.dom.Node`, which JDK 21 allows. |
| B28 | minor | fixed in wave 4 | A missing or blank `ODISEE_HOME` is an `OdiseeException`. The environment value wins and is absolute. |
| B29 | minor | fixed in wave 6 | `SPREADSHEET` was the only Calc hint, and the instruction set was Writer-only. Calc now starts with `cell`. Impress starts with `shape`. Writer keeps its own tags. |
| B30 | major | fixed in wave 5 | Wave 1 covers the helpers. Tests 1–3 open a real template in LibreOffice (`GenerationBasisTest`, Podman). Tests 4–5 cover HTTP status and the pool. The Java client asserts the Hallo value in the PDF against a local server (`OdiseeClientLocalTest`). |
| B31 | major | fixed in wave 5 | A successful HTTP Basic login still left `request.userPrincipal` null. Grails captured the request before Spring Security wrapped it, so `POST /document/generate` returned 401. The caller is read from the security context. |
| B32 | major | fixed in wave 5 | `RequestService` cast the active DOM request to `GPathResult`. A DOM body failed before LibreOffice opened the template. A DOM node is serialized as XML. |
| B33 | minor | fixed | `OdiseeBootStrap` read `grailsApplication.config.odisee.version` by dot notation. Startup logged NavigableMap warnings for `[odisee]` and `[odisee, version]`. The banner reads `config.getProperty('odisee.version', String)`. A set value is unchanged. An absent value is null. |
| B34 | minor | fixed | Production startup logged `DefaultListableBeanFactory` INFO when Grails replaced `localeResolver`, `dispatcherServlet`, `dispatcherServletRegistration`, and `codecLookup`. The production profile sets that logger to WARN. Bean overriding stays on. |
| B35 | major | fixed | macOS `/bin/bash` is 3.2. `odilib` uses `declare -A`, which that bash rejects (`declare: -A: invalid option`). `#!/usr/bin/env bash` still finds `/bin/bash` after `brew install bash`. Before any `declare -A`, a shell older than 4 re-execs the entry script (`$0`: `odictl`, `odipost`, or `odiwatchdog`) with the first executable of `/opt/homebrew/bin/bash` or `/usr/local/bin/bash`. If neither exists, it prints `Odisee needs bash 4 or newer. On macOS: brew install bash.` and exits 1. Bash 4 or newer, including the container, does not re-exec. Homebrew bash does not need to be first on `PATH`. |
| B36 | major | fixed | macOS has no `flock`. `odictl` locked `$ODISEE_HOME/etc` with `flock` and exited 1 (`could not lock`). When `flock` is on `PATH`, that lock stays. Otherwise the lock is the directory `etc/.odictl.lock.d`, removed when `odictl` exits. A directory whose pid is dead is removed and the lock is taken again. |
| B37 | major | fixed | macOS `netstat` is `/usr/sbin/netstat`. IntelliJ's Gradle `PATH` is `/usr/bin:/bin`. `netstat -f inet` is IPv4 only, and Java's loopback on macOS is often `::1`, so the listener count stayed 0. Darwin uses `/usr/sbin/netstat -an -p tcp` when `netstat` is not on `PATH`. |
| B38 | major | fixed | `ODISEE_SHELL=1` backgrounds the JVM and then `exec`s an interactive bash. On macOS that job can be stopped before it writes its pid, and `exit` then kills it. The shell disables monitor mode, ignores `HUP`, and records the pid before `exec`. |
| B39 | minor | fixed | `testLinuxOfficePortCheckReadsProcNet` required `get_inst_status` to see the JVM socket. On macOS that is `netstat`, and the count stayed 0. The test's proof is the `/proc/net/tcp` fixture parser, which runs on every host. The live listener count is asserted on Linux. |
| B40 | blocker | fixed | The service and test `Containerfile`s installed only `libreoffice-writer`, so F6 Calc and Impress generation could not run in containers. Both images install `libreoffice-calc` and `libreoffice-impress` alongside `libreoffice-writer`. |
| B41 | blocker | fixed | `odiwatchdog` enabled `set -o nounset`, read unbound `${port}` and `${WD_TIMEOUT}` instead of `${INSTCFG[port]}` and `${ODISEE_WD_TIMEOUT}`, and restarted with `odictl -q start` instead of `start-inst`. `odictl -w -i` omitted `w` from `getopts` and `CMD_DEPENDENCY_OR[i]` and called `$0 watch_instance` instead of `odiwatchdog -i`. |
| B42 | minor | fixed | `odictl` matched autostart lines with `grep "true$"`, skipping rows with an 8th `group` field, and `odictl user` overwrote customized `audience` and `ui-client` lines in `etc/oidc`. `odilib` expanded literal `yalt`, sorted ports lexicographically, used unanchored `grep` and non-portable `sed -i''`, missed `/usr/lib/libreoffice` in Linux `find_installation`, and had a non-breaking space and unset `${oxt}` in extension helpers. |
| B43 | minor | fixed | `bin/odidev` matched `GNU/Linux` instead of `uname -s`'s `Linux`. `odipost` compared `${requestfile}` to literal `^/*`, fell back to `var/test` instead of `var/request`, ran `eval curl`, and could not send `Authorization: Bearer` via `--bearer` or `ODISEE_TOKEN`. |
| B44 | minor | fixed | `packageDistribution`, `prepareOdiseeImage`, and `Containerfile` pre-created or staged legacy shared `var/template`, `var/document`, `var/merge`, `var/image`, and `var/request` directories. Only runtime directories stay; tenant files live under `var/user/{name}/`. |
| B45 | minor | fixed | A manual `workflow_dispatch` image build on a feature branch ran `published-versions.sh` and evicted an older `master` `2.6.0-<run>` version, and `image.yml` did not ignore HTTP 404 when listing container versions on a brand-new GHCR package. |
| B46 | blocker | fixed | Synchronous `POST /document/generate` left `var/user/{name}/output/{uuid}/` on disk for `stream`, `store`, and `both` even when `archive/@files` was `false`, and `JobService.writeFile` wrote the generated job file a second time over the file `OdiseeService` already saved. Synchronous calls delete the ephemeral request directory unless `archive/@files` is `true`. Async jobs keep `var/user/{name}/output/{id}/` and skip the duplicate write. |
| B47 | major | fixed | Reloading `etc/odiinst` replaced the connection queue at capacity without closing retired idle bridges and cleared `checkedOut`, so an in-flight call returning via `repositConnection` failed with HTTP 503 or entered the new pool unmapped. `RequestService` also read `officeConnectionFactory` outside its monitor. Reload closes retired idle bridges, closes retired in-flight connections on return, and synchronizes `RequestService` factory access. |
| B48 | major | fixed | `OfficeConnectionFactory.fetchConnection(group, waitForever)` shared one queue across groups and broke out once another group's idle slot was seen twice, failing immediately with HTTP 503 instead of waiting `QUEUE_POLL_TIMEOUT` for a busy slot in the requested group. Each group now polls its own queue up to the timeout. |
| B49 | major | fixed | `CallbackAllowList` accepted loopback, private, link-local, and single-label internal hosts, and `CallbackPoster` resolved DNS again inside `HttpURLConnection`, allowing DNS rebinding to internal addresses. `CallbackAllowList` rejects non-public literals and internal hostnames, and `CallbackPoster` resolves once, requires every address to be public, and connects the socket directly to the pinned address without following redirects. |
| B50 | minor | fixed | `HttpStatuses.statusFor` fell back to HTTP 400 for unexpected exceptions, and `RequestService.refreshPool` wrapped pool bootstrap failures in `IllegalStateException`, turning unavailable office instances into HTTP 400. Pool bootstrap failures and `OdiseeServerRuntimeException` map to HTTP 503, and unexpected server exceptions map to HTTP 500. |
| B51 | minor | fixed | `request.schema.json` omitted top-level `delivery` under `additionalProperties: false`, `DocumentStreamer` cast `System.currentTimeMillis()` to `int` for `SecureRandom.nextInt` (negative bound overflow), `OdiseeConstant.MIME_TYPE_WORD97` was the ODT MIME type instead of `application/msword`, `request.xsd` documented `var/USERNAME/pdf` and `merge-all-results` instead of `var/user/USERNAME/work` and `merge-results`, and the `RequestSchema` v3 error omitted `output/format/@type`. |
| B52 | major | fixed | `OOoDocumentCategory.executeMacro`, `OOoFieldCategory.setUserFieldContent`, `OOoTextTableCategory.set`, and `OOoAutotextCategory` logged missing macros, userfields, Writer tables, autotext groups/entries, and bookmarks and still returned HTTP 200. Each now throws `OdiseeException` (HTTP 422). |
| B53 | major | fixed | `PdfService.mergeDocuments` returned `null` on merge errors, leaving a `NullPointerException` in `PostProcessService`. `FileHelper.decomposeFilename` stripped dots from parent directories such as `.local/share/odisee`. `OdiseeFileFormat` lacked `ppt`/`pptx` and mapped Calc and Impress PDF saves to `writer_pdf_Export`, and `OOoDocumentCategory.saveAsPDF_A` hardcoded `writer_pdf_Export` and PDF permission flags forbidden by PDF/A-1. Merges now throw HTTP 422, parent dots stay intact, Calc and Impress select `calc_pdf_Export` and `impress_pdf_Export`, `ppt`/`pptx` are registered, and `saveAsPDF_A` writes tagged, unencrypted PDF/A-1 with the application's filter. |
| B54 | major | fixed | `image/@url` passed unchecked `file://` and internal HTTP URLs to LibreOffice `GraphicURL`, `saveImageToFile` required POSIX permissions and threw `OdiseeServerRuntimeException` on bad MIME types, and decoded `image*` temp files were left in `output`. External image URLs must be public `http`/`https` hosts, unsupported image types and disallowed URLs fail with HTTP 422, non-POSIX filesystems skip `setPosixFilePermissions`, and decoded temp images are deleted after save or failure. |
| B55 | major | fixed | `OOoDocumentCategory.open` mutated `Path.metaClass` and `XComponent.metaClass` across concurrent requests, and `OOoAutotextCategory.getAutotext` and `OOoDocumentCategory.executeDispatch` read missing `xMultiComponentFactory` and `xOfficeComponentContext` properties on `OfficeConnection`. Component state now lives in a thread-safe weak-identity map cleared on `close`, and UNO context lookup uses `getXRemoteServiceManager()` and its `DefaultContext` property. |
| B56 | major | fixed | `Coordinate.parseCoordinate` miscomputed multi-letter base-26 columns (`BA` became 27 instead of 52, `AAA` became 78 instead of 702), did not normalize lowercase column letters, and threw raw `NumberFormatException` when a coordinate had no digits. Columns now use base-26 uppercase math, plain table names resolve without digits, and malformed coordinates fail with `OdiseeException` (HTTP 400). |

## Clients and documentation

| ID | Severity | Status | Bug |
|---|---|---|---|
| B57 | blocker | fixed | `OdiseeClient` reused one `final Request` across `createRequest` calls, `executeBasicMacroInDocument(String)` threw `NullPointerException` when `parameters` was null, `OdiseeHttpHelper` mutated JVM-global `CookieHandler.setDefault` and `http.maxRedirects`, `OdiseeClient.process` called deprecated `new URL(String)`, `OdiseeJson` dropped `output/format`, `clients/client-java` kept an unused `spring-security-crypto` test dependency, and `DesktopClient` / `LoginPane` still used HTTP Basic instead of `OdiseeClient.bearer`. |
| B58 | blocker | fixed | `clients/client-php` sent only `CURLOPT_USERPWD` HTTP Basic, joined `dirname(__FILE__) . 'var/tmp'` without a slash and leaked temporary files, returned error bodies on HTTP >= 400, and dropped `output/format` in `OdiseeJson`. |
| B59 | blocker | fixed | `clients/client-vbnet` sent only HTTP Basic/Digest with `Encoding.Default`, swallowed HTTP >= 400 responses other than 401/404, appended duplicate `<template>` elements in `setLatestTemplate`, wrapped `<action>` in `<post-process><instructions>` with `input/@file` instead of `input/@filename`, missed the first `Read` in `saveDocument` byte counts, and dropped `output/format` in `Helper.Json`. |
| B60 | blocker | fixed | `clients/client-oracle` sent no `Authorization: Bearer` header, posted to `/oooDocument/generate`, emitted `<!DOCTYPE>` and legacy `<userfields>`/`<insert>` tags rejected by the v2 schema, misspelled `aligns` as `alings` in `aligntablecell`, and computed `Content-Length` with character `LENGTH` instead of UTF-8 byte length. |
| B61 | minor | fixed | `.cursor/skills/release/SKILL.md` omitted the `${GITHUB_REF_NAME}` GHCR tag pushed on `v*` tags; `UsersGuide.adoc`, `AdministratorsGuide.adoc`, `clients/client-php/README.md`, `clients/client-vbnet/README.md`, and `webservice/src/test/resources/intellij/*.http` still carried HTTP Basic examples, missing `/odisee` context paths, `find-inst`, and nonexistent Apache AJP configuration files. |

## Tests added in wave 1

- `TemplateLocatorTest` — `LATEST` is numeric, revision 1 falls back to `Name.ott`, path escape is rejected.
- `OdiinstParserTest` — host and port come from the file, comments and blank lines are skipped.
- `OutputFormatsTest` — `template/@outputFormat` is the extension list when `output` is absent. `output/format/@type` is the extension when it is present. A v3 namespace is still rejected.
- `SafePathsTest` — relative merge paths stay under the root; absolute paths and `..` do not.
- `RequestContextTest` — each call gets its own document list; resetting for the next request keeps documents already produced.
- `CompressionTest` — gzip round-trip and a one-byte body.
- `PostProcessServiceTest` — unknown actions and merge paths that leave the data directory are rejected.
