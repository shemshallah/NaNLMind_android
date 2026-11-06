# NaNLMind_ANDROID desktop and host boundary

## Desktop boot contract

Android boots the patched NaN0S image directly into the GUI desktop. The museum catalog is not an Android navigation destination. The Android shell mounts one desktop surface and opens NSH as a child window over that surface at boot. NSH can be hidden/restored without stopping the guest VM; closing a child window returns to the same desktop.

The desktop child-window manager provides title bars, drag, minimize, maximize/restore, and close. NSH, Settings, NaNNet, Training, N16T Gateway, Browser launcher, COM1 Log, filesystem utility, and applications menu use the same internal window layer. Android document pickers and the external browser remain Android-owned system surfaces.

## NSH commands

The boot terminal is the guest's NaN16 window (kind 0), opened on the desktop automatically. It prints the same command reference shown by `--help`. `exit`, `quit`, or `q` closes only this NSH window; the desktop and VM continue running.

Implemented commands:

- `--help`, `h`: print the command reference.
- `c`, `v`, `w`: clear the terminal, show version, report guest window count.
- `l`, `m`, `x`, `t`, `u`: open Lattice, Monitor, Hex, raise the same NSH window, and guest utilities.
- `s`, `n`, `b`: open the unified Settings, NaNNet manager, and isolated Browser Launcher as desktop child windows.
- `nannet-training`: open the same NaNNet Training interface as the desktop button.
- `n16t`: open the N16T Gateway as an internal desktop child window.
- `files`: open the internal `/nsh` file manager.
- `write FILENAME`: open an internal text editor for a file in `/nsh`. Filenames are limited to 64 ASCII letters, digits, dot, dash, or underscore; path traversal and subdirectories are rejected.
- `f`: raise the existing NSH window rather than creating a second terminal.
- `exit`, `quit`, `q`: close only NSH, not the desktop.

The `/nsh` workspace is app-private Android storage, not the guest NaNFS volume. The file manager and editor are Android host-service windows opened only when NaN16 emits a bounded command request. Corpus content is treated as data and is never executed as instructions.

The Android host shell is a UI/OS boundary. Guest applications, NaN16 protocol state, corpus validation, and training calculations remain NaN16 guest responsibilities. The Android shell does not execute corpus text as commands.

## Function keys and guest windows

A 12-key F1-F12 strip is drawn by NaN16 at the top of the desktop, with a matching Android soft-key row. The host routes F5/F7 to the one unified Settings window and F6 to NaNNet; other keys are dispatched to guest windows. F2 is move mode, F3 enlarges/restores, and Escape closes the focused guest child window. F8-F10 launch Lattice, Monitor, and Hex.

Guest window hit-testing, z-order, drag, close, and maximize remain NaN16-owned through the pointer mailbox. Android sends pointer coordinates/actions only. The Android child-window layer separately provides desktop windows for host services such as the text editor, Wi-Fi manager, training interface, and gateway.

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

The Android Activity does not create an Android-native NSH terminal. The only terminal visible at boot is the guest desktop's NaN16 window (kind 0), and the guest shell reloads the desktop rather than the museum catalog on return. F5 and F7 are host shortcuts for the same unified Settings window; F6 opens the Android-backed NaNNet manager. No parallel guest Settings window is exposed through the normal desktop controls.
