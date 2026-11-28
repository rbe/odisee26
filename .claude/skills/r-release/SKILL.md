---
name: r-release
description: Closes the task on the open pull request after the owner's test, then ships the slice. Use after r-review, for a fix or a feature. Merges into the default branch when CI passed. Invokes r-github-pipeline when delivery changes.
---

The general rules in `CLAUDE.md` apply.

<purpose>
`r-release` ships one reviewed slice from one branch to the default branch. `r-review` has pushed the branch and opened the pull request. `r-release` closes the task on that branch before the merge, so one pull request holds the change and the close.
</purpose>

<steps>
1. If the change touches the workflow, the version, publish, the image, cleanup, or retention, invoke `r-github-pipeline`.
2. Invoke `r-project-setup` only if that same change adds or overrides a Make target.
3. If the branch of the slice is already merged into the default branch, stop. Tell the owner that the close now needs a new branch and a second pull request.
4. If this task does not ship, ask `r-record` to write the Release section `Not invoked: this slice does not ship.` and to close the task. Commit the board documents on the branch, push the branch, and stop.
5. Read the Review section of the history file. Stop if the section does not report all six gates as passed, or if `make gates-check` fails. Stop if a finding is neither fixed nor a roadmap row.
6. If the repository has `.github/workflows` and `r-github-pipeline` is installed, run `make ci-status WAIT=1` for the head of the branch. If the repository has no workflow, run the gate targets of the general rules. Stop if the target or a gate fails.
7. If the request did not ask for a ship, ask the owner whether the test of the pull request passed. Stop if the owner says no.
8. Ask `r-record` to write the Release section: what ships, and the Make target or the merge that ships it. Then ask `r-record` to close the task.
9. Commit the board documents on the same branch. If `origin` exists, push. The first line of that commit says that the task is closed.
10. If the owner said that the owner merges, stop here. The pull request holds the change and the close, and it is ready for the merge.
11. Merge the branch into the default branch. If `.github/workflows` exists and `make help` lists `pull-request`, run `make pull-request-merge WAIT=1`. If not, use a Git merge commit, and push the default branch if `origin` exists.
12. If `r-github-pipeline` is not installed, run the target that `CLAUDE.md` names for the shipment, such as `make build` for a library package. If `r-github-pipeline` is installed and the repository has `.github/workflows`, the push to the default branch publishes only when the repository variable `AUTO_PUBLISH` is `true`. Otherwise the owner starts the publish with `workflow_dispatch`. If the repository has no `.github/workflows`, the merge is the shipment.
</steps>

<rules>
- Stage only the files of the change, by name. Never run `git add -A`, `git add .`, or `git commit -a`.
- The first line of a commit message is one sentence that says what the system now does.
- A library with no package ships with the merge.
- The owner creates a `v*` tag. This skill creates none.
- You may delete the branch of the slice after the merge.
</rules>
