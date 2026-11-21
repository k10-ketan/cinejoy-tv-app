# Test CineJoy TV

## Automated media checks

Install Node.js 20 or newer and run the dependency-free suite from the project root.

```sh
node --test tests/*.test.mjs
```

The tests execute the bundled media-control script with simulated media and frame responses. They cover skipping near both ends, seeking across gaps, sliding live windows, missing or changing media, play errors, and unresponsive embedded players. The tests also check that a non-parent frame cannot send a playback command to an embedded player.

Open `tests/preview-focus.html` in Chrome or Chromium. The page runs a regression check against the bundled remote script and reports **PASS movie preview focus** when a preview focuses Resume or Play, preserves focus as its contents update, and restores the card focus when dismissed with Back.

To run the auto-hide regression, start a local server from the project root.

```sh
python3 -m http.server 8000 --bind 127.0.0.1
```

Open [the playback fixture](http://127.0.0.1:8000/tests/player-autohide.html) in Chrome or Chromium. It reports **PASS player controls auto-hide** after checking idle playback, the direct skip shortcut, timeline focus during pause and resume, and closing a player menu.

## Test on a TV

Use a TV remote from launch through playback. Run the checks with a fresh installation and with an update signed by the same key as the installed build.

| Case | Expected result |
| --- | --- |
| Open Home and press Down through the banner | The first content row is already visible and the focus highlight reaches a card |
| Browse down the Home page, then select Back to Top | The arrow returns to the top. The main navigation still offers Home, and D-pad focus skips the top-left logo |
| Move quickly across a row, then reverse direction | The highlight follows the D-pad and stays visible as the row scrolls |
| Open a preview from Continue Watching | Resume is highlighted immediately. Select starts playback from the saved position. Back on the preview returns focus to the card |
| Type a search, dismiss the keyboard, and select a result | Back dismisses the keyboard first, and Down moves into the results |
| Open a playable title and reach the top title text | The D-pad skips the title and the remote Back button exits the player |
| Start playback in the built-in player and leave the remote idle for four seconds | The playback bar hides. Down brings it back |
| Pause with the media key, then tap Left and Right with playback controls unfocused | Each press skips 10 seconds when the stream supports it. The player stays paused |
| Focus a playback control, press Up, and hold Left or Right | The timeline is highlighted. Holding seeks in larger steps and stays within the playable range |
| Press Up with the timeline focused while playing or paused | The player buttons and timeline hide immediately. Holding Up keeps them hidden, and Down brings them back |
| Leave the timeline focused while paused, then press Play and wait four seconds | The timeline remains visible while paused. During playback, the bar hides and releases focus |
| Open Servers, Subtitles, or Settings after the controls fade out | Focus enters the panel. Back closes it, and Left or Right inside it leaves the playback position alone |
| Play a title with an unavailable source or temporarily disconnect the network | The app stays responsive. Try another server or reconnect and reload |
| Press Home during playback, then return to the app | Playback stays in the background only if the device and app session allow it. The player remains controllable on return |

## Version 1.9 playback coverage

Both browser regressions and all 11 media tests passed. On a Fire TV Stick running Android 9-based Fire OS, the bar hid during playback, Right still skipped 10 seconds and revealed the controls, and the timeline stayed visible while paused. Resuming playback hid the bar and released timeline focus. Settings stayed open during playback, and the bar hid again after Back closed Settings. The device and release APKs contained identical app files and used their respective signing keys.

## Version 1.8 preview coverage

The browser regression and all 11 media tests passed. On a Fire TV Stick, Select opened a Continue Watching card with Resume highlighted, Back closed the preview and restored focus to the card, and a second Select on the reopened preview resumed playback from the saved position. The Fire TV test and public APKs had identical app files and used their respective signing keys.

## Version 1.7 playback coverage

All 11 automated tests passed. In a browser with the live site, the production scripts passed checks for the compact home banner, direct 10-second skips, 10/30/60-second timeline steps, focus after the controls faded, the title’s exclusion from D-pad focus, and opening and closing the Servers, Subtitles, and Settings panels.

The v1.7 test APK installed over the existing build and launched on a Fire TV Stick running Fire OS based on Android 9. Remote key events paused and resumed playback, skipped 10 seconds forward and back while paused, focused and scrubbed the timeline, and returned to Home with Back. The hardware test APK used the earlier development key. Its runtime files matched the public APK, which passed signature and checksum verification.

Android TV and Google TV models require their own installation and playback checks. Results for a given player or stream can vary with website changes, network access, and the device’s WebView.
