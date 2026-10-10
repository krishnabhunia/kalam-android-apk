# Keep clocks across uninstall and reinstall

Android removes private app storage on uninstall unless the operating system
offers to retain it and the user chooses that option. Kalam already enables
Android Auto Backup, but backup timing, device settings, restore eligibility,
and matching app signing identity mean it cannot guarantee restoration.

Kalam now requests Android's **Keep app data** uninstall option where supported.
This is an OS prompt, not a silent override of the user's uninstall decision.

For a portable backup independent of signing identity or cloud backup timing:

1. Open **Settings → Clock backup → Back up clocks** while your clocks are still present.
2. Save `Kalam_clocks_backup.json` in **Downloads** or another shared folder.
3. Back up again after changing your clocks. Check that saving succeeded.
4. Reinstall Kalam, then open **Settings → Clock backup → Restore clocks** and select that file.
5. Review the clock count and confirm restoration. The saved list replaces the
   current clocks, including an empty list if an empty backup is selected.

The backup preserves each clock's name, region, IANA timezone, coordinates,
alias, expanded state, list order, sort direction, grouping and group stages.
It excludes weather caches, credentials, match history and birth details.
Weather uses the existing refresh behavior. Other settings are preserved on
manual restore. The document picker grants access only to the selected file;
the app asks for no broad storage permission.

Import validates the entire versioned JSON before any data changes, rejects
invalid coordinates/timezones, missing fields, unsupported versions, corrupt
UTF-8 and files larger than 1 MiB, then commits clocks and display settings in
one DataStore transaction. Read/save failures are logged and shown to the user.

This cannot recover clocks already deleted without a retained-data copy or
existing backup. A shared backup file must exist before uninstalling.
CI currently builds debug-signed APKs; fresh GitHub runners may generate
different debug certificates. Manual clock files work across those installs;
OS data retention and Auto Backup should only be relied on with a stable
signing certificate.

The Clock backup section starts collapsed and shows only its title and arrow.
Backup controls and reinstall guidance appear when expanded. Restoring clocks
preserves unrelated unsaved Settings edits; Save and Discard apply only to
setting changes, not to backup or restore.

## Device checks

- Save multiple clocks including Unicode aliases and grouped/expanded cards.
- Back up to Downloads, uninstall, reinstall, restore and compare every field.
- Check the Keep app data uninstall option on Android 10+ with the same signer.
- Cancel both pickers and the restore confirmation; no clock changes should occur.
- Import a truncated or foreign JSON file; existing clocks must remain intact.
- Try large text settings and a narrow screen; backup buttons wrap instead of clipping.

References:
[Android Auto Backup](https://developer.android.com/identity/data/autobackup),
[hasFragileUserData](https://developer.android.com/guide/topics/manifest/application-element#fragileuserdata),
[Shared document files](https://developer.android.com/training/data-storage/shared/documents-files).
