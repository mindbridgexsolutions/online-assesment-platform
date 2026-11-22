    (function () {
        var chips = document.querySelectorAll('#chips button'), cards = document.querySelectorAll('.qcard');
        chips.forEach(function (b) {
            b.addEventListener('click', function () {
                chips.forEach(function (x) { x.classList.remove('active'); });
                b.classList.add('active');
                cards.forEach(function (c) { c.classList.toggle('d-none', b.dataset.f !== 'all' && c.dataset.f !== b.dataset.f); });
            });
        });
    })();
    