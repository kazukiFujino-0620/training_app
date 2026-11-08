import type { AutogenResult, SuggestedItem } from '../api/types';

/** メニュー自動作成（機能見直し-1-#4）の画面で共通に使う計算・文言。 */

/** ローカル日付のYYYY-MM-DD（UTC変換によるズレを避けるためtoISOStringは使わない）。 */
export function todayString(now: Date = new Date()): string {
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, '0');
  const d = String(now.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/** '2026-10-26' → '10月26日から除外中' */
export function excludedSinceLabel(dateStr: string): string {
  const [, m, d] = dateStr.split('-').map((v) => parseInt(v, 10));
  return `${m}月${d}日から除外中`;
}

/** 結果上部のラベル。プログラムの強度で組んだ場合は何週目・何%かを出す。 */
export function modeLabel(result: Pick<AutogenResult, 'mode' | 'program' | 'intensityPct'>): string {
  if (result.mode !== 'ADHOC' && result.program) {
    return `プログラム 第${result.program.weekNumber}週・${result.intensityPct}%`;
  }
  return 'おまかせ';
}

/** 結果画面で編集中の1種目。入力欄の値は文字列で持つ。 */
export interface EditableRow {
  itemName: string;
  partCode: string;
  partName: string;
  compound: boolean;
  bodyweight: boolean;
  weight: string;
  reps: string;
  sets: string;
}

/**
 * 編集後の内容を種目追加画面（aiSuggestion）の形に変換する。入力が不正なら、その種目名をerrorsに入れる。
 * 重量の空欄は null（種目追加画面でも空欄のまま）、自重種目は 0kg。
 */
export function toSuggestion(rows: EditableRow[]): { items: SuggestedItem[]; errors: string[] } {
  const items: SuggestedItem[] = [];
  const errors: string[] = [];
  for (const r of rows) {
    const sets = Number(r.sets);
    const reps = Number(r.reps);
    const weightText = r.weight.trim();
    const weight = r.bodyweight ? 0 : weightText === '' ? null : Number(weightText);
    const validSets = Number.isInteger(sets) && sets >= 1 && sets <= 20;
    const validReps = Number.isInteger(reps) && reps >= 1 && reps <= 100;
    const validWeight = weight === null || (Number.isFinite(weight) && weight >= 0 && weight <= 1000);
    if (!validSets || !validReps || !validWeight) {
      errors.push(r.itemName);
      continue;
    }
    items.push({
      itemName: r.itemName,
      weightMin: weight,
      weightMax: weight,
      repsMin: reps,
      repsMax: reps,
      sets,
    });
  }
  return { items, errors };
}

export function totalSets(rows: EditableRow[]): number {
  return rows.reduce((sum, r) => sum + (parseInt(r.sets, 10) || 0), 0);
}
