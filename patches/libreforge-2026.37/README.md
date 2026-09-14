# libreforge 2026.37 performance patch

These source files replace the matching files from the official libreforge
`2026.37` tag (`c0f55ec510025b9d0ea25cbc53c24dfa811f4346`).

The compiled shadow JAR is stored at `lib/libreforge-2026.37-shadow.jar` and is
the only source used for the `com.willfp:libreforge` dependency by this build.

The patch:

- indexes active effect blocks by trigger during holder refresh, avoiding a full
  active-effect scan on every dispatch;
- creates holder-specific dispatch data and placeholders once per unique holder;
- avoids the redundant `EffectBlock.tryTrigger` trigger-membership check after
  dispatch has already filtered or indexed the blocks;
- runs mutators once per effect activation instead of evaluating the same mutator
  chain once for checks and again for execution;
- evaluates dynamic random-chain weights once per selection and uses indexed loops
  for normal chains;
- rejects invalid Vortex numeric expressions and skips zero-distance pulls so
  `Entity#setVelocity` never receives NaN or infinite components;
- compiles static Drill filters and flags once per config load while preserving
  2026.37's dynamic placeholders in `blacklisted_blocks`;
- avoids repeated location/vector allocations and hardness lookups;
- preserves the existing `prevent_trigger` behaviour; the built-in Excavation
  enchant opts into `keep_events` so Bukkit events remain intact without
  redispatching the full libreforge `mine_block` chain;
- caps one activation at 64 additional blocks unless `max_blocks` is supplied;
- skips the redundant antigrief lookup before rejecting protected recursive
  `mine_block` dispatches.
