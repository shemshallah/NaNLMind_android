# NaN16 Android N16T guest ABI

The Android app owns one persistent `NanVm` for the protocol session. It does not recreate the VM between frames: the NaN16 guest keeps session transition state, session identity, and the inbound replay cursor in its own memory.

## Build products

The Android Gradle build compiles:

- `src/nannet/n16t_android_guest.nas` → `assets/n16_protocol.nan`
- the assembler's `LABELS` output → `assets/n16_protocol.labels`

The label map is generated from the same source as the guest image. Kotlin resolves entry-point names from this file; instruction addresses are not hard-coded.

## Public entry points

The host sets the indicated registers, sets the program counter to the matching label, and runs until the guest reaches its terminal `HALT`.

| Entry | Input registers | Result |
|---|---|---|
| `n16_android_build` | r1 output byte address, r2 message type, r4 payload byte address, r5 payload length, r6 16-byte session-id address | r1 frame length, r2 error, r3 status; the guest assigns the sequence |
| `n16_android_advance` | none; commits the guest's pending outbound message | r1 state, r2 error, r3 status |
| `n16_android_route` | r1 frame byte address, r2 complete frame length | r1 gateway, r2 interface, r3 status, r4 logical point |
| `n16_android_abort` | none; abandons an unsent pending frame | r3 status |
| `n16_android_reset` | none | clears guest replay/session state, transition state and outbound sequence |

The `_impl` labels are ordinary NaN16 subroutines used by the guest regression test. Android calls the public `n16_android_*` entry labels, which return by halting the bounded guest call.

## Byte-memory mailbox

These are byte addresses in the VM's 4 MiB guest byte-address space. They are deliberately separated from the loaded program and from each other.

| Region | Byte address | Capacity |
|---|---:|---:|
| N16T frame input/output | `0x00100000` | 65,572 bytes |
| Payload staging | `0x00120000` | 65,536 bytes |
| Session-id staging | `0x00140000` | 16 bytes |

N16T frames have a 32-byte header, up to 65,536 payload bytes, and a 4-byte CRC32 trailer. Kotlin copies byte arrays to/from these regions; it does not parse or generate N16T fields.

## Exchange order

1. `buildFrame` invokes the guest frame builder. NaN16 assigns and reserves the next outbound sequence, but does not yet advance session state.
2. The HTTPS adapter posts the complete opaque frame.
3. Only after the HTTPS exchange succeeds, `commitSent` invokes the guest transition routine and advances the guest-owned outbound sequence. On transport failure, `abortPending` clears the reservation without consuming the sequence.
4. `acceptFrame` stages the peer frame and invokes the guest validator/router. Rejected frames do not consume the guest replay cursor or session ownership. Terminal `CLOSE` uses the one-way `closeRemote` path rather than expecting a routable peer frame.
5. The same VM remains alive for the next exchange.

The host supplies payload bytes and random session-id entropy. NaN16 assigns outbound sequence numbers and owns frame encoding, CRC, pending-send transactions, session transitions, replay checks, payload bounds and route selection.

## Deployment boundary

This ABI and the HTTPS byte adapter are client-side primitives. A reachable authenticated gateway, server-side guest/runner response path, candidate evaluation and transactional model promotion are separate work. A successful APK build is not evidence of a live remote training service or a device installation test.
