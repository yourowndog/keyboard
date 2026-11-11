# Fleet and Infrastructure

> Status: Canonical for OmniBoard's use of the fleet only
> Last verified: 2026-11-11
> Verified against: live `run_command` against sisyphus and sleeper, the
> installed toolchain on sisyphus, and `~/ops/cluster-specs` on sleeper

How OmniBoard work uses the fleet. **This is not the fleet's documentation.**

## The fleet is documented elsewhere

Node inventory, addresses, roles and infrastructure history live in
`~/ops/cluster-specs` on sleeper (git origin `/srv/shared/git/cluster-specs.git`,
private mirror `github.com/yourowndog/cluster-specs`). Read that first:

- `CURRENT.md` — operational truth
- `CHANGELOG.md` — infrastructure history; record cluster changes here
- `nodes/sisyphus.md`, `SISYPHUS-BUILD-WORKER.md` — the build host
- `ICARION-REMOTE-ADB.md` — the phone's remote ADB setup

Do not copy node tables or addresses into this repository. An earlier revision
of this file did, and it immediately drifted from the canonical source while
publishing fleet addresses in a public repo. Keep OmniBoard-specific procedure
here and link out for everything else.

Reach the fleet through the Brokentooth Operator tooling (`node_status`,
`run_command`, `process_start`). For builds and tests use node `sisyphus` with
`execution_class=build`; `run_command` is capped at 120 seconds.

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

**Install from sleeper, not sisyphus.** ADB pairing keys belong to the user and
host that created them, and sleeper's are the ones icarion has authorized.
sisyphus can build but reports `unauthorized` against the phone, which is not
something to work around remotely — it would need someone to accept a
fingerprint on the phone's screen.

Arm the connection with `adbw2` on sleeper, which finds Wireless debugging over
Tailscale and re-arms legacy TCP/IP 5555. Note it uses a per-user ADB server
port, not the shared 5037, because the server holds only its owner's keys:

```bash
adbw2
```

Then copy the APK from the build host and install, with `$ICARION` set to the
phone's tailnet address from cluster-specs. Push from sisyphus rather than
pulling from sleeper — see the sandbox note below.

```bash
scp app/build/outputs/apk/debug/app-debug.apk sleeper:/home/sam/omniboard.apk
```

```bash
ADB_SERVER_SOCKET="tcp:localhost:$((15000 + $(id -u) % 50000))" \
  adb -s "$ICARION:5555" install -r /home/sam/omniboard.apk
```

Transfer speed depends on the Tailscale path. When `tailscale status` shows
`relay "dfw"` rather than a direct connection, a ~150 MB debug APK takes tens of
minutes; `tailscale ping` reporting several hundred milliseconds and "direct
connection not established" is the signal. Getting the phone onto the same
network as sleeper is far faster than waiting out a relayed push.

See `ICARION-REMOTE-ADB.md` in cluster-specs for the full setup.

Remember that replacing the APK does not restart a running IME process. See
[Snygg hard-won lessons](../theming/hard-won-lessons.md#verify-the-process-not-just-the-file)
before concluding a theme change had no effect.

## Why this repository's build docs went stale

Worth keeping, because the failure is easy to repeat.

`beksinski` was decommissioned on **2026-08-03** (cluster-specs records it in
`BEKSINSKI-DECOMMISSION-2026-08-03.md`). The fleet's own documentation was
updated. This repository's was not: `AGENTS.md`, `README.md`,
`docs/development/building.md` and `training/README.md` went on describing a
`factory` git remote on a destroyed droplet for about three months, and
`building.md` carried a confident `Last verified` stamp over it the whole time.

Because `AGENTS.md` is the first file every agent session reads, each new
session inherited the wrong build host and had no reason to doubt it. Meanwhile
the global `~/AGENTS.md` on the fleet had said "Sisyphus builds" all along — the
doctrine was never wrong, only this repository's copy of it.

Two lessons, both now reflected above: infrastructure facts belong in
cluster-specs and are linked from here, never copied; and a verification stamp
on a document is worth nothing unless someone re-checks the claim against a live
system.

## Needs attention

- The adb/fastboot symlinks into `/usr/local/bin` on sisyphus are a node change
  that has not been recorded in `~/ops/cluster-specs/CHANGELOG.md`. That repo's
  worktree had uncommitted work in it at the time, so the entry was left for
  someone who can commit it safely.
- The Brokentooth Operator sandbox differs per node, and that shapes what an
  agent can do where. On sleeper it runs in a user namespace mapping only uid
  1000 (`/proc/self/uid_map` is `1000 1000 1`) with `NoNewPrivs` set, so every
  root-owned file — `/etc/passwd`, `/usr/bin/sudo`, `/etc/ssh/ssh_config.d/*` —
  appears owned by `nobody`, `sudo` refuses to run, and **ssh refuses its own
  system config**, failing with `Bad owner or permissions on
  /etc/ssh/ssh_config.d/20-systemd-ssh-proxy.conf`.

  That is a sandbox artifact, not a broken machine: ssh on sleeper works
  normally outside the operator. An earlier revision of this file reported it
  as a real fault on sleeper, which was wrong. Practical consequences: initiate
  scp from the other end, and do not expect to make privileged changes on
  sleeper through the operator. sisyphus's sandbox does allow `sudo`.

  Operator commands on sleeper also run with a private `/tmp`, so a file
  scp'd to `sleeper:/tmp/...` is invisible to them. Copy into `/home/sam/`
  instead when an operator command must read it. For GitHub, sleeper's
  `gh` is signed in, and `git -c credential.helper='!gh auth git-credential'
  push https://github.com/yourowndog/keyboard.git ...` works where an ssh
  remote would not.
- `docs/history/devlog.md` still references pulling `OPENAI_API_KEY` from
  `silo@beksinski`. That is historical and correct as history, but the current
  source of that key is unrecorded.
