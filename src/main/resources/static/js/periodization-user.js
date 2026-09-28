/*
 * 機能見直し-1-#3 期分けプログラム画面（/user/program-cycle）。モックアップ版11の01/02/03/04/06/08。
 * 表示の切り替え:
 *   08 トレーナーからの案（返事待ち・予約中）は常に最上部
 *   04 サイクル終了（cycleCompleted）→ 3択
 *   02 実施中のサイクルあり → 週送りタイムライン
 *   01 上記以外 → プリセット選択（「自分で組む」で06）
 */
(function () {
  'use strict';
  const esc = PZ.esc;
  const root = document.getElementById('pz-root');
  const proposalSlot = document.getElementById('pz-proposal');
  const mainSlot = document.getElementById('pz-main');

  const state = { today: null, cycle: null, proposals: { pending: [], scheduled: [] }, presets: [], rules: null, week: 1 };

  function fmtDate(d) {
    if (!d) return '';
    const s = String(d).slice(0, 10).split('-');
    return Number(s[1]) + '/' + Number(s[2]);
  }
  function dayBefore(d) {
    const x = new Date(String(d).slice(0, 10) + 'T00:00:00');
    x.setDate(x.getDate() - 1);
    return (x.getMonth() + 1) + '/' + x.getDate();
  }
  function tierLabel(t) {
    return { BEGINNER_PRESET: 'プリセット', INTERMEDIATE_CUSTOM: '自分で組んだプログラム', TRAINER_MANAGED: 'トレーナー作成' }[t] || '';
  }

  async function load() {
    // today を先に呼ぶ（サイクル終了の判定・予約した案の自動開始がここで行われるため）
    state.today = await PZ.api('GET', '/api/periodization/today');
    const results = await Promise.all([
      PZ.api('GET', '/api/periodization/cycle'),
      PZ.api('GET', '/api/periodization/proposals'),
      PZ.loadParts(),
    ]);
    state.cycle = results[0];
    state.proposals = results[1] || { pending: [], scheduled: [] };
    if (state.cycle) state.week = state.cycle.currentWeekNumber;
    render();
  }

  function render() {
    renderProposals();
    if (state.today && state.today.cycleCompleted) renderCompleted();
    else if (state.cycle) renderTimeline();
    else renderPresets(null);
  }

  // ---------- 本人による切り替え前の確認（予約中の案がある場合だけ） ----------
  function withSwitchConfirm(slot, proceed) {
    const scheduled = state.proposals.scheduled || [];
    if (scheduled.length === 0) { proceed(); return; }
    const s = scheduled[0];
    slot.innerHTML =
      '<div class="pz-confirm" role="alert"><div class="pz-confirm-line"><span class="pz-st scheduled">予約中</span> ' +
      '予約中の「' + esc(s.name) + '」（' + esc(s.trainerName || '') + 'トレーナーの案）は取り消されます。' +
      esc(s.trainerName || '') + 'トレーナーの画面には「本人が別のプログラムに切り替えたため取り消し」と表示されます。</div>' +
      '<div class="pz-actions"><button type="button" class="btn btn-outline" data-confirm="no">やめる</button>' +
      '<button type="button" class="btn btn-primary" data-confirm="yes">取り消して切り替える</button></div></div>';
    slot.querySelector('[data-confirm="no"]').onclick = function () { slot.innerHTML = ''; };
    slot.querySelector('[data-confirm="yes"]').onclick = function () { slot.innerHTML = ''; proceed(); };
  }

  // ---------- 08 トレーナーからの案 ----------
  function renderProposals() {
    const p = (state.proposals.pending || [])[0];
    const s = (state.proposals.scheduled || [])[0];
    let html = '';
    if (s) {
      html += '<div class="pz-proposal" data-testid="scheduled-card"><span class="pz-st scheduled">予約中</span>' +
        (s.contentUpdatedAt ? ' <span class="pz-meta" style="display:inline;">トレーナーが内容を更新しました（' + fmtDate(s.contentUpdatedAt) + '）</span>' : '') +
        '<div style="margin-top:6px;font-size:0.88rem;color:var(--text-primary);">「' + esc(s.name) + '」は、今のプログラム' +
        (s.scheduledStartDate ? '（' + dayBefore(s.scheduledStartDate) + 'まで）が終わった翌日の<b>' + fmtDate(s.scheduledStartDate) + 'から</b>' : 'が終わった翌日から') +
        '始まります。</div><div class="pz-meta">しばらくアプリを開かなかった場合も' + (s.scheduledStartDate ? fmtDate(s.scheduledStartDate) : '終了日の翌日') +
        '開始として扱うため、開いた時点で途中の週から始まります。</div>' +
        '<button type="button" class="pz-link" data-content="' + s.id + '">案の中身（週ごとの強度・曜日ごとの種目）を見る &rsaquo;</button><div class="pz-content-slot"></div></div>';
    }
    if (p) {
      const hasActive = state.today && state.today.hasActiveCycle;
      const activeName = state.cycle ? state.cycle.name : null;
      html += '<div class="pz-proposal" data-testid="proposal-card">' +
        '<div style="display:flex;justify-content:space-between;gap:8px;"><span class="pz-st pending">トレーナーからの案</span><span class="pz-meta">' + fmtDate(p.sentAt) + ' 受信</span></div>' +
        '<div class="pz-h" style="margin:6px 0 2px;">' + esc(p.name) + '</div>' +
        '<div class="pz-meta">' + esc(p.trainerName || '') + 'トレーナーより ・ ' + p.totalWeeks + '週構成' +
        (p.contentUpdatedAt ? ' ・ ' + fmtDate(p.contentUpdatedAt) + 'に内容を更新' : '') + '</div>';
      if (hasActive && activeName) {
        html += '<div class="pz-current" style="margin:12px 0;">いま実施中: ' + esc(activeName) + '（第' + state.cycle.currentWeekNumber + '週）</div>';
      } else {
        html += '<div style="height:10px;"></div>';
      }
      html += '<div class="pz-choice-grid">';
      if (hasActive) {
        html += '<button type="button" class="pz-choice primary" data-respond="START_NOW"><div class="ttl">今すぐ切り替える</div><div class="desc">実施中のプログラムを終了し、この案で今日から始めます</div></button>';
        if (s) {
          html += '<button type="button" class="pz-choice" disabled><div class="ttl">今のプログラムが終わったら開始</div><div class="desc">予約中の「' + esc(s.name) + '」があるため、この案は予約できません</div></button>';
        } else {
          const start = state.cycle ? new Date(String(state.cycle.startDate) + 'T00:00:00') : null;
          let startText = '';
          if (start) { start.setDate(start.getDate() + 7 * state.cycle.totalWeeks); startText = '終了日（' + dayBefore(start.toISOString().slice(0, 10)) + '）の翌日、<b>' + (start.getMonth() + 1) + '/' + start.getDate() + 'から</b>'; }
          html += '<button type="button" class="pz-choice" data-respond="SCHEDULE"><div class="ttl">今のプログラムが終わったら開始</div><div class="desc">「' + esc(activeName || '') + '」の' + startText + '自動で始まります（予約）</div></button>';
        }
      } else {
        html += '<button type="button" class="pz-choice primary" data-respond="START_NOW"><div class="ttl">このプログラムで始める</div><div class="desc">この案で今日から始めます</div></button>';
      }
      html += '<button type="button" class="pz-choice" data-respond="DECLINE"><div class="ttl">断る</div><div class="desc">この案は使いません。トレーナーには「断られました」と表示されます</div></button></div>';
      html += '<div class="pz-confirm-slot"></div>';
      html += '<button type="button" class="pz-link" data-content="' + p.id + '">案の中身（週ごとの強度・曜日ごとの種目）を見る &rsaquo;</button><div class="pz-content-slot"></div></div>';
    }
    proposalSlot.innerHTML = html;
  }

  proposalSlot.addEventListener('click', async function (e) {
    const contentBtn = e.target.closest('[data-content]');
    if (contentBtn) {
      const slot = contentBtn.parentElement.querySelector('.pz-content-slot');
      if (slot.innerHTML) { slot.innerHTML = ''; return; }
      try {
        const c = await PZ.api('GET', '/api/periodization/proposals/' + contentBtn.dataset.content + '/content');
        slot.innerHTML = contentHtml(c);
      } catch (err) { slot.innerHTML = '<div class="pz-error">' + esc(err.message) + '</div>'; }
      return;
    }
    const btn = e.target.closest('[data-respond]');
    if (!btn) return;
    const p = state.proposals.pending[0];
    const card = btn.closest('.pz-proposal');
    const slot = card.querySelector('.pz-confirm-slot');
    const response = btn.dataset.respond;
    const send = async function () {
      try {
        await PZ.api('POST', '/api/periodization/proposals/' + p.id + '/respond', { response: response });
        await load();
      } catch (err) { slot.innerHTML = '<div class="pz-error">' + esc(err.message) + '</div>'; }
    };
    if (response === 'DECLINE') {
      slot.innerHTML = '<div class="pz-confirm"><div class="pz-confirm-line">「' + esc(p.name) + '」の案を断りますか？</div>' +
        '<div class="pz-actions"><button type="button" class="btn btn-outline" data-c="no">やめる</button><button type="button" class="btn btn-primary" data-c="yes">断る</button></div></div>';
      slot.querySelector('[data-c="no"]').onclick = function () { slot.innerHTML = ''; };
      slot.querySelector('[data-c="yes"]').onclick = send;
    } else if (response === 'START_NOW') {
      withSwitchConfirm(slot, send);
    } else {
      send();
    }
  });

  function contentHtml(c) {
    let html = '<div class="pz-table-wrap" style="margin-top:8px;"><table class="pz-table"><thead><tr><th>週</th><th>目標強度</th><th>曜日ごとの部位と種目</th></tr></thead><tbody>';
    (c.weeks || []).forEach(function (w) {
      const days = (c.days || []).filter(function (d) { return d.weekNumber === w.weekNumber; });
      html += '<tr class="' + (w.deload ? 'deload' : '') + '"><td>第' + w.weekNumber + '週' + (w.deload ? '<br><span class="pz-deload-badge">ディロード</span>' : '') + '</td><td>' + Number(w.targetIntensityPct).toFixed(1) + '%</td><td>' +
        (days.length ? days.map(function (d) {
          return PZ.DAY_LABELS[d.dayOfWeek] + ' ' + esc(PZ.partName(d.partCode)) + ': ' + (d.items || []).map(function (i) { return esc(i.itemName) + '×' + i.targetSets; }).join('、');
        }).join('<br>') : '（未設定）') + '</td></tr>';
    });
    return html + '</tbody></table></div>';
  }

  // ---------- 01 プリセット選択 ----------
  async function renderPresets(renewMode) {
    if (!state.presets.length) state.presets = (await PZ.api('GET', '/api/periodization/presets')) || [];
    let selected = null;
    const cats = {};
    state.presets.forEach(function (p) { (cats[p.purposeCategory] = cats[p.purposeCategory] || []).push(p); });
    const catLabel = { BULK: '増量 (BULK)', CUT: '減量 (CUT)', MAINTENANCE: '維持 (MAINTENANCE)', STRENGTH: '筋力 (STRENGTH)' };
    let html = '<div class="pz-card" data-testid="preset-list"><div class="pz-card-head"><div><div class="pz-h">' +
      (renewMode ? '次のプログラムを選ぶ' : '期分けプログラム') + '</div><div class="pz-meta">' +
      (renewMode ? '別のプリセットを選んで新しいサイクルを始めます' : 'まだプログラムを開始していません') + '</div></div>' +
      (renewMode ? '<button type="button" class="pz-link" data-act="back">&lsaquo; 戻る</button>' : '') + '</div>';
    if (!state.presets.length) {
      html += '<div class="pz-empty">選べるプリセットがまだありません。下の「自分で組む」から始められます。</div>';
    }
    Object.keys(cats).forEach(function (cat) {
      html += '<div class="pz-cat">' + esc(catLabel[cat] || cat) + '</div>';
      cats[cat].forEach(function (p) {
        html += '<button type="button" class="pz-preset" data-preset="' + p.id + '"><div><div class="name">' + esc(p.name) + '</div><div class="meta">' +
          p.totalWeeks + '週構成' + (p.description ? '・' + esc(p.description) : '') + '</div></div><span class="pz-tag">初心者向け</span></button>';
      });
    });
    html += '<button type="button" class="btn btn-primary w-full" data-act="start" style="margin-top:8px;" disabled>プログラムを選んでください</button>' +
      '<div class="pz-confirm-slot"></div>' +
      '<div class="pz-self-build"><div><div class="name" style="font-weight:600;color:var(--text-primary);">プリセットを使わず自分で組む</div>' +
      '<div class="pz-meta">週数・週ごとの強度・曜日ごとの部位と種目を自分で決めます</div></div>' +
      '<button type="button" class="pz-link" data-act="self">自分で組む &rsaquo;</button></div></div>';
    mainSlot.innerHTML = html;
    const startBtn = mainSlot.querySelector('[data-act="start"]');
    mainSlot.onclick = function (e) {
      const presetBtn = e.target.closest('[data-preset]');
      if (presetBtn) {
        mainSlot.querySelectorAll('.pz-preset').forEach(function (b) { b.classList.remove('selected'); });
        presetBtn.classList.add('selected');
        selected = state.presets.find(function (p) { return String(p.id) === presetBtn.dataset.preset; });
        startBtn.disabled = false;
        startBtn.textContent = '選択中: ' + selected.name + ' を開始する';
        return;
      }
      const act = e.target.closest('[data-act]');
      if (!act) return;
      if (act.dataset.act === 'back') { renderCompleted(); }
      else if (act.dataset.act === 'self') { renderBuilder(renewMode); }
      else if (act.dataset.act === 'start' && selected) {
        withSwitchConfirm(mainSlot.querySelector('.pz-confirm-slot'), async function () {
          try {
            if (renewMode) await PZ.api('POST', '/api/periodization/renew', { choice: 'CHOOSE_NEW_PRESET', presetProgramId: selected.id });
            else await PZ.api('POST', '/api/periodization/adopt', { presetProgramId: selected.id });
            await load();
          } catch (err) { PZ.showError(mainSlot.querySelector('.pz-card'), err.message); }
        });
      }
    };
  }

  // ---------- 06 白紙から組む ----------
  async function renderBuilder(fromRenew) {
    if (!state.rules) state.rules = await PZ.api('GET', '/api/periodization/custom/rules');
    mainSlot.onclick = null;
    mainSlot.innerHTML = '<div class="pz-card"><div class="pz-card-head"><div><div class="pz-h">自分でプログラムを組む</div>' +
      '<div class="pz-meta">作成すると、自分で組んだプログラムとして開始します</div></div>' +
      '<button type="button" class="pz-link" data-back="1">&lsaquo; プリセット一覧に戻る</button></div><div class="pz-builder"></div></div>';
    mainSlot.querySelector('[data-back]').onclick = function () { renderPresets(fromRenew); };
    await PZ.Builder(mainSlot.querySelector('.pz-builder'), {
      rules: state.rules,
      submitLabel: 'この内容で開始する',
      beforeSubmit: withSwitchConfirm,
      onSubmit: async function (payload) {
        const res = await PZ.api('POST', '/api/periodization/custom', payload);
        await load();
        if (res && res.warnings && res.warnings.length) {
          const note = document.createElement('div');
          note.className = 'pz-caution';
          note.textContent = res.warnings.join(' ');
          mainSlot.prepend(note);
        }
      },
    });
  }

  // ---------- 04 サイクル終了 ----------
  function renderCompleted() {
    mainSlot.innerHTML = '<div class="pz-card" data-testid="completed"><div class="pz-card-head"><div><div class="pz-h">サイクルが終了しました</div>' +
      '<div class="pz-meta">次はどうしますか？</div></div></div>' +
      '<button type="button" class="pz-choice primary" data-renew="REPEAT_SAME"><div class="ttl">同じ内容で継続する</div><div class="desc">直前のサイクルの内容（曜日ごとの編集結果を含む）をそのまま引き継いで、新しいサイクルを始めます</div></button>' +
      '<button type="button" class="pz-choice" data-renew="CHOOSE_NEW_PRESET"><div class="ttl">別のプリセットを選ぶ</div><div class="desc">プリセット一覧に戻って選び直します</div></button>' +
      '<button type="button" class="pz-choice" data-renew="GO_FREEFORM"><div class="ttl">期分けをやめて通常の週間プログラムに戻る</div><div class="desc">既存の「週間プログラム設定」画面に切り替えます</div></button>' +
      '<div class="pz-confirm-slot"></div></div>';
    mainSlot.onclick = async function (e) {
      const btn = e.target.closest('[data-renew]');
      if (!btn) return;
      const choice = btn.dataset.renew;
      if (choice === 'CHOOSE_NEW_PRESET') { renderPresets(true); return; }
      const run = async function () {
        try {
          await PZ.api('POST', '/api/periodization/renew', { choice: choice });
          if (choice === 'GO_FREEFORM') { window.location.href = '/user/weekly-program'; return; }
          await load();
        } catch (err) { PZ.showError(mainSlot.querySelector('.pz-card'), err.message); }
      };
      if (choice === 'REPEAT_SAME') withSwitchConfirm(mainSlot.querySelector('.pz-confirm-slot'), run);
      else run();
    };
  }

  // ---------- 02 週送りタイムライン ----------
  function renderTimeline() {
    const c = state.cycle;
    const t = state.today || {};
    const w = c.weeks.find(function (x) { return x.weekNumber === state.week; }) || c.weeks[0];
    let html = '<div class="pz-card" data-testid="timeline"><div class="pz-card-head"><div><div class="pz-h">' + esc(c.name) + '</div>' +
      '<div class="pz-meta">' + esc(String(c.startDate)) + '開始・' + esc(tierLabel(c.tier)) + '・第' + c.currentWeekNumber + '週 / 全' + c.totalWeeks + '週</div></div></div>';
    html += '<div class="pz-week-tabs">';
    c.weeks.forEach(function (x) {
      html += '<button type="button" class="pz-week-tab' + (x.weekNumber === state.week ? ' active' : '') + (x.deload ? ' deload' : '') + '" data-week="' + x.weekNumber + '">第' + x.weekNumber + '週' + (x.deload ? ' ディロード' : '') + '</button>';
    });
    html += '</div>';
    if (w) {
      html += '<div class="pz-strip"><div><div class="val">' + Number(w.targetIntensityPct).toFixed(1) + '<span class="pz-meta" style="display:inline;"> % 1RM</span></div><div class="lbl">目標強度</div></div>' +
        (w.deload ? '<span class="pz-deload-badge">ディロード週</span>' : '') + '</div>';
    }
    // 停滞の案内（今週の表示中のみ）
    if (state.week === c.currentWeekNumber && (t.stagnationWarning === 'MILD' || t.stagnationWarning === 'STRONG')) {
      const items = (t.stagnantItems || []).map(function (i) { return esc(i.itemName) + (i.level === 'STRONG' ? '（明確に停滞）' : '（伸び悩み）'); }).join('、');
      html += t.stagnationWarning === 'STRONG'
        ? '<div class="pz-banner strong"><b>明確に停滞しています。ディロード週を挟むことを推奨します。</b><br>停滞している種目: ' + items + '</div>'
        : '<div class="pz-banner mild"><b>そろそろディロードを検討してはどうでしょう。</b><br>伸び悩んでいる種目: ' + items + '</div>';
    }
    PZ.DAY_CODES.forEach(function (code) {
      const d = (w ? w.days : []).find(function (x) { return x.dayOfWeek === code; });
      if (!d || !d.partCode) {
        html += '<div class="pz-day"><div class="pz-day-chip rest">' + PZ.DAY_LABELS[code] + '</div><div class="pz-day-body"><div class="pz-day-items" style="color:var(--text-muted);">休養日</div></div></div>';
        return;
      }
      const names = d.items.map(function (i) { return esc(i.itemName); }).join(' &rarr; ') || '種目が未設定です';
      const weights = d.items.filter(function (i) { return i.targetWeightKg != null; })
        .map(function (i) { return esc(i.itemName) + ' 約' + i.targetWeightKg + 'kg'; }).join('・');
      html += '<div class="pz-day"><div class="pz-day-chip">' + PZ.DAY_LABELS[code] + '</div><div class="pz-day-body"><div class="pz-day-items">' + names + '</div>' +
        '<div class="pz-day-sub">' + esc(PZ.partName(d.partCode)) + (weights ? '・目標重量 ' + weights : '') + '</div></div>' +
        '<button type="button" class="pz-link" data-edit="' + d.dayTemplateId + '" data-code="' + code + '">編集</button></div>';
    });
    html += '</div>';
    mainSlot.innerHTML = html;
    mainSlot.onclick = async function (e) {
      const tab = e.target.closest('[data-week]');
      if (tab) { state.week = Number(tab.dataset.week); renderTimeline(); return; }
      const edit = e.target.closest('[data-edit]');
      if (!edit) return;
      const d = w.days.find(function (x) { return String(x.dayTemplateId) === edit.dataset.edit; });
      await PZ.openItemEditor({
        title: PZ.DAY_LABELS[edit.dataset.code] + '曜日の種目編集',
        sub: '第' + state.week + '週・' + PZ.partName(d.partCode),
        partCode: d.partCode,
        items: d.items,
        onSave: async function (items) {
          await PZ.api('POST', '/api/periodization/day-templates/' + d.dayTemplateId + '/items', { items: items });
          state.cycle = await PZ.api('GET', '/api/periodization/cycle');
          renderTimeline();
        },
      });
    };
  }

  load().catch(function (err) { PZ.showError(root, err.message); });
})();
