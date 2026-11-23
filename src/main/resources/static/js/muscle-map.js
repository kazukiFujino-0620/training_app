/**
 * 部位別の人体図と部位別の表の色分け。会員本人のトップ（menu.html）とスタッフ向けの会員詳細
 * （admin/user_training_detail.html、機能見直し-1-#19）で共用する。
 *
 * [data-muscle-map] の中の .fatigue-dot[data-part][data-pct]（表の各行）から部位ごとの値を読み、
 * 同じ中の人体図（.mz[data-part]）と表の色の点に色を付ける。
 */
(function () {
  function getFatigueColor(pct) {
    if (!pct || pct === 0) return null;
    if (pct <= 30) return '#90caf9';
    if (pct <= 65) return '#1e88e5';
    return '#1565c0';
  }

  function applyFatigueColors() {
    document.querySelectorAll('[data-muscle-map]').forEach(function (root) {
      const fatiguePct = {};
      root.querySelectorAll('.fatigue-dot[data-part]').forEach(function (el) {
        fatiguePct[el.getAttribute('data-part')] = Number(el.getAttribute('data-pct')) || 0;
      });

      root.querySelectorAll('.mz').forEach(function (el) {
        const color = getFatigueColor(fatiguePct[el.getAttribute('data-part')] || 0);
        if (color) el.style.fill = color;
      });

      root.querySelectorAll('.fatigue-dot').forEach(function (el) {
        const color = getFatigueColor(fatiguePct[el.getAttribute('data-part')] || 0);
        if (color) el.style.background = color;
      });
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', applyFatigueColors);
  } else {
    applyFatigueColors();
  }
})();
