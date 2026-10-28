# YATA sync and backup setup

Use this guide to connect YATA to storage you control, add another device, and recover from a
remote snapshot. Core task management works without any sync provider.

[User guide](USER_GUIDE.md) · [Feature reference](FEATURES.md) · [Documentation index](README.md)

## Contents

- [Choose a storage method](#choose-a-storage-method)
- [Before connecting](#before-connecting)
- [GitHub setup](#github-setup)
- [Add another device](#add-another-device)
- [SFTP setup](#sftp-setup)
- [FTPS and FTP setup](#ftps-and-ftp-setup)
- [Scheduling and sync behavior](#scheduling-and-sync-behavior)
- [Encryption and credentials](#encryption-and-credentials)
- [Recovery and restore](#recovery-and-restore)
- [Troubleshooting](#troubleshooting)

## Choose a storage method

| Method | What it provides | What to retain |
| --- | --- | --- |
| **Portable file backup** | A full JSON file you save through Android's picker | The file in storage accessible without this phone |
| **Local backup** | Recovery copies encrypted with this installation's Android Keystore key | The installation and device key; also keep a portable backup |
| **GitHub** | Encrypted snapshot sync with commit-history restore points | Repository access, token and backup passphrase |
| **SFTP** | Encrypted snapshots over SSH, with rotated server backups | Server details, credentials, host fingerprint and backup passphrase |
| **FTPS / FTP** | Encrypted snapshots on an FTP server, with rotated backups | Server details, credentials and backup passphrase |

Sync merges changes between installations. A point-in-time backup preserves a separate recovery
copy. Because sync includes deletions, use an independent portable backup as well.

SFTP and FTPS encrypt the connection. Plain FTP does not protect login credentials in transit,
even though YATA encrypts the task snapshot itself. Prefer SFTP or FTPS when available.

## Before connecting

1. Update all participating installations to compatible current builds. Current backups use
   **format 5**; earlier apps cannot read that format.
2. Leave demo mode. The real-data safety gate pauses backup, sync, restore and exports in demo mode.
3. On every device that already holds useful data, create a portable file backup through
   **Settings → Backup & Sync → Backup YATA to File**.
4. Choose one remote location for this set of devices. Do not mix unrelated task databases in it.
5. Choose a strong **backup encryption passphrase** and retain it outside the phone. Every device
   sharing that remote needs the same passphrase.

Do not begin by restoring or clearing a device with useful local changes. A restore chooses a
remote copy as authoritative; normal sync attempts to merge supported changes.

## GitHub setup

### 1. Create a dedicated repository

Create a private repository in your GitHub account, for example `yata-sync`. Initialize it with
a README so its branch exists, and note the owner, repository name and branch. A dedicated
repository keeps task snapshot history separate from source code and other applications.

YATA uses `yata/snapshot.json` on the configured branch. Despite the `.json` path, current remote
payloads are passphrase-encrypted. Git history provides older snapshot restore points. Avoid
manually editing that file or rewriting the branch's history.

### 2. Create a fine-grained token

YATA's token-field information button also shows these steps:

1. Open GitHub **Settings → Developer settings → Personal access tokens → Fine-grained tokens**.
2. Choose **Generate new token** and give it a recognizable name such as “YATA sync”.
3. Choose the resource owner that owns the sync repository.
4. Under repository access, select **only** the dedicated sync repository.
5. Under repository permissions, set **Contents → Read and write**.
6. Choose an expiration you can maintain, generate the token, and copy it into YATA.

Repository/organization policies can add approval or access requirements. YATA shows token
expiry information when GitHub supplies it. Keep track of expiration so you can replace the
token before background sync starts failing. Never place the token in an issue or screenshot.

### 3. Enter the configuration

Open **Settings → Backup & Sync → Remote sync**, choose **GitHub**, and fill in:

| Field | Example or instruction |
| --- | --- |
| **Token** | The fine-grained token with access to this repository |
| **Repository** | `your-account/yata-sync`, using the actual owner and repository name |
| **Branch** | Your existing branch, commonly `main` |
| **API base** | Keep the default `https://api.github.com` for GitHub.com |
| **Backup encryption passphrase** | The passphrase all participating devices will use |

Use an alternative API base only when you know the endpoint required by your supported server
configuration. A web repository URL is not an API base.

Tap **Connect GitHub** and inspect the result. A successful connection checks access; then run
sync and wait for a successful terminal status. Review the activity/history information and
confirm a snapshot was published before configuring the next device.

### 4. Review the first merge

When the remote already contains data, YATA can show a first-sync summary before applying it.
Review the counts and confirm only if these are the records you intend to combine.

The merge uses the local version, the remote version and a last-known baseline when one exists.
With a baseline, changes to different fields can be combined, while a direct same-field
conflict uses the remote value. A first join has no baseline and resolves matching records
differently. Keep backups and avoid competing edits to the same fields on several devices.

## Add another device

1. Install a compatible current YATA version on the second device.
2. Back up any existing data there and leave demo mode.
3. Configure the **same repository, branch and backup passphrase**. A separate least-privilege
   token for the new device can make access easier to revoke independently.
4. Connect/test and run the first sync. Review any join summary.
5. Confirm expected tasks, projects, lists, tags, people and photos appear.
6. Make a small, recognizable task edit and manually sync. Sync the first device and confirm
   the edit arrived before relying on scheduled transfers.

### Encrypted GitHub configuration transfer

The GitHub configuration screen offers export/import actions for moving setup to another
device. The transfer file contains encrypted repository details, token and backup passphrase;
protect it with the requested **transfer password**. It is separate from a task backup.

Export on the configured device, move the file through a private channel, then import it on the
new device with the transfer password. Review the imported settings, connect and sync.
The transfer password protects this configuration file; the backup passphrase protects remote
task snapshots. Retain the appropriate one for each purpose, and remove unneeded transfer
copies once setup is complete.

## SFTP setup

Choose **SFTP** on the remote sync screen and enter the host, port (normally 22), username and
remote directory. Choose password or private-key authentication as provided by your server;
enter the key passphrase if the key needs one. Set the backup encryption passphrase too.

1. Verify the directory is dedicated to this YATA data set and is writable by the configured user.
2. Run the connection test.
3. On the first connection, compare the presented host-key fingerprint with the fingerprint
   supplied by your server administrator or trusted server configuration.
4. Trust/pin the key only when it matches, then finish the authentication/write test.
5. Run a manual sync and check its terminal result before enabling scheduled backups.

Other devices need the same remote location and backup passphrase. A host-key change should
be investigated before trusting a replacement key, even when the hostname is unchanged.

SFTP/FTP transports use a remote sync lock to coordinate updates. Clear a lock only after
confirming no device is currently syncing. GitHub uses checked branch updates instead and
does not need this lock.

## FTPS and FTP setup

Choose the FTP provider and enter host, port (normally 21), username, password and remote
directory. Enable the FTPS option when supported by your server and use the port/mode your
server actually provides. Set the common backup encryption passphrase.

Test the connection and remote write access, then run a manual sync. If the connection works
but transfers fail, verify server permissions, TLS configuration and the network's handling
of FTP data connections. Avoid weakening server security to bypass a certificate or TLS problem.

FTPS is FTP secured with TLS; SFTP is a different protocol over SSH. Selecting one does not
make a server for the other compatible. Use the protocol the server is configured to accept.

## Scheduling and sync behavior

Backup & Sync offers after-change behavior and periodic choices such as 30, 60 and 120 minutes.
The after-change path is debounced; it does not publish a separate snapshot for each keystroke.
Android's background scheduling, connectivity and battery policy can delay actual execution.

- Manual sync requests an immediate attempt and displays progress/result information.
- Scheduled work skips writing when the canonical data has not changed since the last
  effective scheduled run.
- GitHub publishes checked, fast-forward branch updates and retries compatible concurrent
  update conflicts.
- SFTP/FTP retain rotated remote backup files; GitHub retains snapshot versions through commits.
- Deletions are part of sync. Missing local data after a restore/reinstall needs special review.
- Device-specific credentials, sync checkpoints, app-lock material and running timer state are
  not treated as ordinary portable task preferences.

After an important session on one device, manually sync it before opening and editing the same
work elsewhere. Check final status when reliability matters; a progress indicator is not proof
that the remote write finished.

## Encryption and credentials

Remote snapshots require a passphrase. The envelope starts with `YATAENC1` and uses AES-256-GCM
with a random salt and IV. The key is derived from the passphrase with PBKDF2-HMAC-SHA256.
Authentication rejects altered/truncated data; the UI cannot distinguish a wrong passphrase
from every form of corrupted ciphertext.

On-device local backups use an Android Keystore key instead. That key is tied to the
installation/device and cannot simply be moved to decrypt a backup elsewhere. A portable file
backup is a separate route; do not assume every JSON export has remote-snapshot encryption.

Remote credentials are stored encrypted on the device. Ordinary data backups deliberately
exclude connection credentials and other device-local security state. Keep server access and
the passphrase available separately, or use the encrypted GitHub configuration transfer.

Changing a passphrase on one device does not automatically update the other devices. Before
changing remote encryption settings, retain a portable backup and the old passphrase needed
for older restore points. Repository access alone cannot recover encrypted task data if its
passphrase is lost.

## Recovery and restore

### Restore the latest remote copy

For GitHub, open **Remote sync → GitHub → Recovery → Use latest GitHub snapshot**. The prompt
explains that the remote copy replaces local tasks and portable app data. YATA creates a local
recovery backup first; if that backup fails, the restore does not proceed.

Use this when the remote is deliberately the copy you want the device to match. It differs
from merging local edits through ordinary sync. Verify the restored data and sync status
afterward. The restore also advances the device's sync checkpoint to the selected remote head.

### Earlier restore points

Use the restore-point/history controls available for the provider. Inspect a snapshot summary
before restoring. GitHub points correspond to snapshot commits; SFTP/FTP use retained backup
files. When validating a bad head snapshot, the GitHub implementation can search bounded older
history for a valid recovery snapshot; this does not guarantee every historical file is usable.

### Unexpectedly empty local data

If a previously synced device suddenly has no tasks while the remote still has data, YATA
stops for review. This can happen after an installation or device restore loses the database
but retains pieces of prior sync state.

Choose **Restore from remote** if you want the remote records back. **Sync anyway** treats the
empty local state as intentional and can delete remote records too. Do not select it merely
to dismiss the warning. Keep a backup of any useful data before deciding.

### Rewritten GitHub history

YATA checks that the current head descends from the last head the device observed. A force-push
or branch replacement can break that history and cause sync to stop. Reconnect/review the
configuration, or explicitly use the latest GitHub snapshot if you trust that copy. Do not
try to resolve the warning by clearing all app data.

Git history can keep encrypted snapshots after tasks are deleted from the newest version.
Treat access to the repository and old passphrases accordingly.

## Troubleshooting

| Symptom | Check or next step |
| --- | --- |
| Sync/backup controls disabled | Leave demo mode; check that no transfer is still active |
| Repository not found / 404 | Exact owner/name, token access to that repository, organization approval and branch |
| Public repository rejected | Change it to private or connect a dedicated private repository; YATA requires private GitHub sync storage |
| Unauthorized / expired token | Replace or renew the token and reconnect; review expiry information |
| Permission denied / 403 | Contents read/write, repository access, organization policy, branch rules or API rate-limit message |
| Wrong passphrase or corrupt backup | Use the passphrase that encrypted that snapshot; try a known-good retained copy |
| Branch conflict | Let the checked update retry, then retry manually if another device was writing |
| History rewritten | Review branch changes and choose reconnect/recovery deliberately |
| Local unexpectedly empty | Restore from remote if you want those tasks back; avoid Sync anyway unless deletions are intended |
| SFTP host fingerprint changed | Verify the server's new fingerprint through a trusted channel |
| SFTP/FTP lock remains | Confirm all transfers have stopped before clearing a stale lock |
| Scheduled sync is late | Check network, Android battery/background restrictions and the configured schedule; try manual sync |
| New backup rejected on old app | Update to a version that understands backup format 5 |
| Connection succeeds but tasks do not appear | Complete an actual sync, inspect its result, and confirm both devices use the same location/branch/passphrase |

Review sync history and the exact error before changing settings. A failed authentication
attempt needs corrected credentials; repeated retries do not grant access. For support, include
app/Android versions, provider, timing and a sanitized error. Never attach tokens, private keys,
passwords, passphrases or the encrypted configuration transfer file to a public issue.
