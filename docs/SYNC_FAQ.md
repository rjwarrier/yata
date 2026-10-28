# Sync help: common questions and fixes

Use this page when YATA cannot sync or your tasks do not appear on another device.
It covers GitHub, SFTP and FTP. Start with the message shown in YATA's sync history.

In builds with sync help dialogs, a failed connection or manual sync opens **How to fix**
with steps for the problem. **Sync help online** opens this page. You can also open it from
the Remote sync screen. If setup finds an empty GitHub repository, add a README and connect again.

[Sync setup guide](SYNC_SETUP.md) · [User guide](USER_GUIDE.md) · [All documentation](README.md)

## Find your problem

- [First sync fails: “GitHub repository changed during sync”](#first-sync-fails-github-repository-changed-during-sync)
- [The same message appears after sync worked before](#the-same-message-appears-after-sync-worked-before)
- [“Repository not found” or “404”](#repository-not-found-or-404)
- [“Token is invalid or expired” or “401”](#token-is-invalid-or-expired-or-401)
- [“Permission denied” or “403”](#permission-denied-or-403)
- [“Rate limit reached” or “429”](#rate-limit-reached-or-429)
- [YATA says the repository is public](#yata-says-the-repository-is-public)
- [“Wrong backup passphrase” or a damaged backup](#wrong-backup-passphrase-or-a-damaged-backup)
- [Connection works, but tasks do not appear](#connection-works-but-tasks-do-not-appear)
- [Automatic sync is late or does not run](#automatic-sync-is-late-or-does-not-run)
- [Sync buttons are unavailable](#sync-buttons-are-unavailable)
- [YATA warns that this device is empty](#yata-warns-that-this-device-is-empty)
- [“GitHub branch history changed outside YATA”](#github-branch-history-changed-outside-yata)
- [SFTP or FTP connection problems](#sftp-or-ftp-connection-problems)
- [An older app cannot read the backup](#an-older-app-cannot-read-the-backup)
- [What should I send when asking for help?](#what-should-i-send-when-asking-for-help)

## Before you change anything

If your tasks are still on the phone, save a copy using **Settings → Backup & Sync →
Backup YATA to File**. Keep that file somewhere you can find again.

Do not uninstall YATA, clear its storage, delete the sync repository, or choose a restore just
to get rid of an error. A restore replaces the data on your phone. Normal sync tries to combine
changes from your devices, including deleted tasks.

### A few words explained

| Word | Simple meaning |
| --- | --- |
| Repository, or repo | The storage space in your GitHub account that YATA uses for sync |
| Branch | A named version of that storage space, usually called `main`; use the same one on every device |
| Commit | A saved change in GitHub; saving a README creates the first one |
| README | A text file usually called `README.md`; adding it gets a new GitHub repository ready for YATA |
| Token | A special access key that lets YATA use your GitHub repository; keep it secret |
| Backup passphrase | The password you chose to protect your saved task data; it is separate from your GitHub password and token |
| Remote | The copy stored on GitHub or your chosen server, rather than on your phone |

## First sync fails: “GitHub repository changed during sync”

**Check whether the GitHub repository is empty.** This message can appear on the first sync
when the repository has no saved files yet. It does not always mean another device changed it.

1. Sign in to GitHub in your browser and open the repository you chose for YATA.
2. If it has no files, look for **creating a new file** or **Add file → Create new file**.
3. Name the file `README.md`. Type a short line such as `My YATA sync storage`.
4. Choose **Commit changes** to save it. If GitHub offers **Add a README**, you can use that instead.
5. Note the branch name shown above the file list, often `main`.
6. In YATA, open **Settings → Backup & Sync → Remote sync → GitHub**. Check that the
   repository and branch match what you saw on GitHub. Keep the repository private.
7. Run manual sync again and wait for the final success message.

If you are creating a new repository, select **Add a README file** during creation.
You do not need to delete or recreate an existing repository to add a README.

If files already exist, use the checks below. The error alone cannot tell us its exact cause.

## The same message appears after sync worked before

Another device may have saved a change while this device was syncing.

1. Let any sync running on your other devices finish.
2. Wait a short while, then run manual sync on one device.
3. After it succeeds, sync the next device.

YATA already retries some of these clashes. If it keeps failing, check the repository and
branch settings and share the error details listed at the end of this page. Do not delete
GitHub files or force a restore to work around this message.

## “Repository not found” or “404”

This can mean the name is wrong **or** YATA does not have permission to see the repository.

1. Open the repository in your browser. Its address looks like
   `https://github.com/your-name/yata-sync`.
2. In YATA's Repository field, enter `your-name/yata-sync`, using your actual names.
3. Check the branch name against the name above GitHub's file list.
4. Check that the token allows access to this particular repository. Being able to open
   it in your browser does not prove that YATA's token can access it.
5. If the repository belongs to a company or team, its owner may need to approve access.

See [token setup](SYNC_SETUP.md#2-create-a-fine-grained-token) for the steps.

## “Token is invalid or expired” or “401”

The access key saved in YATA may have expired, been removed, or been copied incorrectly.

Create a replacement token using the [setup guide](SYNC_SETUP.md#2-create-a-fine-grained-token).
Give it access to the sync repository and **Contents → Read and write** permission.
Paste it into YATA's Token field, connect again, and run sync. Repeat on any device using
the old token. You do not need to change the backup passphrase just because a token expired.

## “Permission denied” or “403”

YATA may be able to see the repository but not save changes to it.

- Check the token's repository access and **Contents → Read and write** permission.
- If the error mentions a rate limit, follow the next answer instead.
- If this is a company or team repository, ask its owner about approval or rules that block
  direct changes to the selected branch. Do not turn off security rules without understanding them.

Keep the exact error message if these checks do not fix it.

## “Rate limit reached” or “429”

GitHub is asking YATA to slow down. Wait until the retry/reset time shown, if there is one,
then try again. Avoid repeatedly tapping Sync while waiting. A rate limit usually does not
require a new token or a new repository.

## YATA says the repository is public

A public repository can be seen by other people. YATA requires a **private** sync repository.
Open the repository's settings on GitHub and check its visibility. Make it private, or connect
a separate private repository intended for YATA. Do not use the public YATA source-code repository
as your personal sync storage.

## “Wrong backup passphrase” or a damaged backup

Use the **same backup passphrase** that was used when the remote copy was saved.
Check for typing mistakes or extra spaces. This is not your GitHub login password or token.

If another device still syncs successfully, check your setup there. GitHub access alone cannot
unlock data if the passphrase is lost. Changing the passphrase on one phone does not update
your other devices or unlock older copies.

If you know the passphrase is correct, the saved copy may be damaged. Keep any working local
data backed up before using an older restore point. See [recovery steps](SYNC_SETUP.md#recovery-and-restore).

## Connection works, but tasks do not appear

**Connect** checks access. It does not prove that your task data has finished syncing.

1. Run manual sync on the device that has the tasks. Wait for success.
2. Run manual sync on the second device. Review any first-sync question before continuing.
3. Check that both devices use the same provider, repository or server folder, branch,
   and backup passphrase. The tokens can be different if both have the right access.
4. If sync succeeds but a task still seems missing, check Search, Trash, Archive, and any
   filters that may hide it.

Use one small test task to check that a change reaches the other device before relying on sync.

## Automatic sync is late or does not run

Android can delay background work to save battery or because there is no network connection.
The selected interval is not an exact appointment.

Open YATA and try manual sync. If that works, check your sync schedule, internet connection,
and Android's battery/background settings for YATA. If manual sync also fails, use its error
message to find the matching answer here.

## Sync buttons are unavailable

Check whether YATA is in **demo mode** or a transfer is still running. Leave demo mode and let
an active transfer finish. Demo mode uses sample data and pauses real backup and sync operations.

## YATA warns that this device is empty

Stop and check which copy contains the tasks you want. This warning can appear after a reinstall
or phone restore.

If the tasks are safely stored remotely and you want them back on this phone, review
**Restore from remote**. **Sync anyway** can treat the empty phone as an intentional deletion
and remove remote tasks too. Do not choose it just to dismiss the warning.

Back up any useful local data first and read [the empty-device guidance](SYNC_SETUP.md#unexpectedly-empty-local-data).

## “GitHub branch history changed outside YATA”

The saved history on GitHub no longer follows the history this device previously saw.
Someone may have replaced the branch or changed older saved versions.

Keep a file backup of useful local tasks. Check with anyone who manages the repository before
reconnecting or restoring. **Use latest GitHub snapshot** replaces local data with the remote
copy; use it only after deciding that copy is the one you want. Clearing app storage is not a fix.

See [history and recovery](SYNC_SETUP.md#rewritten-github-history).

## SFTP or FTP connection problems

SFTP and FTP are different server types. Choose the type your server supports; changing the
name in YATA does not make the server support it.

| Message or problem | What to check |
| --- | --- |
| Cannot connect / connection timed out | Internet connection, server address, port, and whether the server is running |
| Login failed | Username, password or private key, and any password needed to unlock that key |
| Cannot save a file | The selected server folder exists and your account can write to it |
| SFTP host fingerprint changed | Ask the server owner to verify its identity before trusting the new fingerprint |
| Sync lock remains | A lock means a device may be using the remote copy. Clear it only after confirming that all device transfers have stopped |
| FTPS certificate or TLS error | Ask the server owner to check the secure connection setup; do not switch off security checks just to bypass the error |

GitHub does not use the SFTP/FTP sync lock. See [SFTP setup](SYNC_SETUP.md#sftp-setup)
or [FTPS/FTP setup](SYNC_SETUP.md#ftps-and-ftp-setup).

## An older app cannot read the backup

Update YATA on all participating devices to compatible current versions. Current backups use
**format 5**; older versions of YATA may not understand it. Do not edit the backup's version
number by hand. See [the setup checklist](SYNC_SETUP.md#before-connecting).

## What should I send when asking for help?

Open [a GitHub issue](https://github.com/rjwarrier/yata/issues) and include:

- YATA version and Android version.
- Provider: GitHub, SFTP, FTPS or FTP.
- Whether this is the first sync or it worked before.
- The exact error, its date/time and time zone, and whether you started sync manually.
- Whether you use more than one device and whether another device can still sync.
- For GitHub: whether the repository has a README and which branch YATA uses.

Remove private information from screenshots and logs. Never post tokens, passwords, backup
passphrases, private keys, task backups, or configuration-transfer files in a public issue.
