/*
 * itバグ-49: 招待コード発行フォーム。トレーナー用を選んだときは、割り当て先から組織（ジム）を外して店舗だけにする。
 *
 * - トレーナー用: 店舗以外の選択肢を隠して選べなくし、選択中が店舗以外なら最初の店舗に切り替える。説明を1行出す。
 * - 会員用: すべての選択肢を戻し、説明を隠す。
 * - サーバー側でも同じ制限をかけている（画面を経由しない操作はエラーになる）。
 */
(function () {
  'use strict';

  var select = document.getElementById('invite-organization');
  var help = document.getElementById('invite-organization-help');
  var radios = document.querySelectorAll('input[name="purpose"]');
  if (!select || !help || radios.length === 0) return;

  function isStore(option) {
    return option.getAttribute('data-org-type') === 'STORE';
  }

  function selectedPurpose() {
    for (var i = 0; i < radios.length; i++) {
      if (radios[i].checked) return radios[i].value;
    }
    return 'MEMBER';
  }

  function render() {
    var trainerOnly = selectedPurpose() === 'TRAINER';
    var options = select.options;
    var firstStore = null;
    for (var i = 0; i < options.length; i++) {
      var option = options[i];
      var allowed = !trainerOnly || isStore(option);
      // Safariはoptionのhiddenを無視するため、disabledも併せて付ける
      option.hidden = !allowed;
      option.disabled = !allowed;
      if (allowed && firstStore === null && isStore(option)) firstStore = option;
    }
    var current = select.options[select.selectedIndex];
    if (trainerOnly && current && !isStore(current) && firstStore) {
      firstStore.selected = true;
    }
    help.hidden = !trainerOnly;
  }

  for (var i = 0; i < radios.length; i++) {
    radios[i].addEventListener('change', render);
  }
  render();
})();
