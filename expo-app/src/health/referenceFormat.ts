/**
 * 睡眠・安静時心拍の参考表示用の文言（機能見直し-1-#5）。
 * 値の良し悪しは判定せず、本人の過去4週間平均との差を事実として示すだけにする。
 */

/** 分を「6時間30分」の形にする。0分（徹夜）は「0時間0分」。 */
export function formatSleepMinutes(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return `${h}時間${m}分`;
}

/** 平均との差の分数を「42分」「1時間5分」の形にする。 */
function formatDiffMinutes(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  if (h === 0) return `${m}分`;
  return m === 0 ? `${h}時間` : `${h}時間${m}分`;
}

/** 平均が無いときの文言（直近4週間に記録が1日も無い場合）。 */
export const NO_AVERAGE_TEXT = '直近4週間の記録がまだないため、平均は表示できません';

export const COMPARISON_NOTE = 'あなた自身の過去4週間との比較です。良し悪しの判定はしていません。';

export function sleepDiffText(latest: number, average: number): string {
  const diff = latest - average;
  if (diff === 0) return '平均と同じ';
  return diff < 0
    ? `平均より${formatDiffMinutes(-diff)}短い`
    : `平均より${formatDiffMinutes(diff)}長い`;
}

export function restingBpmDiffText(latest: number, average: number): string {
  const diff = latest - average;
  if (diff === 0) return '平均と同じ';
  return diff < 0 ? `平均より${-diff} bpm低い` : `平均より${diff} bpm高い`;
}

/** "2026-10-09" → "10/9" */
function shortDate(isoDate: string): string {
  const [, m, d] = isoDate.split('-');
  return `${Number(m)}/${Number(d)}`;
}

/**
 * トレーニングタブの簡易カードのラベル。最新の記録が今日の日付なら日付を付けず、
 * 古い記録しかない場合は日付を添える（何日も前の値を「昨夜」と表示しないため）。
 */
export function sleepCardLabel(recordDate: string, today: string): string {
  return recordDate === today ? '昨夜の睡眠' : `${shortDate(recordDate)}の睡眠`;
}

export function restingHrCardLabel(recordDate: string, today: string): string {
  return recordDate === today ? '安静時心拍' : `${shortDate(recordDate)}の安静時心拍`;
}

/** 手動入力が可能な「分」の選択肢（15分刻み）。 */
export const SLEEP_MINUTE_OPTIONS = [0, 15, 30, 45] as const;

/** よく入力される範囲から外れた値の確認文（サーバーのHealthInputLimits.CONFIRM_MESSAGEと同じ文言。値の評価はしない）。 */
export const CONFIRM_MESSAGE =
  '入力された値は、よく入力される範囲から外れています。入力内容に間違いがなければ、このまま保存できます';

/** ローカル日付のYYYY-MM-DD（UTC変換によるズレを避けるためtoISOStringは使わない）。 */
export function todayDateString(): string {
  const d = new Date();
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}
