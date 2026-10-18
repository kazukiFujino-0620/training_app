/*
 * 機能見直し-1-#5: メニュー画面「体調の参考情報」カードの手動入力（睡眠時間・安静時心拍）。
 *
 * - 片方だけの入力でも保存できる。同じ日の記録は上書きされる。
 * - サーバーは、よく入力される範囲から外れた値を未確認で受け取ると 409 + code=CONFIRMATION_REQUIRED を返す。
 *   その場合はブラウザのダイアログを使わず、カード内に確認文と「このまま保存」「修正する」を出し、
 *   「このまま保存」で confirmed=true を付けて送り直す。
 * - 入力エラー（400）はカード内にメッセージを出す。
 */
(function () {
  'use strict';

  var form = document.getElementById('health-manual-form');
  if (!form) return;

  var dateInput = document.getElementById('hm-date');
  var hoursInput = document.getElementById('hm-sleep-hours');
  var minutesSelect = document.getElementById('hm-sleep-minutes');
  var bpmInput = document.getElementById('hm-resting-bpm');
  var saveButton = document.getElementById('hm-save');
  var confirmBox = document.getElementById('hm-confirm');
  var confirmText = document.getElementById('hm-confirm-text');
  var confirmSave = document.getElementById('hm-confirm-save');
  var confirmCancel = document.getElementById('hm-confirm-cancel');
  var message = document.getElementById('hm-message');

  var csrfToken = document.querySelector('meta[name="_csrf"]').content;
  var csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;

  /** 確認待ちの送信（confirmed=trueで送り直す関数）の一覧 */
  var pendingConfirms = [];
  var anySaved = false;

  function showMessage(text, isError) {
    message.textContent = text;
    message.style.color = isError ? 'var(--danger-color)' : 'var(--text-secondary)';
  }

  function hideConfirm() {
    confirmBox.hidden = true;
    confirmBox.style.display = '';
    pendingConfirms = [];
  }

  function showConfirm(labels) {
    confirmText.textContent =
      labels.join('\n') +
      '\n入力された値は、よく入力される範囲から外れています。入力内容に間違いがなければ、このまま保存できます';
    confirmBox.hidden = false;
    confirmBox.style.display = 'flex';
  }

  function formatSleep(minutes) {
    return Math.floor(minutes / 60) + '時間' + (minutes % 60) + '分';
  }

  function put(url, body) {
    var headers = { 'Content-Type': 'application/json' };
    headers[csrfHeader] = csrfToken;
    return fetch(url, {
      method: 'PUT',
      headers: headers,
      body: JSON.stringify(body),
      credentials: 'same-origin',
    }).then(function (res) {
      return res
        .json()
        .catch(function () { return {}; })
        .then(function (data) { return { status: res.status, data: data }; });
    });
  }

  /** 1件送信し、結果を {kind: 'ok'|'confirm'|'error', ...} で返す */
  function trySave(label, url, body) {
    return put(url, Object.assign({}, body, { confirmed: false })).then(function (r) {
      if (r.status >= 200 && r.status < 300) return { kind: 'ok' };
      if (r.status === 409 && r.data && r.data.code === 'CONFIRMATION_REQUIRED') {
        return {
          kind: 'confirm',
          label: label,
          retry: function () { return put(url, Object.assign({}, body, { confirmed: true })); },
        };
      }
      return { kind: 'error', message: (r.data && r.data.error) || label + 'の保存に失敗しました' };
    }, function () {
      return { kind: 'error', message: label + 'の保存に失敗しました' };
    });
  }

  function finishWithReload() {
    // 表示（最新値・平均）はサーバー側で組み立てているため、保存後は再読み込みして反映する
    window.location.reload();
  }

  form.addEventListener('submit', function (e) {
    e.preventDefault();
    hideConfirm();
    showMessage('', false);

    var date = dateInput.value;
    var hoursText = hoursInput.value.trim();
    var bpmText = bpmInput.value.trim();

    if (hoursText === '' && bpmText === '') {
      showMessage('睡眠時間か安静時心拍のどちらかを入力してください', true);
      return;
    }
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) {
      showMessage('日付を入力してください', true);
      return;
    }
    var tasks = [];
    if (hoursText !== '') {
      if (!/^\d{1,2}$/.test(hoursText)) {
        showMessage('睡眠時間の「時間」は整数で入力してください', true);
        return;
      }
      var minutes = Number(hoursText) * 60 + Number(minutesSelect.value);
      tasks.push(['睡眠時間 ' + formatSleep(minutes), '/api/health/sleep',
        { date: date, durationMinutes: minutes }]);
    }
    if (bpmText !== '') {
      if (!/^\d{1,3}$/.test(bpmText)) {
        showMessage('安静時心拍は整数で入力してください', true);
        return;
      }
      tasks.push(['安静時心拍 ' + bpmText + ' bpm', '/api/health/resting-heart-rate',
        { date: date, restingBpm: Number(bpmText) }]);
    }

    saveButton.disabled = true;
    // 片方ずつ順番に送る（同じ日付の2テーブルで、どちらが失敗したかを分けて示すため）
    tasks.reduce(function (p, t) {
      return p.then(function (acc) {
        return trySave(t[0], t[1], t[2]).then(function (o) { acc.push(o); return acc; });
      });
    }, Promise.resolve([])).then(function (outcomes) {
      var errors = outcomes.filter(function (o) { return o.kind === 'error'; });
      var confirms = outcomes.filter(function (o) { return o.kind === 'confirm'; });
      anySaved = outcomes.some(function (o) { return o.kind === 'ok'; });

      if (errors.length > 0) {
        showMessage(errors.map(function (o) { return o.message; }).join('\n'), true);
      }
      if (confirms.length > 0) {
        pendingConfirms = confirms;
        showConfirm(confirms.map(function (o) { return o.label; }));
        return;
      }
      if (errors.length === 0) {
        finishWithReload();
      }
    }).finally(function () {
      saveButton.disabled = false;
    });
  });

  confirmSave.addEventListener('click', function () {
    var retries = pendingConfirms.map(function (o) { return o.retry(); });
    confirmSave.disabled = true;
    Promise.all(retries).then(function (results) {
      var failed = results.filter(function (r) { return !(r.status >= 200 && r.status < 300); });
      if (failed.length > 0) {
        showMessage(failed.map(function (r) {
          return (r.data && r.data.error) || '保存に失敗しました';
        }).join('\n'), true);
        hideConfirm();
        return;
      }
      finishWithReload();
    }, function () {
      showMessage('保存に失敗しました', true);
    }).finally(function () {
      confirmSave.disabled = false;
    });
  });

  confirmCancel.addEventListener('click', function () {
    hideConfirm();
    if (anySaved) {
      showMessage('確認が必要な項目以外は保存しました。値を修正して、もう一度保存してください', false);
    }
    hoursInput.focus();
  });
})();
