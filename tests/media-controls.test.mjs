import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { MessageChannel } from 'node:worker_threads';
import { test } from 'node:test';
import { runInNewContext } from 'node:vm';

const script = readFileSync(new URL('../app/assets/media-controls.js', import.meta.url), 'utf8');
const bounds = { left: 0, top: 0, right: 640, bottom: 360 };

function visible(element, rect = bounds) {
  return Object.assign(element, {
    getBoundingClientRect: () => rect,
    closest: () => null
  });
}

function video({ intervals = [[0, 120]], ...overrides } = {}) {
  return visible({
    tagName: 'VIDEO',
    currentSrc: 'https://example.test/sample.mp4',
    currentTime: 45,
    duration: 120,
    paused: true,
    ended: false,
    readyState: 1,
    seekable: {
      length: intervals.length,
      start: index => intervals[index][0],
      end: index => intervals[index][1]
    },
    play() { this.paused = false; return Promise.resolve(); },
    pause() { this.paused = true; },
    ...overrides
  });
}

function load({ media = [], frames = [], buttons = [], inFrame = false } = {}) {
  const pending = new Map();
  const listeners = new Map();
  let nextId = 0;
  const window = {
    addEventListener: (name, handler) => listeners.set(name, handler),
    CineJoyTvMediaResult: {
      postMessage(message) {
        const { id, outcome } = JSON.parse(message);
        pending.get(id)(outcome);
        pending.delete(id);
      }
    }
  };
  window.parent = inFrame ? {} : window;
  window.top = window.parent;
  runInNewContext(script, {
    window,
    document: {
      querySelectorAll(selector) {
        if (selector === 'video,audio') return media;
        if (selector === 'iframe') return frames;
        return buttons;
      }
    },
    innerWidth: 960,
    innerHeight: 540,
    getComputedStyle: () => ({ visibility: 'visible' }),
    MessageChannel,
    setTimeout,
    clearTimeout
  });
  return {
    window,
    dispatch: event => listeners.get('message')(event),
    seekBy: window.cineJoyTvMedia.seekBy,
    command(action) {
      return new Promise(resolve => {
        const id = ++nextId;
        pending.set(id, resolve);
        window.cineJoyTvMedia.command(action, id);
      });
    }
  };
}

test('skips 10 seconds and clamps to the start and end without resuming playback', async () => {
  const media = video({ currentTime: 6 });
  const { command } = load({ media: [media] });
  assert.equal(await command('back'), 'back');
  assert.equal(media.currentTime, 0);
  assert.equal(await command('forward'), 'forward');
  assert.equal(media.currentTime, 10);
  media.currentTime = 117;
  assert.equal(await command('forward'), 'forward');
  assert.equal(media.currentTime, 120);
  assert.equal(media.paused, true);
});

test('crosses gaps between seekable ranges in both directions', async () => {
  const media = video({ currentTime: 16, intervals: [[5, 20], [60, 100]] });
  const { command } = load({ media: [media] });
  assert.equal(await command('forward'), 'forward');
  assert.equal(media.currentTime, 60);
  assert.equal(await command('back'), 'back');
  assert.equal(media.currentTime, 20);
  assert.equal(await command('forward'), 'forward');
  assert.equal(media.currentTime, 60);
});

test('long timeline seeks respect the current live window', () => {
  const media = video({ currentTime: 195, duration: Infinity, intervals: [[180, 210], [240, 300]] });
  const { seekBy } = load();
  assert.equal(seekBy(media, 30), true);
  assert.equal(media.currentTime, 240);
  assert.equal(seekBy(media, 60), true);
  assert.equal(media.currentTime, 300);
  assert.equal(seekBy(media, -180), true);
  assert.equal(media.currentTime, 180);
});

test('falls back to finite duration when there are no reported intervals', async () => {
  const media = video({ intervals: [], duration: 49 });
  const { command } = load({ media: [media] });
  assert.equal(await command('forward'), 'forward');
  assert.equal(media.currentTime, 49);
});

test('rejects seeks before metadata, in live video without ranges, and on media errors', async () => {
  const failed = video();
  Object.defineProperty(failed, 'currentTime', {
    get: () => 45,
    set() { throw new Error('Stream disconnected'); }
  });
  for (const media of [video({ readyState: 0 }), video({ intervals: [], duration: Infinity }), failed]) {
    const { command } = load({ media: [media] });
    assert.equal(await command('forward'), 'unseekable');
    assert.equal(media.currentTime, 45);
  }
});

test('toggles the playing video without selecting a paused preview', async () => {
  const preview = visible(video(), { left: 0, top: 0, right: 100, bottom: 100 });
  const playing = video({ paused: false });
  const { command } = load({ media: [preview, playing] });
  assert.equal(await command('toggle'), 'paused');
  assert.equal(playing.paused, true);
  assert.equal(preview.paused, true);
  assert.equal(await command('toggle'), 'playing');
  assert.equal(playing.paused, false);
  assert.equal(preview.paused, true);
});

test('returns loading when the media cannot begin playback immediately', async () => {
  const media = video({ play: () => Promise.reject(new Error('Playback unavailable')) });
  const { command } = load({ media: [media] });
  assert.equal(await command('play'), 'loading');
});

test('uses the available play button before the video is mounted', async () => {
  let clicks = 0;
  const button = visible({ click() { clicks++; } });
  const { command } = load({ buttons: [button] });
  assert.equal(await command('pause'), 'unavailable');
  assert.equal(await command('play'), 'loading');
  assert.equal(clicks, 1);
});

test('falls back to local video when a larger embed does not respond', async () => {
  const frame = visible({ contentWindow: { postMessage() {} } });
  const media = video();
  const { command } = load({ frames: [frame], media: [media] });
  assert.equal(await command('forward'), 'forward');
  assert.equal(media.currentTime, 55);
});

test('reports an unavailable embed when it cannot receive remote commands', async () => {
  const frame = visible({ contentWindow: { postMessage() { throw new Error('Frame disconnected'); } } });
  const { command } = load({ frames: [frame] });
  assert.equal(await command('forward'), 'unavailable');
});

test('embedded video accepts only a supported command from its parent', async () => {
  const media = video();
  const { window, dispatch } = load({ media: [media], inFrame: true });
  let replies = [];
  const reply = { postMessage: outcome => replies.push(outcome), close() {} };
  const event = {
    source: {},
    data: { channel: 'cinejoy-tv-media', action: 'forward' },
    ports: [reply]
  };
  dispatch(event);
  assert.equal(media.currentTime, 45);
  dispatch({ ...event, source: window.parent, data: { ...event.data, action: 'unknown' } });
  dispatch({ ...event, source: window.parent });
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(media.currentTime, 55);
  assert.deepEqual(replies, ['unavailable', 'forward']);
});
