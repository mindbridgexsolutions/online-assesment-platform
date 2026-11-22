/* Shared behaviour. No inline handlers anywhere: the Content-Security-Policy forbids them. */
(function () {
    'use strict';

    // Confirmation dialogs: <button data-confirm="Delete this?"> or <a data-confirm="...">
    document.addEventListener('click', function (e) {
        var el = e.target.closest('[data-confirm]');
        if (el && !window.confirm(el.getAttribute('data-confirm'))) {
            e.preventDefault();
            e.stopPropagation();
        }
    }, true);

    // Prevent accidental double submits.
    document.addEventListener('submit', function (e) {
        var form = e.target;
        if (form.dataset.noLock) {
            return;
        }
        if (form.dataset.submitting) {
            e.preventDefault();
            return;
        }
        form.dataset.submitting = '1';
        setTimeout(function () {
            form.querySelectorAll('button[type=submit],input[type=submit]').forEach(function (b) {
                b.disabled = true;
            });
        }, 0);
        // re-enable if the user navigates back (bfcache)
        window.addEventListener('pageshow', function () {
            delete form.dataset.submitting;
            form.querySelectorAll('button[type=submit],input[type=submit]').forEach(function (b) {
                b.disabled = false;
            });
        });
    });

    // Auto-dismiss flash alerts.
    document.querySelectorAll('[data-autodismiss]').forEach(function (el) {
        setTimeout(function () {
            if (window.bootstrap) {
                window.bootstrap.Alert.getOrCreateInstance(el).close();
            }
        }, parseInt(el.getAttribute('data-autodismiss'), 10) || 6000);
    });

    // Auto-submit filter forms when a <select data-autosubmit> changes.
    document.addEventListener('change', function (e) {
        var t = e.target;
        if (t.matches && t.matches('[data-autosubmit]') && t.form) {
            t.form.submit();
        }
    });

    // Toggle a block by checkbox: <input type=checkbox data-toggle-target="#id">
    document.querySelectorAll('[data-toggle-target]').forEach(function (cb) {
        var target = document.querySelector(cb.getAttribute('data-toggle-target'));
        if (!target) {
            return;
        }
        var apply = function () {
            target.classList.toggle('d-none', !cb.checked);
        };
        cb.addEventListener('change', apply);
        apply();
    });
})();

/* Print / CSV download helpers (CSP-safe replacements for inline onclick handlers). */
(function () {
    'use strict';

    document.addEventListener('click', function (e) {
        var printBtn = e.target.closest('[data-print]');
        if (printBtn) {
            window.print();
            return;
        }
        var csvBtn = e.target.closest('[data-csv-from]');
        if (csvBtn) {
            var table = document.querySelector(csvBtn.getAttribute('data-csv-from'));
            if (!table) {
                return;
            }
            var quote = function (v) {
                v = String(v).replace(/\s+/g, ' ').trim();
                // neutralise spreadsheet formula injection
                if (/^[=+\-@]/.test(v)) {
                    v = "'" + v;
                }
                return '"' + v.replace(/"/g, '""') + '"';
            };
            var lines = [];
            table.querySelectorAll('tr').forEach(function (tr) {
                var cells = [];
                tr.querySelectorAll('th,td').forEach(function (td) {
                    cells.push(quote(td.textContent));
                });
                lines.push(cells.join(','));
            });
            var blob = new Blob(['﻿' + lines.join('\r\n')], {type: 'text/csv;charset=utf-8'});
            var a = document.createElement('a');
            a.href = URL.createObjectURL(blob);
            a.download = csvBtn.getAttribute('data-csv-name') || 'export.csv';
            document.body.appendChild(a);
            a.click();
            setTimeout(function () {
                URL.revokeObjectURL(a.href);
                a.remove();
            }, 500);
        }
    });
})();

// click or tap a diagram to enlarge it; click again or press Esc to close
(function () {
    document.addEventListener('click', function (e) {
        var img = e.target.closest ? e.target.closest('img.q-image') : null;
        if (!img) { return; }
        e.preventDefault();
        var box = document.createElement('div');
        box.className = 'img-zoom';
        box.setAttribute('role', 'dialog');
        box.setAttribute('aria-label', 'Enlarged picture');
        var big = document.createElement('img');
        big.src = img.src;
        big.alt = img.alt || 'Enlarged picture';
        box.appendChild(big);
        function close() { box.remove(); document.removeEventListener('keydown', onKey); }
        function onKey(ev) { if (ev.key === 'Escape') { close(); } }
        box.addEventListener('click', close);
        document.addEventListener('keydown', onKey);
        document.body.appendChild(box);
    });
})();

// Auto-submit: <select data-autosubmit> reloads its form when changed (inline handlers are blocked by the CSP).
document.addEventListener('change', function (e) {
    var el = e.target.closest ? e.target.closest('[data-autosubmit]') : null;
    if (el && el.form) { el.form.submit(); }
});

/* Checkbox lists with search and "select all": <input data-filter-list="#list">, <button data-check-all="#list">, <button data-check-none="#list">,
   <span data-check-count="#list">. Select all / clear apply to the rows that match the search. */
(function () {
    'use strict';
    function rows(list) { return Array.prototype.slice.call(list.querySelectorAll('[data-search]')); }
    function visibleBoxes(list) {
        return rows(list).filter(function (r) { return !r.classList.contains('d-none'); })
            .map(function (r) { return r.querySelector('input[type=checkbox]'); }).filter(Boolean);
    }
    function count(list) {
        var n = list.querySelectorAll('input[type=checkbox]:checked').length;
        document.querySelectorAll('[data-check-count="#' + list.id + '"]').forEach(function (el) { el.textContent = n + ' selected'; });
    }
    document.addEventListener('input', function (e) {
        var box = e.target.closest ? e.target.closest('[data-filter-list]') : null;
        if (!box) { return; }
        var list = document.querySelector(box.getAttribute('data-filter-list'));
        if (!list) { return; }
        var q = box.value.trim().toLowerCase();
        rows(list).forEach(function (r) { r.classList.toggle('d-none', q !== '' && r.getAttribute('data-search').toLowerCase().indexOf(q) < 0); });
        var none = list.querySelector('[data-no-match]');
        if (none) { none.classList.toggle('d-none', visibleBoxes(list).length > 0); }
    });
    document.addEventListener('click', function (e) {
        var btn = e.target.closest ? e.target.closest('[data-check-all],[data-check-none]') : null;
        if (!btn) { return; }
        var all = btn.hasAttribute('data-check-all');
        var list = document.querySelector(btn.getAttribute(all ? 'data-check-all' : 'data-check-none'));
        if (!list) { return; }
        visibleBoxes(list).forEach(function (cb) { cb.checked = all; });
        count(list);
    });
    document.addEventListener('change', function (e) {
        var list = e.target.closest ? e.target.closest('[data-check-list]') : null;
        if (list) { count(list); }
    });
    document.querySelectorAll('[data-check-list]').forEach(count);
})();
