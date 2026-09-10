module.exports = function (api) {
  const isTest = api.env('test');
  api.cache(true);
  return {
    presets: ['babel-preset-expo'],
    // itバグ-25: AppNavigator.tsxでReact.lazy(() => import(...))による画面の遅延ロードを
    // 導入した。Metro（実機/シミュレータ実行時）はimport()をそのまま処理できるが、
    // Jest（npm test）はNode上でCommonJSとして実行するため、素の動的importを
    // `--experimental-vm-modules`無しでは実行できない。テスト実行時のみ
    // babel-plugin-dynamic-import-nodeでrequireベースに変換する（本番バンドルには影響しない）。
    plugins: isTest ? ['babel-plugin-dynamic-import-node'] : [],
  };
};
