# Android routing

## Node shape

```text
8 MiB universe
256 palindromic heads
point 1 control portal (0x0001)
point 256 alternate (0x0100)
NO 2 GiB over-bootstrap map
```

Point 1 is an off-diagonal portal, not a head. It routes head requests to local
bins and to the bootstrap ring.

## Bootstrap ring

The bootstrap ring in `NaNLMind`:

- instantiates the 256 heads of the 8 MiB bootstrap universe;
- maintains the bootstrap-only 2 GiB system-slot map;
- accepts Android route diffs;
- returns a logical stamp and next generation.

The Android node never receives or stores the 2 GiB map.

## Diff

`scripts/route_diff.py` writes a small local JSON artifact. It has no raw text.
The bootstrap ring validates `base_generation` before merging. A stale diff is
rejected and retried after rebasing.

## Boundary

- Android: 8 MiB universe + local route cache.
- Bootstrap ring: 8 MiB universe + 2 GiB map.
- Peers: 8 MiB universes.
- No macrocluster storage exists yet.
