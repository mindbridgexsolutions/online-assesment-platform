/* Exam player. The server owns the clock and the answer key; this script only collects answers, autosaves and shows time. */
(function () {
    'use strict';
    var root = document.getElementById('player');
    if (!root) { return; }

    var cfg = {
        session: root.dataset.session,
        endsAt: parseInt(root.dataset.endsAt, 10),
        offset: parseInt(root.dataset.serverNow, 10) - Date.now(),   // server time minus local time
        saveUrl: root.dataset.saveUrl,
        submitUrl: root.dataset.submitUrl,
        focusUrl: root.dataset.focusUrl,
        resultsUrl: root.dataset.resultsUrl,
        reloadUrl: root.dataset.reloadUrl,
        blockCopy: root.dataset.blockCopy === 'true',
        maxFocus: parseInt(root.dataset.maxFocus, 10) || 0,
        focusCount: parseInt(root.dataset.focusCount, 10) || 0
    };
    var csrfToken = (document.querySelector('meta[name=_csrf]') || {}).content;
    var csrfHeader = (document.querySelector('meta[name=_csrf_header]') || {}).content || 'X-XSRF-TOKEN';

    var sections = Array.prototype.slice.call(root.querySelectorAll('.q-card'));
    var paletteBtns = Array.prototype.slice.call(root.querySelectorAll('.palette-btn'));
    var timerEl = document.getElementById('timer');
    var saveState = document.getElementById('save-state');
    var answeredEl = document.getElementById('answered-count');
    var offlineEl = document.getElementById('offline-warning');
    var focusEl = document.getElementById('focus-warning');
    var current = 0;
    var dirty = {};          // qid -> true
    var saving = false;
    var finished = false;
    var saveTimer = null;
    var submitting = false;

    function now() { return Date.now() + cfg.offset; }

    // ------------------------------------------------------------ reading answers from the DOM
    function readAnswer(sec) {
        var type = sec.dataset.type;
        if (type === 'MCQ_SINGLE' || type === 'TRUE_FALSE') {
            var r = sec.querySelector('input[type=radio]:checked');
            return r ? r.value : '';
        }
        if (type === 'MCQ_MULTI') {
            var vals = Array.prototype.map.call(sec.querySelectorAll('input[type=checkbox]:checked'), function (c) { return c.value; });
            return vals.length ? JSON.stringify(vals) : '';
        }
        if (type === 'MATCHING') {
            var map = {}, any = false;
            sec.querySelectorAll('.matching-row').forEach(function (row) {
                var v = row.querySelector('select').value;
                if (v) { map[row.dataset.left] = v; any = true; }
            });
            return any ? JSON.stringify(map) : '';
        }
        var el = sec.querySelector('textarea, input.ans-input[type=text]');
        return el ? el.value : '';
    }
    function isFlagged(sec) { return sec.querySelector('.flag-toggle').checked; }
    function qid(sec) { return parseInt(sec.dataset.qid, 10); }

    function refreshPalette() {
        var answered = 0;
        sections.forEach(function (sec, i) {
            var has = readAnswer(sec).trim() !== '';
            if (has) { answered++; }
            var b = paletteBtns[i];
            b.classList.toggle('answered', has && !isFlagged(sec));
            b.classList.toggle('flagged', isFlagged(sec));
            b.classList.toggle('current', i === current);
            b.classList.toggle('btn-outline-secondary', !has && !isFlagged(sec));
        });
        answeredEl.textContent = answered;
    }

    function show(i) {
        if (i < 0 || i >= sections.length) { return; }
        sections[current].classList.add('d-none');
        current = i;
        sections[current].classList.remove('d-none');
        sections[current].querySelector('.btn-prev').disabled = i === 0;
        sections[current].querySelector('.btn-next').disabled = i === sections.length - 1;
        refreshPalette();
        window.scrollTo(0, 0);
    }

    // ------------------------------------------------------------ autosave
    function markDirty(sec) {
        dirty[qid(sec)] = true;
        refreshPalette();
        saveState.textContent = 'Unsaved changes';
        backup();
        clearTimeout(saveTimer);
        saveTimer = setTimeout(flush, 700);
    }

    function payload(onlyDirty) {
        var list = [];
        sections.forEach(function (sec) {
            if (!onlyDirty || dirty[qid(sec)]) {
                list.push({questionId: qid(sec), answer: readAnswer(sec), flagged: isFlagged(sec)});
            }
        });
        return {answers: list};
    }

    function post(url, body, keepalive) {
        var headers = {'Content-Type': 'application/json', 'X-Player-Session': cfg.session};
        headers[csrfHeader] = csrfToken;
        var opts = {method: 'POST', credentials: 'same-origin', headers: headers, body: JSON.stringify(body || {}), keepalive: !!keepalive};
        // A stalled connection must not freeze autosave: give up after 12 s so the retry logic can run.
        if (!keepalive && typeof AbortController !== 'undefined') {
            var ctl = new AbortController();
            var timer = setTimeout(function () { ctl.abort(); }, 12000);
            opts.signal = ctl.signal;
            return fetch(url, opts).then(function (res) { clearTimeout(timer); return res; }, function (err) { clearTimeout(timer); throw err; });
        }
        return fetch(url, opts);
    }

    // Unsent answers survive a crash or refresh of this tab: they are replayed to the server on the next load.
    var backupKey = 'exam-backup:' + cfg.saveUrl;
    function backup() {
        try {
            if (Object.keys(dirty).length) { sessionStorage.setItem(backupKey, JSON.stringify(payload(true))); }
            else { sessionStorage.removeItem(backupKey); }
        } catch (e) { /* storage unavailable: autosave still works */ }
    }
    function replayBackup() {
        var stored = null;
        try { stored = JSON.parse(sessionStorage.getItem(backupKey) || 'null'); } catch (e) { stored = null; }
        if (!stored || !stored.answers || !stored.answers.length) { return; }
        post(cfg.saveUrl, stored).then(function (res) {
            if (res.ok) {
                try { sessionStorage.removeItem(backupKey); } catch (e) { }
                window.location.reload();
            } else {
                handleError(res);
            }
        }).catch(function () { /* offline: the normal autosave and the next load will try again */ });
    }

    function handleError(res) {
        if (res.status === 409) {
            overlay('This exam is open in another window',
                'Only one window can answer at a time. Reload to continue here (the other window will stop).', 'Reload', cfg.reloadUrl);
            finished = true;
        } else if (res.status === 410 || res.status === 404) {
            go(cfg.resultsUrl);
        } else if (res.status === 401 || res.status === 403) {
            overlay('You were signed out', 'Please sign in again. Your saved answers are kept.', 'Sign in', cfg.reloadUrl);
        }
    }

    function flush() {
        if (saving || finished) { return Promise.resolve(); }
        var ids = Object.keys(dirty);
        if (!ids.length) { return Promise.resolve(); }
        var body = payload(true);
        var sent = {};
        ids.forEach(function (k) { sent[k] = true; });
        saving = true;
        saveState.textContent = 'Saving...';
        return post(cfg.saveUrl, body).then(function (res) {
            saving = false;
            if (res.ok) {
                Object.keys(sent).forEach(function (k) { delete dirty[k]; });
                backup();
                offlineEl.classList.add('d-none');
                saveState.textContent = 'All changes saved';
                if (Object.keys(dirty).length) { flush(); }
            } else {
                saveState.textContent = 'Not saved';
                handleError(res);
            }
        }).catch(function () {
            saving = false;
            saveState.textContent = 'Not saved';
            offlineEl.classList.remove('d-none');
            clearTimeout(saveTimer);
            saveTimer = setTimeout(flush, 4000);
        });
    }
    setInterval(flush, 15000);
    replayBackup();

    root.addEventListener('input', function (e) {
        var sec = e.target.closest('.q-card');
        if (sec && e.target.matches('textarea, input[type=text]')) { markDirty(sec); }
    });
    root.addEventListener('change', function (e) {
        var sec = e.target.closest('.q-card');
        if (!sec) { return; }
        if (e.target.matches('input[type=radio], input[type=checkbox], select, .flag-toggle')) { markDirty(sec); }
    });
    root.addEventListener('click', function (e) {
        var t = e.target;
        if (t.closest('.btn-next')) { show(current + 1); }
        else if (t.closest('.btn-prev')) { show(current - 1); }
        else if (t.closest('.palette-btn')) { show(parseInt(t.closest('.palette-btn').dataset.goto, 10)); }
        else if (t.closest('.clear-answer')) {
            var sec = t.closest('.q-card');
            sec.querySelectorAll('input[type=radio]:checked, input[type=checkbox]:checked').forEach(function (i) { i.checked = false; });
            sec.querySelectorAll('textarea, input[type=text]').forEach(function (i) { i.value = ''; });
            markDirty(sec);
        }
    });

    // ------------------------------------------------------------ timer
    function tick() {
        var left = Math.max(0, Math.floor((cfg.endsAt - now()) / 1000));
        var h = Math.floor(left / 3600), m = Math.floor((left % 3600) / 60), s = left % 60;
        timerEl.textContent = (h > 0 ? h + ':' : '') + (h > 0 && m < 10 ? '0' : '') + m + ':' + (s < 10 ? '0' : '') + s;
        timerEl.classList.toggle('warn', left <= 300 && left > 60);
        timerEl.classList.toggle('danger', left <= 60);
        if (left <= 0 && !submitting && !finished) { submit(true); }
    }
    setInterval(tick, 1000);
    tick();

    // ------------------------------------------------------------ submit
    var confirmOverlay = document.getElementById('overlay-confirm');
    document.getElementById('btn-submit').addEventListener('click', function () {
        refreshPalette();
        var unanswered = sections.length - parseInt(answeredEl.textContent, 10);
        var flagged = sections.filter(isFlagged).length;
        var txt = unanswered > 0 ? 'You have ' + unanswered + ' unanswered question(s). ' : 'All questions are answered. ';
        if (flagged) { txt += flagged + ' marked for review. '; }
        document.getElementById('confirm-text').textContent = txt + 'You cannot change answers after submitting.';
        confirmOverlay.classList.add('show');
    });
    document.getElementById('btn-cancel-submit').addEventListener('click', function () { confirmOverlay.classList.remove('show'); });
    document.getElementById('btn-confirm-submit').addEventListener('click', function () { submit(false); });

    function submit(auto) {
        if (submitting) { return; }
        submitting = true;
        confirmOverlay.classList.remove('show');
        overlay(auto ? 'Time is up' : 'Submitting...', auto ? 'Submitting your answers automatically.' : 'Please wait.');
        var attempt = 0;
        (function send() {
            attempt++;
            post(cfg.submitUrl, payload(false)).then(function (res) {
                if (res.ok || res.status === 410 || res.status === 404) {
                    finished = true;
                    go(cfg.resultsUrl);
                } else if (res.status === 409) {
                    // another window owns the session: submit without answers, the other window's saved answers count
                    post(cfg.submitUrl, {}).then(function () { go(cfg.resultsUrl); });
                } else {
                    retry();
                }
            }).catch(retry);
            function retry() {
                if (attempt < 8) { setTimeout(send, Math.min(1000 * attempt, 5000)); }
                else { overlay('Could not submit', 'Check your connection, then press the button to try again.', 'Try again', '#'); submitting = false; }
            }
        })();
    }

    function go(url) {
        window.removeEventListener('beforeunload', beforeUnload);
        window.location.href = url;
    }

    function overlay(title, text, linkText, href) {
        document.getElementById('message-title').textContent = title;
        document.getElementById('message-text').textContent = text;
        var link = document.getElementById('message-link');
        if (linkText) {
            link.textContent = linkText;
            link.href = href;
            link.classList.remove('d-none');
            if (href === '#') {
                link.onclick = function (ev) { ev.preventDefault(); document.getElementById('overlay-message').classList.remove('show'); };
            } else {
                link.onclick = function () { window.removeEventListener('beforeunload', beforeUnload); };
            }
        } else {
            link.classList.add('d-none');
        }
        document.getElementById('overlay-message').classList.add('show');
    }

    // ------------------------------------------------------------ integrity helpers
    var lastFocusEvent = 0;
    function focusLost() {
        if (finished || submitting) { return; }
        var t = Date.now();
        if (t - lastFocusEvent < 2500) { return; }
        lastFocusEvent = t;
        post(cfg.focusUrl, {}, true).then(function (res) { return res.ok ? res.json() : null; }).then(function (r) {
            if (!r) { return; }
            cfg.focusCount = r.focusLosses;
            var msg = 'Leaving the exam window was recorded (' + r.focusLosses + (cfg.maxFocus ? ' of ' + cfg.maxFocus : '') + ').';
            focusEl.textContent = msg;
            focusEl.classList.remove('d-none');
            if (r.submitted) { finished = true; go(cfg.resultsUrl); }
        }).catch(function () { });
    }
    document.addEventListener('visibilitychange', function () { if (document.hidden) { focusLost(); } });
    window.addEventListener('blur', function () { setTimeout(function () { if (!document.hasFocus()) { focusLost(); } }, 200); });

    if (cfg.blockCopy) {
        ['copy', 'cut', 'paste', 'contextmenu', 'dragstart'].forEach(function (ev) {
            document.addEventListener(ev, function (e) { e.preventDefault(); });
        });
        document.addEventListener('keydown', function (e) {
            if ((e.ctrlKey || e.metaKey) && ['c', 'x', 'v', 'p', 's', 'u'].indexOf(e.key.toLowerCase()) >= 0) { e.preventDefault(); }
        });
    }

    // full screen is required while the exam is running
    var fsOverlay = document.getElementById('fs-overlay');
    var fsSupported = !!document.documentElement.requestFullscreen;
    function inFs() { return !!document.fullscreenElement; }
    function checkFs() {
        if (!fsOverlay || !fsSupported) { return; }
        fsOverlay.classList.toggle('d-none', inFs() || finished || submitting);
    }
    if (fsOverlay && fsSupported) {
        document.getElementById('fs-enter').addEventListener('click', function () {
            document.documentElement.requestFullscreen().catch(function () { });
        });
        document.addEventListener('fullscreenchange', function () {
            if (!inFs()) { focusLost(); }
            checkFs();
        });
        checkFs();
    }

    function beforeUnload(e) {
        if (finished) { return; }
        flushBeacon();
        e.preventDefault();
        e.returnValue = '';
    }
    function flushBeacon() {
        if (Object.keys(dirty).length) { post(cfg.saveUrl, payload(true), true).catch(function () { }); }
    }
    window.addEventListener('beforeunload', beforeUnload);

    // the browser back button should not leave the exam by accident
    history.pushState(null, '', location.href);
    window.addEventListener('popstate', function () { history.pushState(null, '', location.href); });

    // flush answers right after the page is restored from cache with stale data
    window.addEventListener('pageshow', function (e) { if (e.persisted) { location.reload(); } });

    show(0);
})();
