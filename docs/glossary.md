# Glossary

Each row is one term that the user and the agents use for one thing. `r-record` writes each row. The communication contract says when an agent reads this file.

| Term | Meaning | Path |
| --- | --- | --- |
| ADR | An architecture decision record: one file that states a rule for every change, with the reason. | `docs/adr/` |
| allow-list | A list of values that a check accepts; the check rejects every other value. | `none` |
| AUTO_PUBLISH | A GitHub repository variable; if it is `true`, a push to `master` also runs the publish jobs. | `.github/workflows/ci.yml` |
| bearer token | The login token that a client sends in the header `Authorization: Bearer`. | `none` |
| board | The files that record open work and rules: the roadmap, the ADRs, the history files, the glossary, and `docs/ids.yaml`. | `docs/` |
| bucket | The S3 storage place of one user, configured by one line in `etc/buckets`, where `store` and `both` write the file. | `webservice/src/main/groovy/org/odisee/document/BucketFile.groovy` |
| callback | A URL that Odisee posts the job result to when a job ends, only to a host listed for that user in `etc/callback-hosts`. | `webservice/src/main/groovy/org/odisee/document/CallbackPoster.groovy` |
| CI | Continuous integration: the GitHub Actions workflow that runs `make` targets for each push and each manual start. | `.github/workflows/ci.yml` |
| client | A library that builds a request and sends it to Odisee, in Java, PHP, VB.NET, or Oracle PL/SQL. | `clients/` |
| delivery | The request field that selects what Odisee does with the file: `stream` (return it), `store` (write it to the bucket), or `both`. | `webservice/src/main/groovy/org/odisee/document/Delivery.groovy` |
| dry run | A call of `POST /document/generate?dryRun=true` that applies the instructions and saves no file. | `webservice/grails-app/controllers/org/odisee/document/DocumentController.groovy` |
| gate | One of the six checks `make editorconfig`, `make format`, `make lint`, `make test`, `make integration`, and `make coverage`; `make gates` runs all six. | `Makefile` |
| history file | The record of one closed task: what each stage did and what each gate returned. | `docs/history/` |
| instruction | One element of a request that changes the document, such as `Userfield`, `Texttable`, `Image`, `Bookmark`, `cell`, or `shape`. | `webservice/src/main/groovy/org/odisee/document/InstructionSets.groovy` |
| job | A request sent to `POST /document/jobs`, which Odisee runs in the background and lists in `var/user/{name}/jobs.json`. | `webservice/grails-app/services/org/odisee/document/JobService.groovy` |
| JVM | Java Virtual Machine: one running Java process; each replica is one JVM. | `none` |
| Keycloak | The OpenID Connect server that issues the login tokens in the local stack. | `webservice/src/main/container/keycloak/` |
| macro | A LibreOffice script that a request names, for example in `post-macro`, and that Odisee runs inside the document. | `webservice/src/main/groovy/org/odisee/document/MacroNames.groovy` |
| macro location | The place where LibreOffice looks for a macro: `document` (inside the template), `application` or `user` (the Basic libraries of the office instance), or `share` (scripts installed with LibreOffice). | `none` |
| MinIO | The S3-compatible storage server of the local stack and of `make test-minio`. | `webservice/src/main/container/compose.yaml` |
| odictl | The bash command that an admin runs on the host to start Odisee and to create users, buckets, and callback hosts. | `webservice/src/main/container/bin/odictl` |
| odiinst | The file `etc/odiinst` that lists the host, the port, and the group of each office instance. | `webservice/src/main/groovy/org/odisee/document/OdiinstParser.groovy` |
| ODISEE_HOME | The directory that holds `etc/` (configuration) and `var/user/` (the files of each user). | `none` |
| office instance | One running LibreOffice process (`soffice`) that Odisee connects to through UNO. | `none` |
| office pool | The set of connections to the office instances that Odisee shares between requests. | `webservice/src/main/java/org/odisee/ooo/connection/` |
| OpenID Connect | The login standard that Odisee uses: a client gets a bearer token from Keycloak and sends it with each request. | `webservice/src/main/groovy/org/odisee/security/` |
| Podman | The only container engine of this project; every Make target runs its tools in a Podman container. | `none` |
| pre-push hook | The Git hook that `make git-hooks` installs; it refuses a push unless `make gates` passed on the same files. | `.claude/skills/r-project-setup/contract.mk` |
| publish | The CI jobs `publish-container` and `publish-github`, which push the image to `ghcr.io/rbe/odisee26` and attach the Linux zip to a GitHub release. | `.github/workflows/ci.yml` |
| RalfSkills | The skill collection in `.claude/skills/` that the package tool APM (`apm.yml`) installs from `rbe/ralfskills`, with the shared Make files. | `apm.yml` |
| replica | One of several Odisee services that share one `ODISEE_HOME` directory. | `none` |
| request | The XML or JSON body that names a template, the instructions, and the output format. | `webservice/src/main/resources/xml/v2/request.xsd` |
| revision | One numbered version of a template, stored as `rev/{n}.ott`; `LATEST` selects the highest number. | `webservice/src/main/groovy/org/odisee/document/TemplateLocator.groovy` |
| roadmap row | One row of `docs/ROADMAP.md`: a feature, a defect, or a question that is not done. | `docs/ROADMAP.md` |
| run number | The number that GitHub Actions gives each run of the workflow CI; the published version is `<tag>-<run number>`, such as `2.6.0-42`. | `none` |
| S3 | The storage protocol of Amazon S3, which MinIO and other servers also speak. | `none` |
| template | A LibreOffice file (`.ott`, `.ots`, or `.otp`) that a user stores under `var/user/{name}/template/` and that Odisee fills. | `none` |
| test-java | The Make target that starts LibreOffice in Podman and runs the Java client against it; `make integration` runs it. | `Makefile` |
| UNO | Universal Network Objects, the LibreOffice interface that Odisee uses to open, change, and save a document. | `none` |
| user | The OpenID Connect client id of a caller; each user has its own directory `var/user/{name}/`. | `webservice/src/main/groovy/org/odisee/security/OidcToken.groovy` |
