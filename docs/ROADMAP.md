# Roadmap

The things to do. A row is a feature, a defect, or a question, and is not done. `r-record` deletes the row when the slice closes. Its number is never issued again. The proposal cell is a link or empty.

| Id | Kind | Title | Proposal |
| --- | --- | --- | --- |
| <a id="w-1"></a>W-1 | defect | A request name, a macro name, a macro library, and a macro language pass a stricter allow-list than "no separator and no `..`" (old label B25, partial in wave 4) | |
| <a id="w-11"></a>W-11 | defect | A job that is `queued` or `running` when the service stops keeps that status in `jobs.json` for ever, because no code resumes or fails it at startup | |
| <a id="w-12"></a>W-12 | defect | Two replicas that share one `ODISEE_HOME` can lose a job status update, because `JobStore` locks `jobs.json` only inside one JVM | |
| <a id="w-13"></a>W-13 | defect | `JobService` runs each job on a new thread of an unbounded cached pool, so the number of concurrent jobs has no upper limit | |
| <a id="w-14"></a>W-14 | question | How long does Odisee keep the job files under `var/user/{name}/output/{id}`? Choices: keep them until an admin deletes them, delete them after a fixed number of days, or delete them after the first successful `GET /document/jobs/{id}` | |
| <a id="w-15"></a>W-15 | question | Does CI run all six gates? CI runs `make test` and `make test-java`, which is the integration gate. Choices: add `make editorconfig`, `make format`, `make lint`, and `make coverage` to `.github/workflows/ci.yml`, or change the rule to say that CI runs only those two | |
| <a id="w-16"></a>W-16 | question | Which macro language and location may a request name? Choices: only `Basic` in `document`, `Basic` in `document` and `application`, or any value as now | |
| <a id="w-17"></a>W-17 | question | When does the first publish happen? Choices: create the tag `v2.6.0` and start `publish-container` and `publish-github` by hand, or wait until a named feature is done | |
| <a id="w-18"></a>W-18 | defect | `HttpStatuses.apply` writes the exception message into the body of each error response, also for HTTP 500 and 503, so a client can read internal server messages | |
