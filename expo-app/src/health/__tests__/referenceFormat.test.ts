import {
  formatSleepMinutes,
  restingBpmDiffText,
  restingHrCardLabel,
  sleepCardLabel,
  sleepDiffText,
} from '../referenceFormat';

describe('referenceFormat（機能見直し-1-#5）', () => {
  it('睡眠時間を時間・分で表示し、0分は0時間0分になる', () => {
    expect(formatSleepMinutes(390)).toBe('6時間30分');
    expect(formatSleepMinutes(0)).toBe('0時間0分');
    expect(formatSleepMinutes(480)).toBe('8時間0分');
  });

  it('睡眠の平均との差を長い・短い・同じで表す', () => {
    expect(sleepDiffText(390, 432)).toBe('平均より42分短い');
    expect(sleepDiffText(500, 432)).toBe('平均より1時間8分長い');
    expect(sleepDiffText(552, 432)).toBe('平均より2時間長い');
    expect(sleepDiffText(432, 432)).toBe('平均と同じ');
  });

  it('安静時心拍の平均との差を高い・低い・同じで表す', () => {
    expect(restingBpmDiffText(58, 55)).toBe('平均より3 bpm高い');
    expect(restingBpmDiffText(52, 55)).toBe('平均より3 bpm低い');
    expect(restingBpmDiffText(55, 55)).toBe('平均と同じ');
  });

  it('簡易カードのラベルは今日の記録なら日付なし、古い記録なら日付付き', () => {
    expect(sleepCardLabel('2026-10-15', '2026-10-15')).toBe('昨夜の睡眠');
    expect(sleepCardLabel('2026-10-09', '2026-10-15')).toBe('10/9の睡眠');
    expect(restingHrCardLabel('2026-10-15', '2026-10-15')).toBe('安静時心拍');
    expect(restingHrCardLabel('2026-09-30', '2026-10-15')).toBe('9/30の安静時心拍');
  });
});
