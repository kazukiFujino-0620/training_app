// 期分けプログラム（機能見直し-1-#3）のモバイル画面で共通に使う定数・関数。

export const DAY_CODES = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'] as const;

export const DAY_LABELS: Record<string, string> = {
  MON: '月', TUE: '火', WED: '水', THU: '木', FRI: '金', SAT: '土', SUN: '日',
};

/** 部位（AddExerciseScreenの部位一覧と同じ） */
export const PARTS: { code: string; label: string }[] = [
  { code: 'CHEST', label: '胸' },
  { code: 'BACK', label: '背中' },
  { code: 'SHOULDER', label: '肩' },
  { code: 'ARM', label: '腕' },
  { code: 'LEG', label: '脚' },
  { code: 'CARDIO', label: 'カーディオ' },
];

export function partLabel(code: string | null | undefined): string {
  if (!code) return '休養日';
  return PARTS.find((p) => p.code === code)?.label ?? code;
}

/** "2026-10-24" → "10/24" */
export function fmtMonthDay(date: string | null | undefined): string {
  if (!date) return '';
  const [, m, d] = date.slice(0, 10).split('-');
  return `${Number(m)}/${Number(d)}`;
}

/** "2026-10-24" の前日を "10/23" で返す（タイムゾーンの影響を受けないよう日付だけで計算） */
export function dayBeforeMonthDay(date: string | null | undefined): string {
  if (!date) return '';
  const [y, m, d] = date.slice(0, 10).split('-').map(Number);
  const x = new Date(y, m - 1, d);
  x.setDate(x.getDate() - 1);
  return `${x.getMonth() + 1}/${x.getDate()}`;
}

/** 開始日 + 週数×7日（＝終了日の翌日）を "yyyy-MM-dd" で返す */
export function nextStartDate(startDate: string, totalWeeks: number): string {
  const [y, m, d] = startDate.slice(0, 10).split('-').map(Number);
  const x = new Date(y, m - 1, d + 7 * totalWeeks);
  const mm = String(x.getMonth() + 1).padStart(2, '0');
  const dd = String(x.getDate()).padStart(2, '0');
  return `${x.getFullYear()}-${mm}-${dd}`;
}

export function tierLabel(tier: string): string {
  switch (tier) {
    case 'BEGINNER_PRESET': return 'プリセット';
    case 'INTERMEDIATE_CUSTOM': return '自分で組んだプログラム';
    case 'TRAINER_MANAGED': return 'トレーナー作成';
    default: return '';
  }
}

/** APIエラーからサーバーのメッセージを取り出す */
export function errorMessage(e: unknown, fallback = '通信に失敗しました'): string {
  const err = e as { response?: { data?: { error?: string } } };
  return err?.response?.data?.error ?? fallback;
}
