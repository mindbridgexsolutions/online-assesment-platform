/* Question editor: shows the answer-key section for the chosen type and keeps option inputs indexed. */
(function () {
    'use strict';
    var form = document.getElementById('qform');
    if (!form) { return; }
    var typeSel = document.getElementById('type');
    var subjectSel = document.getElementById('subjectId');
    var categorySel = document.getElementById('categoryId');
    var rows = {
        choice: form.querySelector('[data-rows="choice"]'),
        text: form.querySelector('[data-rows="text"]'),
        match: form.querySelector('[data-rows="match"]')
    };
    var existing = Array.prototype.map.call(document.querySelectorAll('#existing-options span'), function (s) {
        return {id: s.dataset.id, text: s.dataset.text, match: s.dataset.match, correct: s.dataset.correct === 'true', image: s.dataset.image};
    }).filter(function (o) { return o.text || o.image; });

    function el(tag, attrs) {
        var e = document.createElement(tag);
        Object.keys(attrs || {}).forEach(function (k) { e.setAttribute(k, attrs[k]); });
        return e;
    }

    function addRow(kind, data) {
        data = data || {};
        var row = el('div', {'class': 'input-group mb-2 opt-row'});
        var hidden = el('input', {type: 'hidden', 'data-f': 'id'});
        hidden.value = data.id || '';
        row.appendChild(hidden);
        if (kind === 'choice') {
            var cbWrap = el('div', {'class': 'input-group-text'});
            var cb = el('input', {type: 'checkbox', 'class': 'form-check-input mt-0', 'data-f': 'correct', 'aria-label': 'Correct', value: 'true'});
            cb.checked = !!data.correct;
            cbWrap.appendChild(cb);
            row.appendChild(cbWrap);
        }
        var t = el('input', {type: 'text', 'class': 'form-control', maxlength: '1000', 'data-f': 'text',
            placeholder: kind === 'match' ? 'Left item' : (kind === 'text' ? 'Answer / keyword' : 'Option text')});
        t.value = data.text || '';
        row.appendChild(t);
        if (kind === 'choice') {
            var file = el('input', {type: 'file', 'class': 'form-control', accept: 'image/png,image/jpeg,image/gif,image/webp', 'data-f': 'image', style: 'max-width:230px', title: 'Optional picture for this option'});
            row.appendChild(file);
            var keep = el('input', {type: 'hidden', 'data-f': 'imagePath'});
            keep.value = data.image || '';
            row.appendChild(keep);
            if (data.image) {
                var thumb = el('img', {src: form.dataset.filesUrl + data.image, 'class': 'q-thumb', alt: 'Option picture', style: 'max-height:38px;margin:0 6px'});
                row.appendChild(thumb);
                var rmWrap = el('span', {'class': 'input-group-text small'});
                var rm = el('input', {type: 'checkbox', 'class': 'form-check-input mt-0 me-1', 'data-f': 'removeImage', value: 'true', 'aria-label': 'Remove picture'});
                rmWrap.appendChild(rm);
                rmWrap.appendChild(document.createTextNode('remove'));
                row.appendChild(rmWrap);
            }
        }
        if (kind === 'match') {
            var arrow = el('span', {'class': 'input-group-text'});
            arrow.textContent = '→';
            row.appendChild(arrow);
            var m = el('input', {type: 'text', 'class': 'form-control', maxlength: '1000', 'data-f': 'matchText', placeholder: 'Right item'});
            m.value = data.match || '';
            row.appendChild(m);
        }
        var del = el('button', {type: 'button', 'class': 'btn btn-outline-danger', 'aria-label': 'Remove'});
        del.textContent = '×';
        del.addEventListener('click', function () { row.remove(); renumber(); });
        row.appendChild(del);
        rows[kind].appendChild(row);
        renumber();
    }

    function renumber() {
        var i = 0;
        form.querySelectorAll('.opt-row').forEach(function (row) {
            var active = !row.closest('.d-none');
            row.querySelectorAll('[data-f]').forEach(function (inp) {
                if (active) { inp.name = 'options[' + i + '].' + inp.dataset.f; } else { inp.removeAttribute('name'); }
            });
            if (active) { i++; }
        });
    }

    function applyType() {
        var type = typeSel.value;
        form.querySelectorAll('[data-types]').forEach(function (sec) {
            sec.classList.toggle('d-none', sec.dataset.types.split(',').indexOf(type) < 0);
        });
        form.querySelectorAll('[data-types-only]').forEach(function (n) {
            n.classList.toggle('d-none', n.dataset.typesOnly !== type);
        });
        form.querySelectorAll('[data-label-for]').forEach(function (n) {
            n.classList.toggle('d-none', n.dataset.labelFor !== type);
        });
        var hint = form.querySelector('[data-multi-hint]');
        if (hint) { hint.classList.toggle('d-none', type !== 'MCQ_MULTI'); }
        if (type === 'MCQ_SINGLE') {
            var checked = form.querySelectorAll('#sec-choice input[data-f=correct]:checked');
            for (var i = 1; i < checked.length; i++) { checked[i].checked = false; }
        }
        renumber();
    }

    form.addEventListener('change', function (e) {
        var t = e.target;
        if (t.matches && t.matches('input[data-f=correct]') && typeSel.value === 'MCQ_SINGLE' && t.checked) {
            form.querySelectorAll('#sec-choice input[data-f=correct]').forEach(function (o) { if (o !== t) { o.checked = false; } });
        }
    });
    form.addEventListener('click', function (e) {
        var add = e.target.closest('[data-add-row]');
        if (add) { addRow(add.dataset.addRow); }
    });
    var coSel = document.getElementById('coId');
    function filterCos() {
        if (!coSel) { return; }
        var sid = subjectSel.value;
        Array.prototype.forEach.call(coSel.options, function (o) {
            var ok = !o.value || o.dataset.subject === sid;
            o.hidden = !ok;
            o.disabled = !ok;
        });
        if (coSel.selectedOptions[0] && coSel.selectedOptions[0].disabled) { coSel.value = ''; }
    }
    subjectSel.addEventListener('change', filterCos);
    filterCos();
    typeSel.addEventListener('change', applyType);
    form.addEventListener('submit', renumber);

    var type = typeSel.value;
    var kind = (type === 'MCQ_SINGLE' || type === 'MCQ_MULTI') ? 'choice'
        : (type === 'MATCHING' ? 'match' : (type === 'FILL_BLANK' || type === 'SHORT_ANSWER' ? 'text' : null));
    if (kind) {
        existing.forEach(function (o) { addRow(kind, o); });
    }
    ['choice', 'text', 'match'].forEach(function (k) {
        var min = k === 'choice' ? 4 : (k === 'match' ? 3 : 1);
        while (rows[k].children.length < min) { addRow(k); }
    });
    applyType();

    subjectSel.addEventListener('change', function () {
        categorySel.innerHTML = '<option value="">None</option>';
        if (!subjectSel.value) { return; }
        fetch(form.dataset.categoriesUrl + '?subjectId=' + encodeURIComponent(subjectSel.value), {credentials: 'same-origin'})
            .then(function (r) { return r.ok ? r.json() : []; })
            .then(function (list) {
                list.forEach(function (c) {
                    var o = document.createElement('option');
                    o.value = c.id;
                    o.textContent = c.name;
                    categorySel.appendChild(o);
                });
            });
    });
})();
