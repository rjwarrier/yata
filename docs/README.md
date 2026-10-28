# YATA documentation

## Using YATA

| Guide | Purpose |
| --- | --- |
| [User guide](USER_GUIDE.md) | First-run walkthrough and detailed everyday workflows, with demo screenshots |
| [Feature reference](FEATURES.md) | Supported behavior, limits and implementation entry points |
| [Sync and backup setup](SYNC_SETUP.md) | GitHub/SFTP/FTPS/FTP setup, additional devices, encryption and recovery |
| [Changelog](../CHANGELOG.md) | Changes by released version and unreleased work |
| [Project README](../README.md) | Overview, downloads and quick start |

## Developing YATA

| Reference | Purpose |
| --- | --- |
| [Developer guide](DEVELOPMENT.md) | Setup, architecture, builds, verification and contribution workflow |
| [Repository working notes](../CLAUDE.md) | Detailed implementation conventions and agent guidance |
| [Natural-language recognition](natural-language-recognition.md) | Parser structure, language packs, normalization and tests |
| [Localization](localization.md) | String resources, translations and validation |
| [Database 36 integration](database-36-integration.md) | Historical schema reconciliation and migration checks |
| [Design references](../design/README.md) | Visual design context; reference material rather than executable app code |
| [Screenshot provenance](assets/screenshots/README.md) | Demo captures, versions, filenames and reuse notes |

## Design plans and historical handoffs

These files document proposals or implementation history. They are not setup instructions
and may describe earlier behavior. Use the user guide and current source for supported behavior.

- [GitHub sync plan](github-sync-plan.md)
- [Natural-language refactor plan](natural-language-recognition-refactor-plan.md)
- [App-link sharing design](app-links-team-sharing-design.md)
- [App-links v2 plan](app-links-v2-plan.md) and [v3 plan](app-links-v3-plan.md)
- [App-link reliability plan](app-links-reliability-plan.md)
- [App-links handoff](app-links-handoff.md)
- [About-section specification](about-section-spec.md)
- [Repository handoff](../HANDOFF.md) and [translation handoff](../TRANSLATION_HANDOFF.md)

The documentation structure follows the project's companion [Vayana README](https://github.com/rjwarrier/Vayana)
and [user guide](https://github.com/rjwarrier/Vayana/blob/main/docs/USER_GUIDE.md): overview,
illustrated workflows, feature reference and dedicated sync instructions. YATA's procedures
and feature descriptions are based on this repository's implementation.
