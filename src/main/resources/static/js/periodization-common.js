/*
 * 機能見直し-1-#3 期分けプログラム: Web画面の共通部品（モックアップ版11準拠）。
 * - PZ.api: CSRFトークン付きのfetch（エラー時はサーバーのメッセージで例外）
 * - PZ.openItemEditor: 曜日別の種目編集モーダル（03）。並べ替え・セット数・追加・削除
 * - PZ.Builder: 白紙から組むフォーム（06）。トレーナーの案の編集画面でも使う
 */
(function () {
  'use strict';

  const DAY_CODES = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];
  const DAY_LABELS = { MON: '月', TUE: '火', WED: '水', THU: '木', FRI: '金', SAT: '土', SUN: '日' };

  function esc(v) {
    return String(v == null ? '' : v)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  async function api(method, url, body) {
    const headers = { Accept: 'application/json' };
    const token = document.querySelector('meta[name="_csrf"]');
    const header = document.querySelector('meta[name="_csrf_header"]');
    if (token && header && method !== 'GET') headers[header.content] = token.content;
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    const res = await fetch(url, {
      method: method,
      headers: headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
      credentials: 'same-origin',
    });
    if (res.status === 204) return null;
    const text = await res.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch (e) { data = null; }
    if (!res.ok) {
      const msg = (data && (data.error || data.message)) || text || '通信に失敗しました';
      throw new Error(msg);
    }
    return data;
  }

  let partsCache = null;
  async function loadParts() {
    if (!partsCache) {
      const parts = await api('GET', '/api/training-parts');
      partsCache = (parts || []).map(function (p) { return { code: p.partCode, name: p.partName }; });
    }
    return partsCache;
  }
  function partName(code) {
    if (!code) return '休養日';
    const p = (partsCache || []).find(function (x) { return x.code === code; });
    return p ? p.name : code;
  }

  let itemsCache = null;
  /** 種目マスタ（本日以降に使用可能なもの）。[{itemName, partCode}] */
  async function loadItems() {
    if (!itemsCache) {
      const today = new Date().toISOString().slice(0, 10);
      const grouped = await api('GET', '/api/training-items-grouped?date=' + today);
      itemsCache = [];
      Object.keys(grouped || {}).forEach(function (part) {
        (grouped[part] || []).forEach(function (i) { itemsCache.push({ itemName: i.itemName, partCode: part }); });
      });
    }
    return itemsCache;
  }

  /** 画面内に1つだけのエラー表示を出す。 */
  function showError(container, message) {
    let box = container.querySelector(':scope > .pz-error');
    if (!box) {
      box = document.createElement('div');
      box.className = 'pz-error';
      container.prepend(box);
    }
    box.textContent = message;
  }

  /**
   * 曜日別の種目編集モーダル（03）。
   * @param {{title:string, sub:string, partCode:string|null, items:{itemName:string,targetSets:number}[], onSave:function}} opts
   *   onSave(items) は Promise を返す。失敗時はモーダル内にメッセージを出して閉じない。
   */
  async function openItemEditor(opts) {
    const master = await loadItems();
    const items = (opts.items || []).map(function (i) { return { itemName: i.itemName, targetSets: i.targetSets || 3 }; });
    const backdrop = document.createElement('div');
    backdrop.className = 'pz-modal-backdrop';
    backdrop.innerHTML =
      '<div class="pz-modal" role="dialog" aria-modal="true" aria-label="' + esc(opts.title) + '">' +
      '<div class="pz-h">' + esc(opts.title) + '</div>' +
      '<div class="pz-meta" style="margin-bottom:12px;">' + esc(opts.sub || '') + '</div>' +
      '<div class="pz-err-slot"></div>' +
      '<div class="pz-item-list"></div>' +
      '<div class="pz-add"><input class="form-control" list="pz-item-master" placeholder="種目マスタから追加（名前を入力）" aria-label="追加する種目">' +
      '<button type="button" class="btn btn-outline btn-sm" data-act="add">追加</button></div>' +
      '<datalist id="pz-item-master"></datalist>' +
      '<div class="pz-actions"><button type="button" class="btn btn-outline" data-act="cancel">キャンセル</button>' +
      '<button type="button" class="btn btn-primary" data-act="save">保存する</button></div></div>';
    document.body.appendChild(backdrop);
    const modal = backdrop.querySelector('.pz-modal');
    const list = modal.querySelector('.pz-item-list');
    const input = modal.querySelector('input[list]');
    const errSlot = modal.querySelector('.pz-err-slot');

    // 同じ部位の種目を先に並べる
    const ordered = master.slice().sort(function (a, b) {
      return (a.partCode === opts.partCode ? 0 : 1) - (b.partCode === opts.partCode ? 0 : 1);
    });
    modal.querySelector('datalist').innerHTML = ordered
      .map(function (i) { return '<option value="' + esc(i.itemName) + '">' + esc(partName(i.partCode)) + '</option>'; })
      .join('');

    function render() {
      if (items.length === 0) {
        list.innerHTML = '<div class="pz-empty">種目がありません。下の欄から追加してください。</div>';
        return;
      }
      list.innerHTML = items.map(function (i, idx) {
        return '<div class="pz-item-row" data-idx="' + idx + '">' +
          '<span class="pz-drag" aria-label="ドラッグで並べ替え" title="ドラッグで並べ替え">&#10303;</span>' +
          '<span class="pz-item-name">' + esc(i.itemName) + '</span>' +
          '<input class="form-control pz-sets" type="number" min="1" max="20" value="' + esc(i.targetSets) + '" aria-label="' + esc(i.itemName) + 'のセット数">' +
          '<span class="pz-meta">セット</span>' +
          '<button type="button" class="pz-link danger" data-act="remove" aria-label="' + esc(i.itemName) + 'を削除">&times;</button></div>';
      }).join('');
    }
    render();

    function close() { backdrop.remove(); }

    list.addEventListener('change', function (e) {
      if (!e.target.classList.contains('pz-sets')) return;
      const idx = Number(e.target.closest('.pz-item-row').dataset.idx);
      items[idx].targetSets = Number(e.target.value);
    });
    list.addEventListener('click', function (e) {
      const btn = e.target.closest('[data-act="remove"]');
      if (!btn) return;
      items.splice(Number(btn.closest('.pz-item-row').dataset.idx), 1);
      render();
    });

    // ⠿ハンドルのドラッグで並べ替え（マウス・タッチ共通のPointer Events）
    list.addEventListener('pointerdown', function (e) {
      const handle = e.target.closest('.pz-drag');
      if (!handle) return;
      e.preventDefault();
      const row = handle.closest('.pz-item-row');
      let from = Number(row.dataset.idx);
      row.classList.add('dragging');
      handle.setPointerCapture(e.pointerId);
      function move(ev) {
        const rows = Array.from(list.querySelectorAll('.pz-item-row'));
        const over = rows.find(function (r) {
          const rect = r.getBoundingClientRect();
          return ev.clientY >= rect.top && ev.clientY <= rect.bottom;
        });
        if (!over) return;
        const to = Number(over.dataset.idx);
        if (to === from) return;
        const moved = items.splice(from, 1)[0];
        items.splice(to, 0, moved);
        from = to;
        render();
        list.querySelector('.pz-item-row[data-idx="' + to + '"]').classList.add('dragging');
      }
      function up() {
        handle.removeEventListener('pointermove', move);
        handle.removeEventListener('pointerup', up);
        handle.removeEventListener('pointercancel', up);
        render();
      }
      handle.addEventListener('pointermove', move);
      handle.addEventListener('pointerup', up);
      handle.addEventListener('pointercancel', up);
    });

    function addItem() {
      const name = input.value.trim();
      if (!name) return;
      if (!master.some(function (i) { return i.itemName === name; })) {
        errSlot.innerHTML = '<div class="pz-error">種目マスタにある種目を選んでください: ' + esc(name) + '</div>';
        return;
      }
      if (items.some(function (i) { return i.itemName === name; })) {
        errSlot.innerHTML = '<div class="pz-error">同じ種目がすでにあります: ' + esc(name) + '</div>';
        return;
      }
      errSlot.innerHTML = '';
      items.push({ itemName: name, targetSets: 3 });
      input.value = '';
      render();
    }
    modal.querySelector('[data-act="add"]').addEventListener('click', addItem);
    input.addEventListener('keydown', function (e) { if (e.key === 'Enter') { e.preventDefault(); addItem(); } });
    modal.querySelector('[data-act="cancel"]').addEventListener('click', close);
    backdrop.addEventListener('click', function (e) { if (e.target === backdrop) close(); });
    modal.querySelector('[data-act="save"]').addEventListener('click', async function () {
      const saveBtn = this;
      saveBtn.disabled = true;
      try {
        await opts.onSave(items.map(function (i) { return { itemName: i.itemName, targetSets: Number(i.targetSets) }; }));
        close();
      } catch (err) {
        errSlot.innerHTML = '<div class="pz-error">' + esc(err.message) + '</div>';
        saveBtn.disabled = false;
      }
    });
  }

  /** 週番号順のディロード週フラグから、補足・注意の文言を返す（判定条件はサーバーの /custom/rules と同じ）。 */
  function customWarnings(rules, deloadByWeek) {
    const out = [];
    if (deloadByWeek.length === rules.minTotalWeeks) out.push({ note: true, text: rules.shortCycleNote });
    let streak = 0, maxStreak = 0, any = false;
    deloadByWeek.forEach(function (d) { if (d) { any = true; streak = 0; } else { streak++; maxStreak = Math.max(maxStreak, streak); } });
    if (maxStreak >= rules.longLoadStreakWarnWeeks) out.push({ note: false, text: rules.longLoadStreakWarning });
    if (deloadByWeek.length >= rules.noDeloadWarnMinWeeks && !any) out.push({ note: false, text: rules.noDeloadWarning });
    return out;
  }

  /**
   * 白紙から組むフォーム（06）。
   * @param {HTMLElement} container
   * @param {{rules:object, initial?:object, submitLabel:string, beforeSubmit?:function, onSubmit:function, onCancel?:function, cancelLabel?:string}} opts
   *   onSubmit(payload) は Promise。payload は /custom と同じ形 {name,totalWeeks,weeks,days}
   */
  async function Builder(container, opts) {
    await loadParts();
    const rules = opts.rules;
    const init = opts.initial || null;
    const state = {
      name: init ? init.name : '',
      totalWeeks: init ? init.totalWeeks : 4,
      weeks: {},   // weekNumber -> {pct, deload}
      days: {},    // weekNumber -> dayCode -> {partCode, items}
      currentWeek: 1,
    };
    if (init) {
      (init.weeks || []).forEach(function (w) { state.weeks[w.weekNumber] = { pct: w.targetIntensityPct, deload: !!w.deload }; });
      (init.days || []).forEach(function (d) {
        state.days[d.weekNumber] = state.days[d.weekNumber] || {};
        state.days[d.weekNumber][d.dayOfWeek] = { partCode: d.partCode, items: d.items || [] };
      });
    }
    function week(n) { if (!state.weeks[n]) state.weeks[n] = { pct: '', deload: false }; return state.weeks[n]; }
    function day(w, code) {
      state.days[w] = state.days[w] || {};
      if (!state.days[w][code]) state.days[w][code] = { partCode: null, items: [] };
      return state.days[w][code];
    }

    function render() {
      const parts = partsCache || [];
      let html = '<div class="pz-err-slot"></div>';
      html += '<div class="pz-form-row"><label for="pz-name">プログラム名</label>' +
        '<input id="pz-name" class="form-control" style="flex:1;min-width:180px;" maxlength="100" value="' + esc(state.name) + '"></div>';
      html += '<div class="pz-form-row"><label>週数</label><span class="pz-stepper">' +
        '<button type="button" data-act="weeks-" aria-label="週数を減らす">&minus;</button><span>' + state.totalWeeks + ' 週</span>' +
        '<button type="button" data-act="weeks+" aria-label="週数を増やす">+</button></span>' +
        '<span class="pz-meta">' + rules.minTotalWeeks + '〜' + rules.maxTotalWeeks + '週で設定できます</span></div>';
      html += '<div class="pz-sub-h">週ごとの目標強度 <span class="hint">%1RM・ディロード週は任意で指定</span></div>';
      html += '<div class="pz-table-wrap"><table class="pz-table"><thead><tr><th>週</th><th>目標強度 (%1RM)</th><th>ディロード</th></tr></thead><tbody>';
      for (let n = 1; n <= state.totalWeeks; n++) {
        const w = week(n);
        html += '<tr class="' + (w.deload ? 'deload' : '') + '"><td>第' + n + '週</td>' +
          '<td><input class="form-control pz-pct" type="number" step="0.5" min="0.5" max="100" data-week="' + n + '" value="' + esc(w.pct) + '" aria-label="第' + n + '週の目標強度"></td>' +
          '<td><input type="checkbox" data-deload="' + n + '"' + (w.deload ? ' checked' : '') + ' aria-label="第' + n + '週をディロード週にする"></td></tr>';
      }
      html += '</tbody></table></div>';
      html += '<div class="pz-warnings"></div>';
      html += '<div class="pz-sub-h">曜日ごとの部位と種目 <button type="button" class="btn btn-outline btn-sm" data-act="copy">第1週の内容を全週にコピー</button></div>';
      html += '<div class="pz-week-tabs">';
      for (let n = 1; n <= state.totalWeeks; n++) {
        html += '<button type="button" class="pz-week-tab' + (n === state.currentWeek ? ' active' : '') + (week(n).deload ? ' deload' : '') + '" data-tab="' + n + '">第' + n + '週' + (week(n).deload ? ' ディロード' : '') + '</button>';
      }
      html += '</div>';
      DAY_CODES.forEach(function (code) {
        const d = day(state.currentWeek, code);
        html += '<div class="pz-day"><div class="pz-day-chip' + (d.partCode ? '' : ' rest') + '">' + DAY_LABELS[code] + '</div><div class="pz-day-body"><div class="pz-chips">';
        parts.forEach(function (p) {
          html += '<button type="button" class="pz-chip' + (d.partCode === p.code ? ' on' : '') + '" data-part="' + esc(p.code) + '" data-day="' + code + '">' + esc(p.name) + '</button>';
        });
        html += '<button type="button" class="pz-chip' + (!d.partCode ? ' on' : '') + '" data-part="" data-day="' + code + '">休養</button></div>';
        if (d.partCode) {
          html += '<div class="pz-day-sub">' + (d.items.length ? d.items.map(function (i) { return esc(i.itemName) + ' ' + i.targetSets + 'セット'; }).join(' / ') : '種目が未設定です') + '</div>';
        }
        html += '</div>' + (d.partCode ? '<button type="button" class="pz-link" data-edit-day="' + code + '">種目を編集</button>' : '') + '</div>';
      });
      html += '<div class="pz-actions">' +
        (opts.onCancel ? '<button type="button" class="btn btn-outline" data-act="cancel">' + esc(opts.cancelLabel || 'キャンセル') + '</button>' : '') +
        '<button type="button" class="btn btn-primary" data-act="submit">' + esc(opts.submitLabel) + '</button></div>';
      html += '<div class="pz-confirm-slot"></div>';
      container.innerHTML = html;
      renderWarnings();
    }

    function renderWarnings() {
      const box = container.querySelector('.pz-warnings');
      const deload = [];
      for (let n = 1; n <= state.totalWeeks; n++) deload.push(week(n).deload);
      box.innerHTML = customWarnings(rules, deload).map(function (w) {
        return w.note ? '<div class="pz-note">' + esc(w.text) + '</div>' : '<div class="pz-caution">' + esc(w.text) + '</div>';
      }).join('');
    }

    function payload() {
      const weeks = [];
      const days = [];
      for (let n = 1; n <= state.totalWeeks; n++) {
        const w = week(n);
        weeks.push({ weekNumber: n, targetIntensityPct: w.pct === '' ? null : Number(w.pct), deload: !!w.deload });
        DAY_CODES.forEach(function (code) {
          const d = (state.days[n] || {})[code];
          if (d && d.partCode) days.push({ weekNumber: n, dayOfWeek: code, partCode: d.partCode, items: d.items });
        });
      }
      return { name: state.name.trim(), totalWeeks: state.totalWeeks, weeks: weeks, days: days };
    }

    container.addEventListener('input', function (e) {
      if (e.target.id === 'pz-name') state.name = e.target.value;
      if (e.target.dataset.week) week(Number(e.target.dataset.week)).pct = e.target.value;
    });
    container.addEventListener('change', function (e) {
      if (e.target.dataset.deload) {
        week(Number(e.target.dataset.deload)).deload = e.target.checked;
        render();
      }
    });
    container.addEventListener('click', async function (e) {
      const t = e.target.closest('button');
      if (!t) return;
      const act = t.dataset.act;
      if (act === 'weeks-' && state.totalWeeks > rules.minTotalWeeks) { state.totalWeeks--; state.currentWeek = Math.min(state.currentWeek, state.totalWeeks); render(); }
      else if (act === 'weeks+' && state.totalWeeks < rules.maxTotalWeeks) { state.totalWeeks++; render(); }
      else if (act === 'copy') {
        const src = state.days[1] || {};
        for (let n = 2; n <= state.totalWeeks; n++) {
          state.days[n] = {};
          Object.keys(src).forEach(function (code) {
            state.days[n][code] = { partCode: src[code].partCode, items: src[code].items.map(function (i) { return Object.assign({}, i); }) };
          });
        }
        render();
      } else if (t.dataset.tab) { state.currentWeek = Number(t.dataset.tab); render(); }
      else if (t.dataset.day !== undefined && t.dataset.part !== undefined) {
        const d = day(state.currentWeek, t.dataset.day);
        d.partCode = t.dataset.part || null;
        if (!d.partCode) d.items = [];
        render();
      } else if (t.dataset.editDay) {
        const code = t.dataset.editDay;
        const d = day(state.currentWeek, code);
        await openItemEditor({
          title: DAY_LABELS[code] + '曜日の種目編集',
          sub: '第' + state.currentWeek + '週・' + partName(d.partCode),
          partCode: d.partCode,
          items: d.items,
          onSave: async function (items) { d.items = items; render(); },
        });
      } else if (act === 'cancel') {
        opts.onCancel();
      } else if (act === 'submit') {
        const p = payload();
        const slot = container.querySelector('.pz-err-slot');
        slot.innerHTML = '';
        const doSubmit = async function () {
          t.disabled = true;
          try {
            await opts.onSubmit(p);
          } catch (err) {
            slot.innerHTML = '<div class="pz-error">' + esc(err.message) + '</div>';
            t.disabled = false;
            window.scrollTo({ top: container.getBoundingClientRect().top + window.scrollY - 80, behavior: 'smooth' });
          }
        };
        if (opts.beforeSubmit) {
          opts.beforeSubmit(container.querySelector('.pz-confirm-slot'), doSubmit);
        } else {
          doSubmit();
        }
      }
    });
    render();
  }

  window.PZ = {
    DAY_CODES: DAY_CODES,
    DAY_LABELS: DAY_LABELS,
    esc: esc,
    api: api,
    loadParts: loadParts,
    partName: partName,
    loadItems: loadItems,
    showError: showError,
    openItemEditor: openItemEditor,
    customWarnings: customWarnings,
    Builder: Builder,
  };
})();
