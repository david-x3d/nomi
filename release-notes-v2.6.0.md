# Nomi v2.6.0

Share a day with another Nomi by holding the two phones together. No pairing, no account, no server.

## What is new

- **Share** and **Receive a shared day** in a food's long-press menu. Long-press a food, tick what to send, tap **Tap to share**, and hold the other phone to the back of this one.
- A combined meal is a box per food, so you can send the rice and leave the fish. Everything starts ticked, and the total updates as you untick.
- The receiving phone shows you the day — which day it was eaten on, which foods, what they add up to — before anything is written. **Add to my diary** keeps it, **Discard** drops it.
- An arriving day keeps the date it was eaten on, so somebody's Monday stays their Monday.
- Arriving foods are marked as shared and estimated, and are not looked up in your own food catalogue.

## How to share

1. On the phone that is sending: long-press a food → **Share** → tick the foods → **Tap to share**.
2. On the phone that is receiving: long-press any food → **Receive a shared day**.
3. Hold the two phones back to back, NFC areas touching, until the day has arrived. Keep them still.
4. On the receiving phone, tap **Add to my diary**.

## Requirements

- NFC on both phones. A phone without it still installs and will tell you.
- The receiving phone must be **unlocked with Nomi open**. A locked phone cannot be read, so if nothing happens, check that one first.

## Known limitations

- The tap has not yet been run between two physical phones. Android has no NFC in the emulator, so the radio is the one part of this release that is not verified on hardware.
- NFC is a contact link, not a network one. This works wherever two people are in the same place; it does not send to a phone that is somewhere else.
- A shared day is capped at a practical size — a few hundred kilobytes, far beyond any single day of food. Very large shares are refused rather than attempted.

## Also changed

- The Bluetooth share prototype has been removed along with the `BLUETOOTH_CONNECT` permission. Nomi no longer asks to reach your paired devices.
- A failed tap now returns you to the page instead of leaving you on *Waiting for a phone*.

Your food log, settings, and calorie targets are unchanged.
