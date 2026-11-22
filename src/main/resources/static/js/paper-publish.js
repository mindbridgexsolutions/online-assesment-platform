    (function () {
        var boxes = Array.prototype.slice.call(document.querySelectorAll('.pick')), rows = document.querySelectorAll('.stu');
        function count() {
            var n = boxes.filter(function (b) { return b.checked; }).length;
            document.getElementById('count').textContent = n + ' selected';
            document.getElementById('n').textContent = n;
            document.getElementById('go').disabled = n === 0;
        }
        boxes.forEach(function (b) { b.addEventListener('change', count); });
        document.getElementById('find').addEventListener('input', function (e) {
            var q = e.target.value.toLowerCase();
            rows.forEach(function (r) { r.classList.toggle('d-none', q && r.dataset.q.toLowerCase().indexOf(q) < 0); });
        });
        document.getElementById('all').addEventListener('click', function () {
            rows.forEach(function (r) { if (!r.classList.contains('d-none')) { r.querySelector('.pick').checked = true; } }); count();
        });
        document.getElementById('none').addEventListener('click', function () { boxes.forEach(function (b) { b.checked = false; }); count(); });
        document.querySelectorAll('.batch').forEach(function (btn) {
            btn.addEventListener('click', function () {
                var ids = btn.dataset.ids.split(',');
                boxes.forEach(function (b) { if (ids.indexOf(b.value) >= 0) { b.checked = true; } }); count();
            });
        });
        count();
    })();
    