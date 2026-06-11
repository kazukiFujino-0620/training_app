// 前回記録パネル — 再利用可能ファクトリ
// 使い方: const panel = createPrevRecordPanel(containerEl); panel.load('ベンチプレス');
function createPrevRecordPanel(containerEl) {
    if (!containerEl) return { load: () => {}, clear: () => {} };

    let sessions = [], pr = null, idx = 0;

    containerEl.innerHTML =
        '<div class="prev-record-panel-inner" style="display:none;">' +
          '<div class="prev-record-header">' +
            '<button type="button" class="prev-nav-btn btn-prev-inner" style="display:none;">&#8249;</button>' +
            '<span class="prev-session-label"></span>' +
            '<button type="button" class="prev-nav-btn btn-next-inner" style="display:none;">&#8250;</button>' +
          '</div>' +
          '<div class="prev-sets-container-inner"></div>' +
          '<div class="prev-pr-inner" style="display:none;"></div>' +
        '</div>' +
        '<div class="prev-record-empty-inner" style="display:none; padding:8px; text-align:center; color:var(--text-muted,#888); font-size:0.85rem;">' +
          'この種目の記録はまだありません。今日が最初の記録です！' +
        '</div>';

    const panelEl     = containerEl.querySelector('.prev-record-panel-inner');
    const emptyEl     = containerEl.querySelector('.prev-record-empty-inner');
    const setsEl      = containerEl.querySelector('.prev-sets-container-inner');
    const prEl        = containerEl.querySelector('.prev-pr-inner');
    const labelEl     = containerEl.querySelector('.prev-session-label');
    const btnPrev     = containerEl.querySelector('.btn-prev-inner');
    const btnNext     = containerEl.querySelector('.btn-next-inner');

    btnPrev.addEventListener('click', function() { navigate(-1); });
    btnNext.addEventListener('click', function() { navigate(1); });

    function navigate(dir) {
        var next = idx + dir * -1;
        if (next < 0 || next >= sessions.length) return;
        idx = next;
        render();
    }

    function render() {
        var s = sessions[idx];
        labelEl.textContent = prevRecordLabel(idx, s.date);
        setsEl.innerHTML = s.sets.map(function(set) {
            var typePrefix = '';
            if (set.setType === 'WARMUP') {
                typePrefix = '<span class="prev-set-type">[WU]</span> ';
            } else if (set.setType === 'DROP') {
                typePrefix = '<span class="prev-set-type prev-set-type-drop">[DROP]</span> ';
            }
            return '<div class="prev-set-row">' + typePrefix + 'Set' + set.setNumber + ': ' +
                   set.weight + 'kg × ' + set.reps + '回</div>';
        }).join('');
        if (pr) {
            prEl.textContent = 'PR: ' + pr.maxWeight + 'kg × ' + pr.maxReps +
                               '回 (' + prevRecordFmtDate(pr.achievedDate) + ' 達成)';
            prEl.style.display = '';
        } else {
            prEl.style.display = 'none';
        }
        btnPrev.style.display = idx < sessions.length - 1 ? '' : 'none';
        btnNext.style.display = idx > 0 ? '' : 'none';
    }

    async function load(itemName) {
        clear();
        if (!itemName) return;
        var controller = new AbortController();
        var timeout = setTimeout(function() { controller.abort(); }, 3000);
        try {
            var res = await fetch(
                '/api/previous-training?itemName=' + encodeURIComponent(itemName),
                { signal: controller.signal }
            );
            clearTimeout(timeout);
            if (!res.ok) return;
            var data = await res.json();
            sessions = data.sessions || [];
            pr = data.pr || null;
            idx = 0;
            if (sessions.length === 0) {
                emptyEl.style.display = '';
            } else {
                panelEl.style.display = '';
                render();
            }
        } catch (e) {
            // タイムアウト・ネットワークエラーは無視
        }
    }

    function clear() {
        sessions = []; pr = null; idx = 0;
        panelEl.style.display = 'none';
        emptyEl.style.display = 'none';
    }

    return { load: load, clear: clear };
}

function prevRecordLabel(idx, dateStr) {
    var labels = ['前回', '前々回', '3回前', '4回前',
                  '5回前', '6回前', '7回前'];
    var label = labels[idx] || ((idx + 1) + '回前');
    var d = new Date(dateStr);
    return label + ' (' + (d.getMonth() + 1) + '/' + d.getDate() + ')';
}

function prevRecordFmtDate(dateStr) {
    var d = new Date(dateStr);
    return (d.getMonth() + 1) + '/' + d.getDate();
}
