# NaNLMind_ANDROID desktop and host boundary

## Desktop boot contract

Android boots the patched NaN0S image directly into the GUI desktop. The museum catalog is not an Android navigation destination. The Android shell mounts one desktop surface and opens NSH as a child window over that surface at boot. NSH can be hidden/restored without stopping the guest VM; closing a child window returns to the same desktop.

The desktop child-window manager provides title bars, drag, minimize, maximize/restore, and close. NSH, Settings, NaNNet, Training, N16T Gateway, Browser launcher, COM1 Log, filesystem utility, and applications menu use the same internal window layer. Android document pickers and the external browser remain Android-owned system surfaces.

## NSH commands

Type `--help` in NSH for the live command reference. The command dispatcher supports:

- File operations under the app-private `/nsh` sandbox: `pwd`, `ls`, `dir`, `files`, `write`, `touch`, `cat`, `type`, `read`, `mkdir`, `rm`, `del`.
- Desktop applications: `apps`, `run lattice|monitor|hex|settings|nannet|system|training|gateway|browser`, `windows`, `wm`.
- System windows: `settings`, `wifi`, `nannet`, `nannet-training`, `n16t`, `gateway`, `browser`, `log`, `disk`, `fs`.
- Display and lifecycle: `fit`, `scale`, `system`, `desktop`, `reset`, `version`, `ver`, `clear`, `cls`, `exit`, `quit`.

The Android host shell is a UI/OS boundary. Guest applications, NaN16 protocol state, corpus validation, and training calculations remain NaN16 guest responsibilities. The Android shell does not execute corpus text as commands.

## Function keys and guest windows

The on-screen row injects actual PC keyboard scan codes, not synthetic extended-key values:

| Key | Android action |
|---|---|
| F1 | Guest F1 |
| F2 | Guest move mode |
| F3 | Guest enlarge/restore |
| F5 | Unified Android desktop settings |
| F6 | NaNNet manager |
| F7 | Guest system utilities |
| F8 | Guest Lattice |
| F9 | Guest Monitor |
| F10 | Guest Hex |
| F11/F12 | Guest function-key input |

Touching guest window title-bar controls is translated into the same Escape/F3 guest actions. The guest window manager itself is NaN16; the Android layer only maps touch coordinates to those existing guest key actions.

## NaNNet network model

Android owns the physical Wi-Fi and cellular radios. The NaN16 guest must not enumerate a fictional PCI Wi-Fi card. The Android NaNNet manager reads the active Android network route through ConnectivityManager, reports Wi-Fi/cellular/Ethernet and validated internet status, and uses the active Android route for HTTPS. Android chooses LTE/5G radio mode; the app does not force carrier radio selection.

Wi-Fi scan results are rendered inside the NaNNet manager. Android may throttle scans or mask SSIDs; the manager therefore displays cached results and the current association where available. Android 10+ controls Wi-Fi join approval through its network-suggestion flow.

## N16T gateway

The human-facing portal is `GET /portal`. Binary protocol frames use `POST /n16t` with `application/x-n16t`. If a user enters the portal URL into the gateway window, the client normalizes the path to `/n16t`; it does not POST binary frames to the HTML portal. A 2xx HELLO/CAPABILITIES exchange proves only protocol reachability, not corpus upload, candidate evaluation, or merge deployment.

## NaN16-first build and execution

The Android-specific guest image is patched from the pinned NaN0S source revision by `tools/android_guest_patch.pl` and assembled by the NaN0S AWK/NaN16 build tools. The Android APK workflow no longer uses Python for guest-image patching. No new C training or model semantics are introduced. The existing C/JNI code remains the interpreter and narrow host-device boundary needed to execute NaN16 on Android.

## Current training boundary

The current Android training guest validates N16TXT/1 input and produces a temporary byte-frequency/byte-bigram candidate snapshot. It restores the active desktop VM afterward. This is not yet a neural optimizer, remote corpus-upload client, candidate quality evaluator, or transactional merge pipeline. The UI labels this boundary explicitly and does not claim a remote training merge is complete.


## Android host-shell correction (2026-11-06)

The Android Activity must not create, display, or launch an Android-native NSH terminal. The only terminal visible at boot is the guest desktop's NaN16 window (kind 0). The host Activity is limited to VM display/input and Android hardware/service boundaries. The desktop's F7 shortcut is routed into the guest and opens the guest Settings window; it must not be intercepted by an Android settings panel.
