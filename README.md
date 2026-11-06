# NaNLMind_android

Android/Termux edge node for NaNLMind.

## Scope

This repository runs **one 8 MiB universe only**.

```text
8 MiB universe
256 heads
node point 2
point 1 local control portal
route-diff client
NO 2 GiB over-bootstrap map
```

The 2 GiB map belongs only to the bootstrap universe in
[`NaNLMind`](https://github.com/shemshallah/NaNLMind), maintained by the
six-runner bootstrap ring. Android is an edge/peer universe; it does not load
the bootstrap map.

The six-runner ring instantiates 256 heads of the 8 MiB bootstrap universe. It
does not instantiate 256 child charts.

## Build

```sh
pkg install clang make git python
scripts/build_termux.sh
```

The core is cloned into `.nanlmind/` on first build. The Android repository is a
thin build/route wrapper; the address algebra lives in the core.

## Run

```sh
make bootstrap
make verify
```

`make bootstrap` runs the Android byte bootstrap on the 8 MiB universe.

## Routing

The default control point is **1** (`0x0001`), a non-head portal. Point **256**
(`0x0100`) is the alternate mapping. Point 128 is not special.

The node can write a local route diff for the bootstrap ring:

```sh
make route-diff
```

No 2 GiB map is created, uploaded, or cached here.

## Endpoint observation transport

The Android edge node can submit a version-1, hardware-neutral observation to
the shared NaNLMind intake gateway. The gateway assigns `my_android` from the
scoped device token; the payload cannot choose its own endpoint identity or
claim a verified outcome.

Set the gateway base URL and the scoped token in the Termux session (never in
the APK, source tree, or route-diff artifact):

```sh
export NANLM_INTAKE_URL="https://YOUR_GATEWAY"
export NANLM_DEVICE_TOKEN="(revocable device token)"
```

Create an observation envelope from the local runner's actual trace summary,
then submit it:

```sh
make submit-observation EVENT=state/observation.json
```

The event contains only its SHA-256 event ID, base generation, and active
head indices. The client rejects caller-supplied correctness labels and uses
Python's standard library, so it is not tied to ARM, x86, Android API level,
or an accelerator. The gateway queues the observation; it does **not** mean
the canonical 256-head state has been updated. The runner ring must validate
and ingest it before it can affect Overbootstrap.

## Security

`DEEPREPO_PAT` is never used here and must never be shipped in the APK or kept on
the device. Android/community nodes use a scoped, revocable
`NANLM_DEVICE_TOKEN` against `NANLM_INTAKE_URL`. The build/CI key file in
`Documents/apikeys/` is never copied into this repo or the APK. See
[SECURITY.md](SECURITY.md).

## Why separate

The Android build and transport are different from the x86 bootstrap ring. The
core remains shared; this repository only carries the Android target, route
client, and Termux scripts.

See [docs/ROUTING.md](docs/ROUTING.md).
