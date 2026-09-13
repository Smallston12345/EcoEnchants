# libreforge 2026.34 Drill performance patch

These source files replace the matching files from the official libreforge
`2026.34` tag (`58b5ce77fe6ef2ebfb7ab02a47b8aaef57304d8e`).

The compiled shadow JAR is stored at `lib/libreforge-2026.34-shadow.jar` and is
the only source used for the `com.willfp:libreforge` dependency by this build.

The patch:

- compiles static Drill filters and flags once per config load;
- avoids repeated location/vector allocations and hardness lookups;
- preserves the existing `prevent_trigger` behaviour; the built-in Excavation
  enchant opts into `keep_events` so Bukkit events remain intact without
  redispatching the full libreforge `mine_block` chain;
- caps one activation at 64 additional blocks unless `max_blocks` is supplied;
- skips the redundant antigrief lookup before rejecting protected recursive
  `mine_block` dispatches.
