# Security

## Never ship DEEPREPO_PAT

This is an Android app/repository. An APK can be unpacked. **Do not put
`DEEPREPO_PAT`, a GitHub token, the bootstrap ring secret, or any long-lived
credential in the APK, source, resources, preferences, or build output.**

## Intake credential

The Android node submits route diffs to the intake endpoint using a scoped,
short-lived, revocable `NANLM_DEVICE_TOKEN`. That token:

- is not a GitHub PAT;
- cannot access the ring repos or releases;
- is limited to intake/route submission;
- expires and can be revoked independently.

The client reads `NANLM_DEVICE_TOKEN` from the process environment. It never
reads `DEEPREPO_PAT`.

## Build hygiene

- `.nanlmind/` is an external clone and is gitignored.
- `state/`, `.env`, token files, and keystores are gitignored.
- Do not log the token or include it in route-diff JSON.
- Revoke first if a token leaks; rotation alone is not sufficient.


## Local key files

The limited key at `Documents/apikeys/NANLMIND_ANDROID.txt` is a build/CI
credential. It must stay outside this repository and outside the APK. Do not
copy it into `config/`, `scripts/`, resources, or generated assets. The Android
app uses only the scoped `NANLM_DEVICE_TOKEN` for intake.
