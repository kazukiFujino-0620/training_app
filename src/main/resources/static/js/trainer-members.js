// 機能見直し-1-#12 担当会員の一覧: 店舗の切り替えは選んだ時点で表示する（JavaScriptが無い場合は「表示」ボタンで送信）
(function () {
  'use strict';
  document.querySelectorAll('select[data-auto-submit]').forEach(function (select) {
    var form = select.form;
    var button = form && form.querySelector('[data-scope-submit]');
    // .btn は display を指定しているため hidden 属性では消えない
    if (button) button.style.display = 'none';
    select.addEventListener('change', function () {
      form.submit();
    });
  });
})();
