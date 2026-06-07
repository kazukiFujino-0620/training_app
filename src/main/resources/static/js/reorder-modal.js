(function () {
  'use strict';

  const PART_LABELS = {
    CHEST: '胸', BACK: '背中', ARM: '腕', SHOULDER: '肩', LEG: '脚'
  };

  let currentOrder = []; // { _origIdx, id, menu, partCode } の配列
  let currentSaveCallback = null; // クライアントサイドモード用コールバック

  // ── CSRF ─────────────────────────────────────────────────────────
  function getCsrf() {
    return {
      token: document.querySelector('meta[name="_csrf"]')?.content ?? '',
      header: document.querySelector('meta[name="_csrf_header"]')?.content ?? 'X-CSRF-TOKEN'
    };
  }

  // ── 日付の取得（URLパラメータ or 今日） ──────────────────────────
  function getTargetDate() {
    const p = new URLSearchParams(window.location.search).get('date');
    if (p) return p;
    return new Date().toISOString().slice(0, 10);
  }

  // ── モーダル開閉 ─────────────────────────────────────────────────
  // options が省略 or null → API経由（menu / start/training）
  // options.items + options.onSave → クライアントサイドモード（training/register）
  function openModal(options) {
    const modal = document.getElementById('reorderModal');
    if (!modal) return;
    modal.classList.remove('hidden');

    if (options && options.items) {
      currentSaveCallback = options.onSave || null;
      currentOrder = options.items.map((item, i) => ({
        _origIdx: i,
        id: item.id,
        menu: item.menu,
        partCode: item.partCode
      }));
      if (currentOrder.length === 0) {
        document.getElementById('reorderList').innerHTML =
          '<p style="color:var(--text-muted);">この日のトレーニングはありません</p>';
      } else {
        renderList();
      }
    } else {
      currentSaveCallback = null;
      fetchTrainings();
    }
  }

  function closeModal() {
    const modal = document.getElementById('reorderModal');
    if (modal) modal.classList.add('hidden');
    currentOrder = [];
    currentSaveCallback = null;
  }

  // ── トレーニング一覧取得（APIモード用） ─────────────────────────
  async function fetchTrainings() {
    const date = getTargetDate();
    const listEl = document.getElementById('reorderList');
    listEl.innerHTML = '<p style="color:var(--text-muted);">読み込み中…</p>';

    try {
      const res = await fetch(`/api/training/by-date?date=${date}`);
      if (!res.ok) throw new Error('fetch failed');
      const data = await res.json();

      if (data.length === 0) {
        listEl.innerHTML = '<p style="color:var(--text-muted);">この日のトレーニングはありません</p>';
        currentOrder = [];
        return;
      }

      currentOrder = data.map((d, i) => ({
        _origIdx: i,
        id: d.id,
        menu: d.menu,
        partCode: d.partCode
      }));
      renderList();
    } catch (e) {
      listEl.innerHTML = '<p style="color:var(--danger-color);">読み込みに失敗しました</p>';
    }
  }

  // ── リスト描画 ───────────────────────────────────────────────────
  function renderList() {
    const listEl = document.getElementById('reorderList');
    listEl.innerHTML = '';

    currentOrder.forEach((item, idx) => {
      const row = document.createElement('div');
      row.style.cssText = 'display:flex;align-items:center;gap:0.5rem;padding:0.5rem 0;border-bottom:1px solid var(--border-color);';

      const upBtn = document.createElement('button');
      upBtn.textContent = '▲';
      upBtn.className = 'btn btn-sm btn-secondary';
      upBtn.style.padding = '0.25rem 0.5rem';
      upBtn.disabled = idx === 0;
      upBtn.addEventListener('click', () => moveItem(idx, -1));

      const downBtn = document.createElement('button');
      downBtn.textContent = '▼';
      downBtn.className = 'btn btn-sm btn-secondary';
      downBtn.style.padding = '0.25rem 0.5rem';
      downBtn.disabled = idx === currentOrder.length - 1;
      downBtn.addEventListener('click', () => moveItem(idx, 1));

      const label = document.createElement('span');
      const partLabel = PART_LABELS[item.partCode] ?? item.partCode;
      // 未保存アイテムは「★」マークで区別
      const newBadge = (item.id == null) ? ' <span style="color:var(--warning-color);font-size:0.8em;">★新規</span>' : '';
      label.innerHTML = `${partLabel} | ${item.menu}${newBadge}`;
      label.style.flex = '1';

      row.appendChild(upBtn);
      row.appendChild(downBtn);
      row.appendChild(label);
      listEl.appendChild(row);
    });
  }

  // ── 上下移動 ─────────────────────────────────────────────────────
  function moveItem(idx, direction) {
    const target = idx + direction;
    if (target < 0 || target >= currentOrder.length) return;
    [currentOrder[idx], currentOrder[target]] = [currentOrder[target], currentOrder[idx]];
    renderList();
  }

  // ── 保存 ─────────────────────────────────────────────────────────
  async function saveOrder() {
    if (currentOrder.length === 0) { closeModal(); return; }

    // クライアントサイドモード: コールバックで呼び出し元に委譲
    if (currentSaveCallback) {
      currentSaveCallback([...currentOrder]);
      closeModal();
      return;
    }

    // APIモード: display_order をDBに保存
    const csrf = getCsrf();
    const orderedIds = currentOrder.map(item => item.id);

    try {
      const res = await fetch('/api/training/reorder', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          [csrf.header]: csrf.token
        },
        body: JSON.stringify(orderedIds)
      });

      if (res.ok) {
        closeModal();
        location.reload();
      } else if (res.status === 403) {
        alert('権限がありません。');
      } else {
        alert('保存に失敗しました。');
      }
    } catch (e) {
      alert('通信エラーが発生しました。');
    }
  }

  // ── グローバル公開 ────────────────────────────────────────────────
  window.openReorderModal = openModal;

  // ── イベント登録 ─────────────────────────────────────────────────
  document.addEventListener('DOMContentLoaded', function () {
    document.getElementById('openReorderModal')
      ?.addEventListener('click', function () {
        document.getElementById('settingsDropdown')?.classList.remove('open');
        openModal();
      });

    document.getElementById('reorderSaveBtn')
      ?.addEventListener('click', saveOrder);

    document.getElementById('reorderCancelBtn')
      ?.addEventListener('click', closeModal);

    document.getElementById('reorderModal')
      ?.addEventListener('click', function (e) {
        if (e.target === this) closeModal();
      });
  });
})();
