// TV remote navigation by k10-ketan.
(function () {
  'use strict';
  // Amazon WebView on Fire TV can omit the Media Session interfaces CineJoy uses.
  if (typeof window.MediaMetadata !== 'function') {
    window.MediaMetadata = function (metadata) {
      metadata = metadata || {};
      this.title = metadata.title || '';
      this.artist = metadata.artist || '';
      this.album = metadata.album || '';
      this.artwork = metadata.artwork || [];
    };
  }
  if (!navigator.mediaSession) {
    Object.defineProperty(navigator, 'mediaSession', {value: {
      metadata: null,
      playbackState: 'none',
      setActionHandler: function () {},
      setPositionState: function () {}
    }});
  }
  if (window.cineJoyTvRemote || !document.body) return;

  var timeline = '.player-root [class~="group/progress"]:has(> input[type=range][aria-label="Seek Video"])';
  var episodePanel = '.episode-popover';
  var episodeCard = '[role=button][aria-label^="Play "]';
  var seasonButton = 'button[aria-label="Change season"]';
  var focusable = [
    'a[href]', 'button', 'input:not([type=hidden])', 'select', 'textarea',
    'summary', '[contenteditable=true]', '[role=button]', '[role=tab]',
    '[role=option]', '[role=menuitem]', '[role=checkbox]', '[role=switch]',
    '[role=radio]', '[role=slider]', '[tabindex]:not([tabindex="-1"])',
    '[data-tooltip-content]', '[data-tooltip-html]', 'label[for]', 'iframe', 'video[controls]', timeline
  ].join(',');
  var typing = 'textarea,[contenteditable=true],input:not([type=button]):not([type=submit]):not([type=reset]):not([type=checkbox]):not([type=radio]):not([type=range]):not([type=file]):not([type=color]):not([type=hidden])';
  var focused = null;
  var adjusting = null;
  var timelineWake = null;
  var playerIdleTimer = null;
  var beforeTimeline = null;
  var playerMenuButton = null;
  var previewSelector = 'body > .preview[role="group"]';
  var activePreview = null;
  var previewOpener = null;
  var previewPath = null;
  var label = document.createElement('div');
  label.setAttribute('aria-hidden', 'true');
  label.id = 'cinejoy-tv-label';
  label.hidden = true;

  var css = document.createElement('style');
  css.textContent =
    'html{scroll-padding:24px 0 72px;}' +
    '[data-cinejoy-tv-focus]{outline:3px solid #95ff50!important;outline-offset:-3px!important;}' +
    '#cinejoy-tv-label{position:fixed;z-index:2147483647;padding:5px 9px;border-radius:5px;' +
    'background:#101510;color:white;font:650 16px system-ui;pointer-events:none;white-space:nowrap;}' + `
    .app-shell :is(.glass-header, .hero-action-pill) {
      backdrop-filter: none !important;
      background-color: #17191e !important;
    }
    .header-row a[href="/"][aria-label="Home"]:not(nav *) {
      visibility: hidden;
    }
    .player-root button[aria-label="Back"] {
      visibility: hidden;
    }
    .player-root button:has(+ [data-cinejoy-player-title]) {
      display: none;
    }
    .player-root[data-cinejoy-player-idle] > .bottom-0:has([aria-label="Seek Video"]),
    .player-root[data-cinejoy-player-idle] > .inset-0.bg-gradient-to-b.pointer-events-none,
    .player-root[data-cinejoy-player-idle="dismissed"] > .top-0 button {
      visibility: hidden !important;
    }
    [data-cinejoy-player-title] {
      max-width: 60%;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      color: white;
      text-align: center;
      font-weight: 400;
      letter-spacing: .025em;
    }
    /* CineJoy's home carousel uses a separate height for the artwork and content. */
    .app-shell > div > [class~="h-[85vh]"] {
      height: clamp(244px, 54vh, 392px);
      isolation: isolate;
    }
    .app-shell > div > [class~="h-[85vh]"] > .overflow-hidden {
      height: 100% !important;
    }
    .app-shell > div > [class~="h-[85vh]"] > .overflow-hidden > img {
      object-position: center 34%;
    }
    .app-shell > div > [class~="h-[85vh]"] > .overflow-hidden::after {
      position: absolute;
      inset: 0;
      content: '';
      pointer-events: none;
      background: linear-gradient(90deg, #050505e8 0%, #050505a6 34%, transparent 72%),
                  linear-gradient(0deg, #050505 0%, transparent 30%);
    }
    .app-shell > div > [class~="h-[85vh]"] > .inset-0.items-end {
      padding: 0 clamp(24px, 4vw, 56px) 20px;
    }
    .app-shell > div > [class~="h-[85vh]"] :is(.max-w-2xl, .max-w-2xl > .flex-col) {
      align-items: flex-start;
      gap: 8px;
      margin-inline: 0;
      max-width: min(100%, 460px);
      text-align: left;
    }
    .app-shell > div > [class~="h-[85vh]"] .max-w-2xl .justify-center {
      justify-content: flex-start;
    }
    .app-shell > div > [class~="h-[85vh]"] .max-w-2xl :is(img, h1) {
      max-width: min(100%, 320px);
      max-height: clamp(44px, 13vh, 92px);
      object-position: left center;
      transform-origin: left;
      font-size: clamp(22px, 4vh, 36px);
    }
    .app-shell > div > [class~="h-[85vh]"] .max-w-2xl p {
      text-align: left;
      font-size: 15px;
      line-height: 20px;
      -webkit-line-clamp: 2;
    }
    .app-shell > div > [class~="h-[85vh]"] > .bottom-32 {
      bottom: 24px;
    }
    .app-shell > div > [class~="h-[85vh]"] + .relative {
      padding-top: 16px;
    }
    @media (min-width: 800px) and (max-width: 1023px) {
      html:has(.app-shell > div > [class~="h-[85vh]"]) .header-row > * .desktop-nav,
      html:has(.app-shell > div > [class~="h-[85vh]"]) .header-row > .desktop-nav {
        display: flex;
      }
      html:has(.app-shell > div > [class~="h-[85vh]"]) .header-row + .pointer-events-auto {
        display: none;
      }
    }
    `;
  document.documentElement.appendChild(css);
  document.body.appendChild(label);

  function shown(element, rect) {
    if (!element || !document.contains(element) ||
        element.closest('[hidden],[inert],[aria-hidden=true],[aria-disabled=true]') ||
        element.matches(':disabled')) return false;
    var style = getComputedStyle(element);
    rect = rect || element.getBoundingClientRect();
    return style.visibility === 'visible' && style.pointerEvents !== 'none' &&
      style.opacity !== '0' && rect.width >= 4 && rect.height >= 4;
  }

  function scope() {
    var root = document.fullscreenElement || document.webkitFullscreenElement || document;
    var dialogs = root.querySelectorAll('dialog[open],[role=dialog][aria-modal=true],[aria-modal=true]');
    for (var i = dialogs.length - 1; i >= 0; i--) {
      if (shown(dialogs[i])) return dialogs[i];
    }
    if (root === document && !watching()) {
      var preview = document.querySelector(previewSelector);
      if (shown(preview)) return preview;
    }
    return (watching() && playerPanel(root)) || root;
  }

  function playerPanel(root) {
    var panels = (root || document).querySelectorAll(
      '.player-root .server-panel,.player-root .menu-slide-panel,.player-root ' + episodePanel);
    for (var i = panels.length - 1; i >= 0; i--) {
      if (shown(panels[i]) && hitPoint(panels[i])) return panels[i];
    }
    return null;
  }

  function usableControl(element, rect) {
    if (!shown(element, rect)) return false;
    if (element.matches('button,a,input,select,textarea,[role]')) return true;
    if (element.parentElement.closest('button,a,[role=button]')) return false;
    if (element.matches('[data-tooltip-content],[data-tooltip-html]') &&
        !element.hasAttribute('tabindex')) {
      return typeof element.onclick === 'function' || getComputedStyle(element).cursor === 'pointer';
    }
    return true;
  }

  function controls(root) {
    return Array.from(root.querySelectorAll(focusable)).filter(function (element) {
      return usableControl(element);
    });
  }

  function updateLabel() {
    label.hidden = true;
    if (!shown(focused) || (focused.innerText || "").trim()) return;
    var onTimeline = focused.matches(timeline);
    var name = onTimeline ? 'Timeline · ◀ / ▶ to seek' :
      focused.getAttribute('aria-label') || focused.getAttribute('data-tooltip-content') ||
      focused.getAttribute('title');
    if (!name) return;
    var rect = focused.getBoundingClientRect();
    if (rect.bottom < 0 || rect.top > innerHeight || rect.right < 0 || rect.left > innerWidth) return;
    label.textContent = name;
    label.hidden = false;
    label.style.left = Math.max(4, Math.min(rect.left, innerWidth - label.offsetWidth - 4)) + 'px';
    label.style.top = (!onTimeline && rect.bottom + label.offsetHeight + 4 < innerHeight ?
      rect.bottom + 4 : Math.max(4, rect.top - label.offsetHeight - 4)) + 'px';
  }

  function clear() {
    if (focused) focused.removeAttribute('data-cinejoy-tv-focus');
    clearInterval(timelineWake);
    timelineWake = null;
    beforeTimeline = null;
    focused = null;
    adjusting = null;
    label.hidden = true;
  }

  function focus(element) {
    if (focused && focused !== element) clear();
    focused = element;
    element.setAttribute('data-cinejoy-tv-focus', '');
    element.scrollIntoView({block: 'nearest', inline: 'nearest', behavior: 'instant'});
    // Keep the TV keyboard closed until Select is pressed on a text field.
    if (!element.matches(typing + ',iframe')) {
      if (element.tabIndex < 0 && !element.hasAttribute('tabindex')) element.tabIndex = -1;
      element.focus({preventScroll: true});
    }
    if (element.matches(timeline) && timelineWake === null) {
      timelineWake = setInterval(function () {
        if (watching() && shown(focused)) revealSiteControls(focused.closest('.player-root'));
        else clear();
      }, 800);
    }
    updateLabel();
  }

  function inViewport(rect) {
    return rect.right > 0 && rect.bottom > 0 && rect.left < innerWidth && rect.top < innerHeight;
  }

  function current(root) {
    var rect = focused && focused.getBoundingClientRect();
    if (shown(focused, rect)) {
      if (root.contains(focused) && inViewport(rect)) return focused;
    }
    var first = (!shown(focused, rect) || root === activePreview) && preferred(root);
    if (first) return first;
    var items = controls(root);
    var active = document.activeElement;
    if (items.indexOf(active) !== -1 && inViewport(active.getBoundingClientRect())) return active;
    return items.find(function (element) {
      return inViewport(element.getBoundingClientRect()) && hitPoint(element);
    }) || items[0];
  }

  function watching() {
    return location.pathname === '/watch' || location.pathname.startsWith('/watch/');
  }

  var playerTitle = document.createElement('span');
  playerTitle.setAttribute('data-cinejoy-player-title', '');
  playerTitle.className = 'text-sm md:text-lg drop-shadow-md';

  function syncPlayerTitle() {
    var button = watching() && document.querySelector('.player-root button[aria-label^="View details for "]');
    if (!button) {
      playerTitle.remove();
      return;
    }
    // Keep the site's button in the DOM so its renderer still owns it.
    var title = button.textContent.trim();
    if (playerTitle.textContent !== title) playerTitle.textContent = title;
    if (button.nextElementSibling !== playerTitle) button.after(playerTitle);
    if (focused === button) clear();
  }

  function hidePlayerControls(player, reason) {
    clearTimeout(playerIdleTimer);
    if (player.contains(focused)) clear();
    var active = document.activeElement;
    if (player.contains(active) &&
        (reason === 'dismissed' || active.closest('.bottom-0:has([aria-label="Seek Video"])'))) {
      active.blur();
    }
    player.setAttribute('data-cinejoy-player-idle', reason);
  }

  function showPlayerControls(player) {
    player.removeAttribute('data-cinejoy-player-idle');
    clearTimeout(playerIdleTimer);
    var video = player.querySelector('video');
    if (!video || video.paused || video.ended) return;
    playerIdleTimer = setTimeout(function () {
      if (!watching() || !player.contains(video) || video.paused || video.ended || playerPanel()) return;
      hidePlayerControls(player, 'timeout');
    }, 4000);
  }

  function revealSiteControls(player) {
    var position = {bubbles: true, clientX: innerWidth / 2, clientY: innerHeight / 2};
    var surface = document.elementFromPoint(position.clientX, position.clientY);
    if (!surface || !player.contains(surface)) surface = player;
    surface.dispatchEvent(new PointerEvent('pointermove', position));
    surface.dispatchEvent(new MouseEvent('mousemove', position));
  }

  function wakePlayer() {
    var player = document.querySelector('.player-root');
    if (!player) return;
    showPlayerControls(player);
    revealSiteControls(player);
  }

  function preferred(root) {
    if (watching()) return null;
    var preview = root !== document && root.matches(previewSelector);
    if (root !== document && !preview) return null;
    if (root === document && location.pathname === '/search') {
      return document.querySelector('input[type=search],input[placeholder*="Search" i]');
    }
    if (preview || location.pathname === '/' || /^\/(movie|tv|series)\//.test(location.pathname)) {
      return Array.from(root.querySelectorAll('button,a[href]')).find(function (element) {
        return /^(play|resume)(\s|$)/i.test((element.getAttribute('aria-label') || element.innerText || '').trim()) &&
          shown(element) && (root !== document || hitPoint(element));
      });
    }
    return null;
  }

  function syncPreviewFocus() {
    var preview = !watching() && document.querySelector(previewSelector);
    if (!preview && activePreview) {
      if (previewPath === location.pathname && shown(previewOpener) &&
          (!focused || activePreview.contains(focused))) focus(previewOpener);
      activePreview = null;
      previewOpener = null;
      previewPath = null;
    }
    if (!preview || preview === activePreview) return;
    var play = preferred(preview);
    if (!play) return;
    previewOpener = focused && !preview.contains(focused) ? focused : null;
    previewPath = location.pathname;
    activePreview = preview;
    focus(play);
  }

  function scrollParent(element, horizontal) {
    for (var parent = element.parentElement; parent && parent !== document.body; parent = parent.parentElement) {
      var overflow = horizontal ? getComputedStyle(parent).overflowX : getComputedStyle(parent).overflowY;
      var room = horizontal ? parent.scrollWidth - parent.clientWidth : parent.scrollHeight - parent.clientHeight;
      if (/auto|scroll|hidden/.test(overflow) && room > 3) return parent;
    }
    return document.scrollingElement;
  }

  function seek(bar, direction, repeat) {
    var video = bar.closest('.player-root').querySelector('video');
    if (!video || !window.cineJoyTvMedia) return;
    var seconds = repeat >= 18 ? 60 : repeat >= 8 ? 30 : 10;
    window.cineJoyTvMedia.seekBy(video, direction === 'left' ? -seconds : seconds);
  }

  function moveEpisodeFocus(panel, from, direction) {
    if (!panel.matches(episodePanel)) return false;
    var season = panel.querySelector(seasonButton);
    var onCard = from.matches(episodeCard);
    if (onCard && direction === 'up' && shown(season)) {
      focus(season);
      return true;
    }
    if (!(from === season && direction === 'down') &&
        !(onCard && (direction === 'left' || direction === 'right'))) return false;
    var cards = Array.from(panel.querySelectorAll(episodeCard)).filter(function (card) {
      return usableControl(card);
    });
    var next = from === season ? cards.find(hitPoint) || cards[0] :
      cards[cards.indexOf(from) + (direction === 'left' ? -1 : 1)];
    if (next) focus(next);
    return true;
  }

  function move(direction, repeat) {
    var horizontal = direction === 'left' || direction === 'right';
    if (watching()) {
      if (direction === 'up' && document.querySelector('.player-root[data-cinejoy-player-idle="dismissed"]')) {
        return;
      }
      if (horizontal && !shown(focused) && scope() === document &&
          document.querySelector('.player-root') && window.cineJoyTvMedia) {
        if (!repeat) {
          clear();
          wakePlayer();
          window.cineJoyTvMedia.command(direction === 'left' ? 'back' : 'forward');
        }
        return;
      }
      wakePlayer();
    }
    if (adjusting && shown(adjusting) && horizontal) {
      var oldValue = adjusting.value;
      if (direction === 'left') adjusting.stepDown();
      else adjusting.stepUp();
      if (adjusting.value !== oldValue) {
        // React tracks changes made through its value setter.
        var nextValue = adjusting.value;
        adjusting.value = oldValue;
        Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(adjusting, nextValue);
        adjusting.dispatchEvent(new Event('input', {bubbles: true}));
        adjusting.dispatchEvent(new Event('change', {bubbles: true}));
      }
      return;
    }
    adjusting = null;
    var root = scope();
    var from = current(root);
    if (!from) {
      window.scrollBy(direction === 'left' ? -160 : direction === 'right' ? 160 : 0,
        direction === 'up' ? -160 : direction === 'down' ? 160 : 0);
      return;
    }
    if (from !== focused) {
      focus(from);
      return;
    }
    if (root !== document && moveEpisodeFocus(root, from, direction)) return;
    var start = from.getBoundingClientRect();
    if (from.matches(timeline)) {
      if (direction === 'up') {
        hidePlayerControls(from.closest('.player-root'), 'dismissed');
        return;
      }
      if (horizontal) {
        seek(from, direction, repeat);
        return;
      }
      if (direction === 'down' && root.contains(beforeTimeline) && usableControl(beforeTimeline)) {
        focus(beforeTimeline);
        return;
      }
    } else if (!horizontal && from.closest('.player-root')) {
      var bar = root.querySelector(timeline);
      if (shown(bar) && bar.getBoundingClientRect().bottom <= start.top + 4) {
        if (direction === 'up') {
          focus(bar);
          beforeTimeline = from;
        } else {
          clear();
          from.blur();
        }
        return;
      }
    }
    if (location.pathname === '/search' && from.matches(typing) && direction === 'down') {
      var firstResult = Array.from(root.querySelectorAll('a[href^="/movie/"],a[href^="/series/"]')).find(function (item) {
        return usableControl(item);
      });
      if (firstResult) {
        focus(firstResult);
        return;
      }
    }

    var forward = direction === 'right' || direction === 'down';
    var primaryStart = horizontal ? start.left : start.top;
    var primaryEnd = horizontal ? start.right : start.bottom;
    var crossStart = horizontal ? start.top : start.left;
    var crossEnd = horizontal ? start.bottom : start.right;
    var row = horizontal ? scrollParent(from, true) : null;
    var best = Infinity;
    var next = null;
    root.querySelectorAll(focusable).forEach(function (element) {
      if (element === from || element.contains(from) || from.contains(element)) return;
      var rect = element.getBoundingClientRect();
      var min = horizontal ? rect.left : rect.top;
      var max = horizontal ? rect.right : rect.bottom;
      var near = horizontal ? rect.top : rect.left;
      var far = horizontal ? rect.bottom : rect.right;
      var crossLimit = horizontal ? innerHeight : innerWidth;
      if (near >= crossLimit || far <= 0) return;
      var ahead = forward ? min - primaryStart : primaryEnd - max;
      if (ahead < 4) return;
      var crossGap = Math.max(0, near - crossEnd, crossStart - far);
      var alongGap = Math.max(0, forward ? min - primaryEnd : primaryStart - max);
      var crossCenter = Math.abs((crossStart + crossEnd - near - far) / 2);
      if (horizontal && row !== document.scrollingElement && row.contains(element) && crossGap === 0) {
        alongGap /= 2;
      } else if (horizontal && !inViewport(rect)) {
        return;
      }
      var score = alongGap + ahead * 0.1 + crossGap * 3 + crossCenter * 0.35;
      // Check visibility and scroll containers only for candidates that could win.
      if (score >= best || !usableControl(element, rect)) return;
      if (!horizontal) {
        var ownRow = scrollParent(element, true);
        if (ownRow && ownRow !== document.scrollingElement) {
          var rowRect = ownRow.getBoundingClientRect();
          if (rect.right <= rowRect.left || rect.left >= rowRect.right) return;
        }
      }
      best = score;
      next = element;
    });

    if (next) focus(next);
    else if (root !== activePreview) {
      var scroll = scrollParent(from, horizontal);
      var offset = forward ? 180 : -180;
      scroll.scrollBy({left: horizontal ? offset : 0, top: horizontal ? 0 : offset, behavior: 'instant'});
      updateLabel();
    }
  }

  function hitPoint(element) {
    var rect = element.getBoundingClientRect();
    var left = Math.max(0, rect.left);
    var right = Math.min(innerWidth, rect.right);
    var top = Math.max(0, rect.top);
    var bottom = Math.min(innerHeight, rect.bottom);
    if (left >= right || top >= bottom) return null;
    var samples = [[0.5, 0.5], [0.25, 0.5], [0.75, 0.5], [0.5, 0.25], [0.5, 0.75]];
    for (var i = 0; i < samples.length; i++) {
      var x = left + (right - left) * samples[i][0];
      var y = top + (bottom - top) * samples[i][1];
      var hit = document.elementFromPoint(x, y);
      if (hit && element.contains(hit)) return {x: x / innerWidth, y: y / innerHeight};
    }
    return null;
  }

  function select() {
    if (watching()) wakePlayer();
    if (watching() && !shown(focused) && scope() === document && document.querySelector('video')) {
      return {playback: true};
    }
    var root = scope();
    var element = current(root);
    if (!element) return null;
    focus(element);
    if (element.matches(timeline)) return null;
    if (element.matches('input[type=range]')) {
      adjusting = adjusting === element ? null : element;
      return null;
    }
    var point = hitPoint(element);
    if (element.matches('iframe') && point) {
      point.pointer = true;
      return point;
    }
    if (point) {
      point.keyboard = element.matches(typing);
      return point;
    }
    // Some site controls only appear on hover or sit behind a decorative overlay.
    if (!element.matches(typing + ',iframe') && typeof element.click === 'function') element.click();
    return null;
  }

  function finishEditing() {
    var active = document.activeElement;
    if (!active || !active.matches(typing)) return false;
    active.blur();
    return true;
  }

  function back() {
    if (shown(focused) && focused.matches(timeline)) {
      if (shown(beforeTimeline)) focus(beforeTimeline);
      else clear();
      return true;
    }
    if (adjusting) {
      adjusting = null;
      return true;
    }
    if (finishEditing()) return true;
    var root = scope();
    if (root !== document && root.matches(episodePanel)) {
      var backToEpisodes = root.querySelector('button[aria-label="Back to episodes"]');
      if (shown(backToEpisodes)) {
        backToEpisodes.click();
        setTimeout(function () {
          var season = root.querySelector(seasonButton);
          if (shown(season)) focus(season);
        }, 0);
        return true;
      }
      var episodes = document.querySelector('.player-root button[aria-label="Episodes"]');
      if (shown(episodes)) {
        episodes.click();
        focus(episodes);
        return true;
      }
    }
    var fullscreen = document.fullscreenElement || document.webkitFullscreenElement;
    if (fullscreen) {
      if (document.exitFullscreen) document.exitFullscreen();
      else if (document.webkitExitFullscreen) document.webkitExitFullscreen();
      return true;
    }
    if (root !== document) {
      var close = controls(root).find(function (element) {
        return /^(close|cancel|dismiss)( |$)/i.test(element.getAttribute('aria-label') || (element.textContent || "").trim());
      });
      if (close) {
        close.click();
        return true;
      }
      if (root.matches('dialog[open]')) {
        root.close();
        return true;
      }
      if (root.matches('.menu-slide-panel') && shown(playerMenuButton)) {
        playerMenuButton.click();
        focus(playerMenuButton);
        return true;
      }
    }
    var expanded = focused && focused.closest('[aria-haspopup][aria-expanded=true]');
    if (!expanded && focused) expanded = document.querySelector('[aria-haspopup=listbox][aria-expanded=true]');
    if (expanded && shown(expanded)) {
      expanded.click();
      focus(expanded);
      return true;
    }
    return false;
  }

  function focusFailedServer(url) {
    var failed = new URL(url);
    failed.hash = '';
    var frame = Array.from(document.querySelectorAll('iframe[src]')).find(function (item) {
      if (!shown(item) || !inViewport(item.getBoundingClientRect())) return false;
      var source = new URL(item.src, document.baseURI);
      source.hash = '';
      return source.href === failed.href;
    });
    if (!frame) return null;
    return 'unavailable';
  }

  function playerOption(name) {
    function open() {
      var picker = Array.from(document.querySelectorAll('button[aria-label],button[title]')).find(function (button) {
        return (button.getAttribute('aria-label') || button.title) === name && shown(button);
      });
      if (!picker) return false;
      focus(picker);
      picker.click();
      return true;
    }
    if (!watching()) return false;
    wakePlayer();
    if (open()) return true;
    if (!document.querySelector('.player-root')) return false;
    setTimeout(open, 120);
    return true;
  }

  window.addEventListener('scroll', updateLabel, true);
  window.addEventListener('resize', updateLabel);
  document.addEventListener('click', function (event) {
    if (!(event.target instanceof Element)) return;
    var player = watching() && event.target.closest('.player-root');
    if (player) showPlayerControls(player);
    var trigger = event.target.closest('button[aria-label="Servers"],button[aria-label="Subtitles"],button[aria-label="Settings"],button[aria-label="Episodes"]');
    var playerScope = document.fullscreenElement || document.webkitFullscreenElement || document;
    if (!trigger || !watching() || scope() !== playerScope) return;
    playerMenuButton = trigger;
    var attempts = trigger.matches('button[aria-label="Episodes"]') ? 5 : 1;
    function focusOpenedPanel() {
      if (playerMenuButton !== trigger || !trigger.isConnected) return;
      var panel = scope();
      if (panel !== playerScope && panel.contains(focused)) return;
      var first = panel !== playerScope && controls(panel).find(function (control) {
        return !/^close$/i.test(control.getAttribute('aria-label') || '') && hitPoint(control);
      });
      if (first) focus(first);
      else if (--attempts > 0) setTimeout(focusOpenedPanel, 180);
    }
    setTimeout(focusOpenedPanel, 180);
  }, true);
  document.addEventListener('pointerdown', function (event) {
    if (focused && !focused.contains(event.target)) clear();
  }, true);
  ['pointerdown', 'pointermove'].forEach(function (type) {
    document.addEventListener(type, function (event) {
      if (!event.isTrusted || !watching() || !(event.target instanceof Element)) return;
      var player = event.target.closest('.player-root');
      if (player) showPlayerControls(player);
    }, true);
  });
  ['play', 'pause', 'ended', 'emptied'].forEach(function (type) {
    document.addEventListener(type, function (event) {
      if (!watching() || !(event.target instanceof HTMLVideoElement)) return;
      var player = event.target.closest('.player-root');
      if (!player) return;
      showPlayerControls(player);
      revealSiteControls(player);
    }, true);
  });
  document.addEventListener('focusin', function (event) {
    if (focused && focused !== event.target && event.target !== document.body) clear();
  });
  window.addEventListener('popstate', clear);
  function syncPageControls() {
    syncPlayerTitle();
    syncPreviewFocus();
  }
  new MutationObserver(syncPageControls).observe(document.body, {childList: true, subtree: true, characterData: true});
  syncPageControls();
  var player = watching() && document.querySelector('.player-root');
  if (player) showPlayerControls(player);
  window.cineJoyTvRemote = {
    move: move, select: select, back: back, clear: clear, finishEditing: finishEditing,
    focusFailedServer: focusFailedServer, playerOption: playerOption
  };
  var first = preferred(scope());
  if (!focused && first) focus(first);
})();
