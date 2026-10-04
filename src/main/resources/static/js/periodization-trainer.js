/*
 * 機能見直し-1-#3 トレーナー側（モックアップ版11の07）。
 * - /trainer/periodization: 案を送る（返事待ち・予約中の案がある相手には送信前に確認）、返事待ちの案の取り下げ
 * - /trainer/periodization/proposals/{id}/edit: 開始前の案の編集（06・03のUIを流用）
 */
(function () {
  'use strict';
  const esc = PZ.esc;

  function fmt(d) {
    if (!d) return '';
    const s = String(d).slice(0, 10).split('-');
    return Number(s[1]) + '/' + Number(s[2]);
  }

  // ---------- 案を送る ----------
  const page = document.getElementById('pz-trainer');
  if (page) {
    const traineeId = Number(page.dataset.traineeId);
    const traineeName = page.dataset.traineeName;
    const sendBtn = document.getElementById('pz-send');
    const confirmSlot = document.getElementById('pz-send-confirm');
    let selected = null;

    page.addEventListener('click', function (e) {
      const btn = e.target.closest('[data-preset]');
      if (!btn) return;
      page.querySelectorAll('.pz-preset').forEach(function (b) { b.classList.remove('selected'); });
      btn.classList.add('selected');
      selected = { id: Number(btn.dataset.preset), name: btn.dataset.name };
      sendBtn.disabled = false;
      sendBtn.textContent = traineeName + 'さんに案を送る';
      confirmSlot.innerHTML = '';
    });

    async function send() {
      try {
        await PZ.api('POST', '/trainer/periodization/proposals', { traineeUserId: traineeId, presetProgramId: selected.id });
        window.location.reload();
      } catch (err) {
        confirmSlot.innerHTML = '<div class="pz-error">' + esc(err.message) + '</div>';
      }
    }

    sendBtn.addEventListener('click', function () {
      if (!selected) return;
      const o = window.PZ_OUTSTANDING || { pending: [], scheduled: [] };
      if (!o.pending.length && !o.scheduled.length) { send(); return; }
      let html = '<div class="pz-confirm" role="alert"><div class="pz-meta" style="margin-bottom:6px;">送信前の確認</div>';
      o.pending.forEach(function (p) {
        html += '<div class="pz-confirm-line"><span class="pz-st pending">返事待ち</span> 返事待ちの案「' + esc(p.name) + '」（' + fmt(p.createdAt) +
          '送信）は、新しい案「' + esc(selected.name) + '」に置き換わります。' + esc(traineeName) + 'さんには新しい案だけが届きます。</div>';
      });
      o.scheduled.forEach(function (s) {
        html += '<div class="pz-confirm-line"><span class="pz-st scheduled">予約中</span> 予約中の案「' + esc(s.name) + '」' +
          (o.scheduledStartDate ? '（今のプログラム終了後の' + fmt(o.scheduledStartDate) + 'から開始）' : '') +
          'はそのまま残ります。新しい案を' + esc(traineeName) + 'さんが予約できるのは、この予約が始まった後です。</div>';
      });
      html += '<div class="pz-actions"><button type="button" class="btn btn-outline" data-c="no">やめる</button>' +
        '<button type="button" class="btn btn-primary" data-c="yes">' + (o.pending.length ? '置き換えて送る' : '送る') + '</button></div></div>';
      confirmSlot.innerHTML = html;
      confirmSlot.querySelector('[data-c="no"]').onclick = function () { confirmSlot.innerHTML = ''; };
      confirmSlot.querySelector('[data-c="yes"]').onclick = send;
    });

    const withdrawSlot = document.getElementById('pz-withdraw-confirm');
    page.addEventListener('click', function (e) {
      const btn = e.target.closest('[data-withdraw]');
      if (!btn) return;
      const id = btn.dataset.withdraw;
      withdrawSlot.innerHTML = '<div class="pz-confirm"><div class="pz-confirm-line">返事待ちの案「' + esc(btn.dataset.name) +
        '」を取り下げますか？ ' + esc(traineeName) + 'さんの画面から案が消えます。</div>' +
        '<div class="pz-actions"><button type="button" class="btn btn-outline" data-c="no">やめる</button>' +
        '<button type="button" class="btn btn-primary" data-c="yes">取り下げる</button></div></div>';
      withdrawSlot.querySelector('[data-c="no"]').onclick = function () { withdrawSlot.innerHTML = ''; };
      withdrawSlot.querySelector('[data-c="yes"]').onclick = async function () {
        try {
          await PZ.api('POST', '/trainer/periodization/proposals/' + id + '/withdraw');
          window.location.reload();
        } catch (err) { withdrawSlot.innerHTML = '<div class="pz-error">' + esc(err.message) + '</div>'; }
      };
    });
  }

  // ---------- 案の編集 ----------
  const edit = document.getElementById('pz-edit');
  if (edit) {
    const id = edit.dataset.proposalId;
    const status = edit.dataset.status;
    const target = edit.querySelector('.pz-builder');
    if (status === 'PENDING' || status === 'SCHEDULED') {
      Promise.all([
        PZ.api('GET', '/api/periodization/custom/rules'),
        PZ.api('GET', '/trainer/periodization/proposals/' + id + '/content'),
      ]).then(function (r) {
        return PZ.Builder(target, {
          rules: r[0],
          initial: r[1],
          submitLabel: '案を更新する',
          cancelLabel: 'キャンセル',
          onCancel: function () { window.location.href = '/trainer/periodization?targetUserId=' + edit.dataset.traineeId; },
          onSubmit: async function (payload) {
            await PZ.api('POST', '/trainer/periodization/proposals/' + id + '/content', payload);
            window.location.href = '/trainer/periodization?targetUserId=' + edit.dataset.traineeId;
          },
        });
      }).catch(function (err) { PZ.showError(target, err.message); });
    }
  }
})();
