---
name: deploy-internal
description: >-
  Deploy this Android app (작명하우스, package com.naminghouse.app) to the Google
  Play internal testing track. Use this whenever the user wants to ship, release,
  or push out a build — phrases like "내부 테스트 배포", "배포 진행해줘",
  "release to Play", "ship an internal build", "deploy to the internal track",
  or even just "배포해줘"/"deploy" in this repo, since internal testing is the
  deploy path here. Also use it when the user asks to bump the version and
  upload, or to re-run a failed internal release. It auto-computes the next
  versionCode, triggers the android-internal-release.yml workflow, watches the
  run, verifies the Play upload, and triages failures.
---

# Deploy to Play internal testing

This repo deploys via the **Android Internal Release** GitHub Actions workflow
(`.github/workflows/android-internal-release.yml`), which builds a signed AAB
and uploads it to the Google Play **internal** track with fastlane `supply`.

The goal is that the user types one thing and gets a deployed build with as
little back-and-forth as possible. Most mechanics live in
`scripts/deploy-internal.sh`; your job is the judgment around it (scope, the one
safety confirm, reporting) and triage if it fails.

## Happy path

1. **Run the script in the background** (it blocks ~7 min watching the run, so
   don't burn a Bash timeout in the foreground):

   ```
   .claude/skills/deploy-internal/scripts/deploy-internal.sh
   ```

   Run it with `run_in_background: true` and let the completion notification
   bring you back. The script prints a plan, triggers `workflow_dispatch` on
   `origin/main`, watches to completion, and reports success + the Play upload
   confirmation, or the failing step + log tail.

2. **Report the result**: versionName/versionCode, the run URL, and whether the
   Play upload was confirmed.

## What the script decides for you

- **versionCode** = last *successful* run's versionCode **+ 1**. Play rejects a
  build whose versionCode doesn't strictly increase, and only *successful* runs
  consume a code — a failed run never reaches the upload, so its code is free to
  reuse. When there is no successful CI run yet, the script falls back to
  `BASELINE_VERSION_CODE` in the script (2 — the 2026-08-17 manual upload) and
  adds one, so the first automated deploy lands on 3 without a manual flag.
- **versionName** follows the house scheme **MAJOR.MONTH.PATCH**, same as
  삼라만상/마이타로: MAJOR = 연도−2025 (2026→1, 2027→2), MONTH = 달력 월,
  PATCH = 같은 달 안의 배포 순번(달이 바뀌면 0으로 리셋). 예: 2026년 8월 첫 배포
  1.8.0, 같은 달 재배포 1.8.1, 9월 첫 배포 1.9.0. 대격변만 수동 —
  `--version-name 3.8.0` 으로 올리면 이후 배포도 그 MAJOR 를 유지한다.
  (The pre-CI manual builds used 1.0/1.1; the first CI deploy restarts the
  numbering at 1.8.0. That's an increase, so Play accepts it.)
- **What gets deployed** = whatever is on **origin/main** right now. The build
  runs on GitHub against the pushed ref, *not* your local working tree.

## Scope

- **Uncommitted changes are never deployed.** The script warns if the tree is
  dirty but proceeds with the pushed HEAD. **Never auto-commit the working tree
  to sweep changes in** — this repo is edited in parallel (Android Studio, other
  agents) and routinely holds unrelated in-progress work. If the user wants
  specific uncommitted work included, stage *only* those files, commit, push,
  then deploy.
- **Unpushed local commits aren't deployed either.** The script refuses to run
  if local `main` is ahead of `origin/main`. When that happens: show the user
  what's unpushed (`git log origin/main..HEAD --oneline`), confirm it's meant to
  ship, push, then re-run. Pushing to `main` is the one consequential step — get
  a yes unless the user already told you to just go.

## Required repo secrets

The workflow needs these on `hwoh87/NamingHouse` (Settings → Secrets and
variables → Actions). They are the **same values** as `hwoh87/saju`, since both
apps sign with the same keystore and live under the same Play developer account
(오효컴퍼니). Claude must not read or set these — the user adds them.

| Secret | What it is |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | base64 of the release keystore |
| `RELEASE_STORE_PASSWORD` | keystore password |
| `RELEASE_KEY_ALIAS` | key alias |
| `RELEASE_KEY_PASSWORD` | key password |
| `PLAY_SERVICE_ACCOUNT_JSON` | Play Developer API service-account JSON |

This app has **no Firebase**, so unlike 삼라만상/마이타로 there is no
`GOOGLE_SERVICES_JSON_BASE64` secret and no google-services decode step. Don't
copy one in without also adding the plugin.

## Interpreting the result

- **Success**: the script prints the Play upload confirmation and the version.
  Tell the user it's live on the internal track and may take a few minutes to
  appear in Play Console.
- **"run is success but no Play upload marker"**: usually just log-aggregation
  lag, not a failure. The script already retries for 2 minutes. Re-check with
  the printed grep before claiming anything went wrong.

## Failure triage

The script prints the failing step and the tail of its log. Map it:

- **Set up Ruby / bundler** — the historically flaky spot in the sibling repos.
  No `Gemfile.lock` is committed here on purpose; if one appears with an old
  `BUNDLED WITH` or Ruby-incompatible pins (e.g. CFPropertyList 3.0.9), that's
  the regression — remove it rather than fighting the pins.
- **Build release bundle (AAB)** — a real compile error. Read the Gradle output,
  fix the Kotlin, commit, push, re-deploy. This is app code, not CI.
- **Upload to Play Console** —
  - *versionCode already used*: a previous successful upload claimed it. Just
    re-run (the script recomputes from the latest success), or pass
    `--version-code <higher>`.
  - *auth / service account*: `PLAY_SERVICE_ACCOUNT_JSON` or the signing secrets
    are missing/expired. The user must fix secrets; you can't.

After fixing CI-side issues, commit **only** the CI files — keep parallel
app-code churn out of your commit — then re-run the script.

## Not covered here

Production promote is not wired up in this repo (삼라만상 has an
`android-production-promote.yml` + a `production_promote` fastlane lane). If the
user asks to promote to production, that workflow needs porting first — say so
rather than improvising a production push.

## Manual fallback

If the script is unavailable or the workflow changed:

```bash
# next versionCode = last successful run's code + 1
gh run list --workflow=android-internal-release.yml --status success --limit 1 --json databaseId
gh run view <id> --log | grep -m1 'Resolved versionName='   # -> name + code

# trigger on main
gh workflow run android-internal-release.yml --ref main \
  -f version_name=<name> -f version_code=<code>

# find + watch the new run, then verify
gh run list --workflow=android-internal-release.yml --limit 1 --json databaseId
gh run watch <new-id> --exit-status
gh run view <new-id> --log | grep "Successfully finished the upload to Google Play"
```

The workflow also auto-deploys on `v*` tag pushes, so
`git tag v1.8.0 && git push origin v1.8.0` is an alternative trigger.
