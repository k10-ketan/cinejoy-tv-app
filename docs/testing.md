# Test CineJoy TV

## Automated media checks

Install Node.js 20 or newer and run the dependency-free suite from the project root.

```sh
node --test tests/*.test.mjs
```

The tests execute the bundled media-control script with simulated media and frame responses. They cover skipping near both ends, seeking across gaps, sliding live windows, missing or changing media, play errors, and unresponsive embedded players. The tests also check that a non-parent frame cannot send a playback command to an embedded player.

## Signed controls updates

Run the verifier tests with JDK 17. They include modified signatures, malformed lengths, unsupported formats, and oversized downloads.

```sh
test_dir="$(mktemp -d)"
javac -d "$test_dir" app/src/main/java/app/cinejoy/tv/ControlsBundle.java tests/ControlsBundleTest.java
java -cp "$test_dir" app.cinejoy.tv.ControlsBundleTest
```

For a TV test of the update channel, use a release APK or build with `CINEJOY_ENABLE_CONTROLS_UPDATES=1`. Launch after publishing a higher signed bundle and look for `Saved controls bundle` and `Using controls bundle` under the `CineJoyControls` logcat tag. Start a title and return from the launcher to verify that a controls refresh preserves the playback page. To simulate a channel outage or invalid response, use a test build pointed at a test channel and confirm that the previous signed bundle remains active.

## Browser regressions

Start a local server from the project root and open both fixtures in Chrome or Chromium.

```sh
python3 -m http.server 8000 --bind 127.0.0.1
```

- **Preview focus** checks that Resume or Play receives focus, that it survives preview updates, and that Back restores focus to the card. [Open fixture](http://127.0.0.1:8000/tests/preview-focus.html)
- **Playback controls** checks auto-hide, direct skipping, timeline focus during pause and resume, closing a player menu, and navigating the episode picker. [Open fixture](http://127.0.0.1:8000/tests/player-autohide.html)

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
| Open Episodes, press Down to enter the row, use Left or Right, and press Select | The row scrolls to keep the focused card visible, and playback switches to that episode |
| Open the season selector and press Back twice | The first press returns to the episode row. The second closes the picker and focuses the Episodes button |
| Play a title with an unavailable source or temporarily disconnect the network | The app stays responsive. Try another server or reconnect and reload |
| Press Home during playback, then return to the app | Playback stays in the background only if the device and app session allow it. The player remains controllable on return |

## Device coverage

Fire TV Stick testing on Android 9-based Fire OS covered installation over an existing build, Continue Watching, play and pause, direct skipping, timeline seeking, control auto-hide, and focus restoration. Android TV and Google TV devices still need hardware coverage. Results can vary with the stream provider, network, and system WebView.
