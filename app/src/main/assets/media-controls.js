(function () {
  'use strict';
  if (window.cineJoyTvMedia) return;

  var channel = 'cinejoy-tv-media';
  var allowed = ['play', 'pause', 'toggle', 'back', 'forward'];
  var controls = 'button[aria-label="Play" i],button[title="Play" i],.vjs-big-play-button,' +
    '.jw-display-icon-container .jw-icon-display,[data-plyr="play"]';

  function visibleArea(element) {
    var rect = element.getBoundingClientRect();
    var width = Math.min(innerWidth, rect.right) - Math.max(0, rect.left);
    var height = Math.min(innerHeight, rect.bottom) - Math.max(0, rect.top);
    if (width < 1 || height < 1 ||
        element.closest('[hidden],[inert]') ||
        getComputedStyle(element).visibility !== 'visible') return 0;
    return width * height;
  }

  function activeMedia() {
    var elements = Array.from(document.querySelectorAll('video,audio')).filter(function (media) {
      return (media.currentSrc || media.src || media.srcObject) &&
        (visibleArea(media) || media.tagName === 'AUDIO');
    });
    return elements.find(function (media) { return !media.paused && !media.ended; }) ||
      elements.sort(function (a, b) { return visibleArea(b) - visibleArea(a); })[0];
  }

  function sendToFrame(frame, action) {
    return new Promise(function (resolve) {
      var pipe = new MessageChannel();
      var timeout = setTimeout(function () { finish('unavailable'); }, 900);
      function finish(result) {
        clearTimeout(timeout);
        pipe.port1.close();
        resolve(result);
      }
      pipe.port1.onmessage = function (event) { finish(event.data); };
      try {
        frame.contentWindow.postMessage({channel: channel, action: action}, '*', [pipe.port2]);
      } catch (error) {
        finish('unavailable');
      }
    });
  }

  function seekBy(media, seconds) {
    if (media.readyState === 0 || !Number.isFinite(media.currentTime)) return false;
    var ranges = media.seekable;
    var target = media.currentTime + seconds;
    var time;
    if (!ranges.length) {
      if (!Number.isFinite(media.duration) || media.duration <= 0) return false;
      time = Math.max(0, Math.min(media.duration, target));
    } else if (seconds > 0) {
      // Move to the next available interval if the target falls in a gap.
      time = ranges.end(ranges.length - 1);
      for (var i = 0; i < ranges.length; i++) {
        if (target > ranges.end(i)) continue;
        time = Math.max(ranges.start(i), target);
        break;
      }
    } else {
      time = ranges.start(0);
      for (var j = ranges.length - 1; j >= 0; j--) {
        if (target < ranges.start(j)) continue;
        time = Math.min(ranges.end(j), target);
        break;
      }
    }
    try {
      media.currentTime = time;
      return true;
    } catch (error) {
      return false;
    }
  }

  function controlMedia(media, action) {
    if (action === 'pause' || (action === 'toggle' && !media.paused && !media.ended)) {
      media.pause();
      return Promise.resolve('paused');
    }
    if (action === 'toggle' || action === 'play') {
      return new Promise(function (resolve) {
        var timer = setTimeout(function () { resolve('loading'); }, 650);
        Promise.resolve(media.play()).then(function () {
          clearTimeout(timer);
          resolve('playing');
        }, function () {
          clearTimeout(timer);
          resolve('loading');
        });
      });
    }

    return Promise.resolve(seekBy(media, action === 'back' ? -10 : 10) ? action : 'unseekable');
  }

  function run(action) {
    if (allowed.indexOf(action) === -1) return Promise.resolve('unavailable');
    var media = activeMedia();
    var frame = Array.from(document.querySelectorAll('iframe'))
      .filter(function (item) { return item.contentWindow && visibleArea(item) > 12000; })
      .sort(function (a, b) { return visibleArea(b) - visibleArea(a); })[0];
    if (media && (!frame || visibleArea(media) > visibleArea(frame) + 100 || !media.paused)) {
      return controlMedia(media, action);
    }
    if (frame) {
      return sendToFrame(frame, action).then(function (result) {
        if (result === 'unavailable' && media) return controlMedia(media, action);
        return result;
      });
    }
    if (action === 'toggle' || action === 'play') {
      var play = Array.from(document.querySelectorAll(controls)).find(function (button) {
        return visibleArea(button) && !button.disabled;
      });
      if (play) {
        play.click();
        return Promise.resolve('loading');
      }
    }
    return Promise.resolve('unavailable');
  }

  window.addEventListener('message', function (event) {
    if (window === window.top || event.source !== window.parent || !event.data ||
        event.data.channel !== channel || !event.ports.length) return;
    var reply = event.ports[0];
    function send(result) {
      reply.postMessage(result);
      reply.close();
    }
    run(event.data.action).then(send, function () { send('unavailable'); });
  });

  window.cineJoyTvMedia = {
    seekBy: seekBy,
    command: function (action, id) {
      function report(outcome) {
        if (window.CineJoyTvMediaResult && id !== undefined) {
          window.CineJoyTvMediaResult.postMessage(JSON.stringify({id: id, outcome: outcome}));
        }
      }
      run(action).then(report, function () { report('unavailable'); });
    }
  };
})();
