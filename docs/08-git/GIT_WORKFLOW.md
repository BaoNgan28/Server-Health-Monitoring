# Git Workflow — Group 10

## Long-lived branches

```text
main      = stable / milestone / final release
develop   = integration branch
```

## Task flow

```text
GitHub Issue
  ↓
Assign đúng 1 người + Label + Milestone
  ↓
feature/<MSSV>-issue-<N>-<short-name>
  ↓
commit(s)
  ↓
push task branch
  ↓
Pull Request → develop
  ↓
Screenshot/log + peer review + Approve
  ↓
Merge
  ↓
Update README contribution matrix
```

## Before starting a task

```bash
git switch develop
git pull origin develop
git switch -c feature/<MSSV>-issue-<N>-<short-name>
```

## Before opening a Pull Request

```bash
git status
git fetch origin
git merge origin/develop
```

Then run relevant tests/build, push the branch, and create the PR.

## After the PR is merged

```bash
git switch develop
git pull origin develop
git branch -d feature/<MSSV>-issue-<N>-<short-name>
git fetch --prune
```

## Final release

At the end of Week 3, after every task is merged to `develop` and final QA passes:

1. Open a Pull Request from `develop` to `main`.
2. Require at least one peer approval.
3. Merge only after CI/manual validation passes.
4. Repository Owner tags `v1.0.0` on `main`.
