# Git workflow

How your work must look in Git when you submit. This page states the end state, not the commands: [Pro Git](https://git-scm.com/book/en/v2) and the other links in `docs/LEARNING.md` cover how.

## 1. Your fork

- You work in your own fork of this repository. Like this repository, your fork is public.
- Your fork contains only `main`, plus your own branches. It has a remote called `upstream` that points at this repository. You fetch `platform/sdk-next` and `platform/test-kit` from `upstream`; you don't copy them into your fork.

## 2. Branches and pull requests

- Every change reaches `main` through a feature branch and a pull request **inside your fork**: the base is your fork's `main`. GitHub proposes this repository as the base by default. Change it; never open a pull request against this repository.
- Pull requests are merged without squashing.
- A feature branch is brought up to date by rebasing it onto `main`, never by merging `main` into it.
- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/).

## 3. Platform branches

- **SDK-101 and SDK-107** come in from `platform/sdk-next` with `git cherry-pick -x`, and nothing else from that branch does. Don't merge that branch or rebase onto it.
- One of the two conflicts with a change already on `main`. Resolve it so that **both** changes survive.
- `legacy-core-sdk/`, `mock-bank/` and `web-client/` change in no other way.
- You may also cherry-pick anything useful from `platform/test-kit`, with `-x`. Leave out work in progress.

## 4. SEC-301

- The committed credential is gone from the tip of `main`.
- A proper `.gitignore` exists, and `target/` and `.idea/` are no longer tracked.
- `DESIGN.md` explains what else has to happen, outside the repository as well as inside it.

## 5. Release and hotfix

1. When your work is finished, create the branch `release/1.0` from `main`, and the annotated tag `v1.0.0` on it.
2. **INC-112.** Risk lowers the USD fraud threshold from 5,000.00 to 3,000.00, effective immediately.
   - Change the default on `main` through a pull request.
   - Backport it to `release/1.0` with `git cherry-pick -x`.
   - Tag the result `v1.0.1`, annotated.

## 6. What you submit

Your fork URL, the SHA of `main` and the SHA of `v1.0.1`, by the deadline in the README.
