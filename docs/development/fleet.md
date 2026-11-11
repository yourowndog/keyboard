# Fleet and Infrastructure

> Status: Canonical
> Last verified: 2026-11-10
> Verified against: live `node_status` and `run_command` against sisyphus,
> sisyphus `~/.ssh/config`, and installed toolchain on sisyphus

The machines OmniBoard work runs on. **Check this document's node facts against
the live fleet before relying on them.** Stale infrastructure notes in this
repository once cost real time — see [Changes](#changes).

## Nodes

Addresses are Tailscale IPs, from sisyphus `~/.ssh/config`. Short aliases are
the second column of each `Host` line.

| Node | Alias | Address | Role |
|---|---|---|---|
| sisyphus | `sy` | 100.90.131.29 | **Build machine.** JDK 17 + Android SDK + adb. Also `sisyphus-lan` / `syl` at 10.0.0.41. |
| sleeper | `s` | 100.66.224.88 | General workhorse. **Do not build here** — small disk, build output fills it. |
| weakling | `w` | 100.109.145.90 | Took over the retired Beksinski droplet's duties. |
| titan | `t` | 100.104.232.94 | |
| pyrrhus | `p` | 100.81.181.62 | |
| hannibal | `h` | 100.116.129.104 | |
| tungsten | `tu` | 100.74.204.19 | |
| icarion | `i` | 100.101.185.66 | Daily-driver Android phone. Device-validation target. |

Reach them through the Brokentooth Operator tooling (`node_status`,
`run_command`, `process_start`). For builds and tests use node `sisyphus` with
`execution_class=build`.

## Building on sisyphus

Checkout: `/home/sam/projects/keyboard`, tracking `origin` only. There is **no
build-factory git remote**; sisyphus builds from its own checkout, so pull
before building.

Toolchain, confirmed present:

- JDK 17 (Temurin 17.0.21) at `~/.local/share/android-build-tools/jdk-17`,
  already `JAVA_HOME`.
- Android SDK at `~/.local/share/android-build-tools/android-sdk`, already
  `ANDROID_HOME` and `ANDROID_SDK_ROOT`.
- adb 1.0.41, symlinked to `/usr/local/bin/adb` (and `fastboot` likewise) so it
  resolves from any environment.

  `~/.bashrc` already put `$ANDROID_HOME/platform-tools` on `PATH` for both
  interactive and `bash -c` shells, so adb was always findable from a shell.
  What failed was the fleet `android_devices` helper, which execs without a
  profile and so sees only the bare `/usr/local/sbin:/usr/local/bin:/usr/bin`.
  Hence the symlink into `/usr/local/bin` rather than another `PATH` export.

`local.properties` is absent and the debug build does not need it: the SDK
comes from the environment. It is still where machine-local voice settings
(`OPENAI_API_KEY`, `WHISPER_MODEL`) would go.

A long build should be started with `process_start`, not `run_command`, which
is capped at 120 seconds.

## Installing to icarion

icarion is reachable over Tailscale and accepts an adb TCP connection on 5555,
but authorization is a physical step:

```bash
"$ANDROID_HOME/platform-tools/adb" connect 100.101.185.66:5555
```

If this reports `failed to authenticate` and `adb devices` shows the device as
`unauthorized`, the phone is waiting for someone to accept the host's RSA
fingerprint on its screen. That cannot be done remotely. Once accepted, install
with `adb -s 100.101.185.66:5555 install -r <apk>`.

Remember that replacing the APK does not restart a running IME process. See
[Snygg hard-won lessons](../theming/hard-won-lessons.md#verify-the-process-not-just-the-file)
before concluding a theme change had no effect.

## Changes

Newest first. Record infrastructure changes here when they happen; this section
exists because the last one was not recorded for about six months.

- **2026-11-10** — Documented the fleet for the first time. Corrected
  `AGENTS.md`, `README.md`, `docs/development/building.md` and
  `training/README.md`, all of which still described the Beksinski factory.
- **~2026-06** — **beksinski retired.** The droplet is gone. Its duties moved to
  weakling. The `factory` git remote (`beksinski:/home/silo/git/omniboard.git`)
  and the "push a branch to build" workflow no longer exist. This was not
  recorded at the time, and the documentation continued to describe Beksinski
  as the build path for about five months.

  Dated from harvested typing on 2026-06-23 in which Sam describes wanting to
  stop paying for the droplet and migrate the server onto a cluster node. That
  is firmer than recollection ("like 6 months ago"), but it is the date of the
  intent, not of confirmed decommissioning.
- **(date unknown)** — Builds moved from sleeper to sisyphus, because build
  output repeatedly filled sleeper's small disk and sisyphus was underused.

## Needs attention

- The Beksinski retirement date is inferred from harvested typing, not from a
  decommissioning record.
- No OmniBoard cluster-specs document was found anywhere on the fleet; this
  file is the first. If an older one surfaces, reconcile rather than duplicate.
- Only sisyphus and icarion have been checked live. The roles of titan,
  pyrrhus, hannibal and tungsten are unrecorded, and weakling's inherited
  duties are described only in general terms.
- `docs/history/devlog.md` still references pulling `OPENAI_API_KEY` from
  `silo@beksinski`. That is historical and correct as history, but the current
  source of that key is unrecorded.
