import {
  excludedSinceLabel, modeLabel, toSuggestion, todayString, totalSets, EditableRow,
} from '../autoWorkoutShared';

// 機能見直し-1-#4: メニュー自動作成の共通計算

function row(over: Partial<EditableRow>): EditableRow {
  return {
    itemName: 'ベンチプレス', partCode: 'CHEST', partName: '胸', compound: true, bodyweight: false,
    weight: '70', reps: '10', sets: '4', ...over,
  };
}

describe('autoWorkoutShared', () => {
  it('todayString はローカル日付を YYYY-MM-DD で返す', () => {
    expect(todayString(new Date(2026, 0, 5, 23, 30))).toBe('2026-01-05');
  });

  it('excludedSinceLabel は「M月D日から除外中」', () => {
    expect(excludedSinceLabel('2026-10-26')).toBe('10月26日から除外中');
    expect(excludedSinceLabel('2026-03-01')).toBe('3月1日から除外中');
  });

  it('modeLabel: プログラム実施中（休養日のおまかせ含む）は週と%、それ以外は「おまかせ」', () => {
    const program = {
      cycleName: '筋肥大4週', weekNumber: 2, totalWeeks: 4, targetIntensityPct: 70, partCode: 'LEG', partName: '脚',
    };
    expect(modeLabel({ program, intensityPct: 70 })).toBe('プログラム 第2週・70%');
    expect(modeLabel({ program: { ...program, partCode: null, partName: null }, intensityPct: 75 })).toBe('プログラム 第2週・75%');
    expect(modeLabel({ program: null, intensityPct: 70 })).toBe('おまかせ');
  });

  it('toSuggestion: 編集後の重量・回数・セットを1つの値で渡す。空欄の重量はnull、自重は0kg', () => {
    const { items, errors } = toSuggestion([
      row({ weight: '62.5' }),
      row({ itemName: 'チェストプレス', weight: '' }),
      row({ itemName: 'ディップス', bodyweight: true, weight: '' }),
    ]);
    expect(errors).toEqual([]);
    expect(items).toEqual([
      { itemName: 'ベンチプレス', weightMin: 62.5, weightMax: 62.5, repsMin: 10, repsMax: 10, sets: 4 },
      { itemName: 'チェストプレス', weightMin: null, weightMax: null, repsMin: 10, repsMax: 10, sets: 4 },
      { itemName: 'ディップス', weightMin: 0, weightMax: 0, repsMin: 10, repsMax: 10, sets: 4 },
    ]);
  });

  it('toSuggestion: 回数・セットが整数でない、重量が数値でない種目はエラーにする', () => {
    const { items, errors } = toSuggestion([
      row({ itemName: 'A', sets: '0' }),
      row({ itemName: 'B', reps: '2.5' }),
      row({ itemName: 'C', weight: 'abc' }),
      row({ itemName: 'D' }),
    ]);
    expect(errors).toEqual(['A', 'B', 'C']);
    expect(items.map((i) => i.itemName)).toEqual(['D']);
  });

  it('totalSets は入力中のセット数の合計', () => {
    expect(totalSets([row({ sets: '4' }), row({ sets: '3' }), row({ sets: '' })])).toBe(7);
  });
});
