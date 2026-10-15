# Nomi v2.9.4

### Under the hood

Nothing about how Nomi looks or behaves is meant to change in this release. It is a cleanup of the code underneath.

- The 4,359-line view model is split into focused parts: provider configuration, key handling, Health Connect sync, the debug log, the on-device food catalogue and the screen-state mappers each live on their own.
- The Today page and the root screen are split into files by what they draw. The largest single function went from 766 lines to under 250.
- Unused code is gone: the pre-notes Today page, the system speech recogniser that Whisper replaced, and several models nothing referenced.
- Reading a photo for a meal, a label or a menu page goes through one shared loader instead of five copies of the same block.

Install the attached APK over your existing Nomi installation to keep your data.
