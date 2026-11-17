# Vanish Class Network — setup guide

## Use the class-wide relay

All classmates must use the same shared WebSocket relay address:

`wss://vanish-relay-sawaira.bonto.page/ws`

The Android app in this project has been adapted for **Vanish Relay Protocol v1**. It no longer uses the old HTTP `/sync/push` and `/sync/pull` endpoints for chat.

## Step-by-step: Android Studio

1. Extract the ZIP file.
2. Open the `field-sync/app` folder in Android Studio.
3. Wait for Gradle sync to finish. Keep the project plugin versions unchanged.
4. Connect an Android phone with USB debugging enabled, or start an Android emulator.
5. Press **Run** to install and launch the app.
6. Enter a display name (maximum 30 characters).
7. Keep the relay address as `wss://vanish-relay-sawaira.bonto.page/ws`. Every student must use this exact address.
8. Press **Join Network**. The first connection after the free host has been idle may take up to a minute.
9. Send a message. Other classmates connected to the same relay should receive it. Messages disappear 30 seconds after the relay timestamp.

## Test the relay

Open the gear/settings icon, check the relay address, and press **Test connection**. A successful test means the WebSocket connection accepted the protocol JOIN and returned a `welcome` frame.

## How the app handles the protocol

- Sends `join` with protocol version 1, a persistent device ID, and the student's display name.
- Sends chat messages as JSON WebSocket text frames.
- Receives relay messages and uses the server clock offset to calculate expiry.
- Sends a JSON `ping` every 15 seconds and expects `pong`.
- Reconnects after a lost connection using 1, 2, 4, then 8-second delays.
- Queues messages locally while offline, but drops them when their 30-second local send window has passed.
- Uses message IDs and the local Room primary key to avoid displaying duplicate messages.
- Handles `delivered`, `expired`, and `error` frames.

## Important limitations from the shared proposal

- The protocol proposal is marked **pending agreement**. Confirm it has been approved before treating it as final.
- The relay has no password; anyone who knows the address can join.
- The relay stores messages in memory only. It does not replay messages after reconnecting.
- The host may sleep when idle, so a first connection can be slow.
- The old `server/` folder is the teacher's HTTP field-sync starter and is **not** the shared WebSocket relay. Do not start that local server for the class-wide network.
- The relay address and protocol must be supported by the hosted server. The Android app cannot make a server implement a protocol it does not support.

## Protocol reference

See [`PROTOCOL.md`](PROTOCOL.md) for the complete shared message contract.
