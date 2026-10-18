/*
 * 機能見直し-1-#7: メニュー画面「週の目標」カードの設定ダイアログ。
 *
 * - 1〜7回を「−」「＋」で選んで保存する（PUT /api/frequency-goal）。保存後は画面を再読み込みしてカードを更新する。
 * - 期分けプログラムの実施中は表示のみ（ステッパーは無効、閉じるボタンだけ）。
 * - ブラウザのダイアログは使わず、エラーはダイアログ内に表示する。
 */
(function () {
  'use strict';

  var MIN = 1;
  var MAX = 7;

  var modal = document.getElementById('frequencyGoalModal');
  var openButton = document.getElementById('fg-open');
  if (!modal || !openButton) return;

  var valueEl = document.getElementById('fg-value');
  var minus = document.getElementById('fg-minus');
  var plus = document.getElementById('fg-plus');
  var save = document.getElementById('fg-save');
  var message = document.getElementById('fg-message');

  var initial = parseInt(modal.getAttribute('data-initial'), 10) || 3;
  var value = initial;

  function render() {
    if (!valueEl) return;
    valueEl.textContent = String(value);
    minus.disabled = value <= MIN;
    plus.disabled = value >= MAX;
  }

  function open() {
    value = initial;
    if (message) message.textContent = '';
    render();
    modal.classList.remove('hidden');
  }

  function close() {
    modal.classList.add('hidden');
  }

  openButton.addEventListener('click', open);
  modal.querySelectorAll('[data-fg-close]').forEach(function (b) {
    b.addEventListener('click', close);
  });
  modal.addEventListener('click', function (e) {
    if (e.target === modal) close();
  });
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' && !modal.classList.contains('hidden')) close();
  });

  if (!save) return; // プログラム実施中（表示のみ）

  minus.addEventListener('click', function () {
    if (value > MIN) value--;
    render();
  });
  plus.addEventListener('click', function () {
    if (value < MAX) value++;
    render();
  });

  var csrfToken = document.querySelector('meta[name="_csrf"]').content;
  var csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;

  save.addEventListener('click', function () {
    save.disabled = true;
    var headers = { 'Content-Type': 'application/json' };
    headers[csrfHeader] = csrfToken;
    fetch('/api/frequency-goal', {
      method: 'PUT',
      headers: headers,
      body: JSON.stringify({ weeklyTargetCount: value })
    })
      .then(function (res) {
        if (res.ok) {
          window.location.reload();
          return null;
        }
        return res.json().then(function (body) {
          throw new Error((body && body.error) || '保存できませんでした');
        });
      })
      .catch(function (err) {
        message.textContent = err.message || '保存できませんでした';
        save.disabled = false;
      });
  });
})();
