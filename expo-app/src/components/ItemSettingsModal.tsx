import React, { useEffect, useState } from 'react';
import {
  Modal, View, Text, TouchableOpacity, Pressable, StyleSheet, ActivityIndicator, Alert,
} from 'react-native';
import { restPreferenceApi } from '../api/client';

/** RestPreferenceService.DEFAULT_REST_SECONDS と同じ値（未登録種目のデフォルトレスト時間） */
const DEFAULT_REST_SECONDS = 120;
const MIN_REST_SECONDS = 10;
const MAX_REST_SECONDS = 600;
const STEP_SECONDS = 10;

interface SupersetInfo {
  groupId: number;
  role: 'A' | 'B' | null;
  /** 解除ボタン押下時の処理（呼び出し元でtrainingApi.ungroupSupersetを呼ぶ） */
  onUngroup: () => void;
}

interface Props {
  visible: boolean;
  onClose: () => void;
  itemName: string;
  /**
   * 個人上書きが未登録の場合に表示する初期値（種目や重量に応じたシステム算出値のフォールバック）。
   * 未指定時はRestPreferenceService.DEFAULT_REST_SECONDS(120秒)を使う。
   */
  fallbackSeconds?: number;
  /** 保存成功時に呼ばれる（呼び出し元でのキャッシュ更新用、任意） */
  onSaved?: (seconds: number) => void;
  /** システム算出値に戻した時に呼ばれる（任意） */
  onReset?: () => void;
  /**
   * スーパーセット設定セクション。指定した場合のみ表示する
   * （登録前の一括セット入力画面では未確定のため非表示、記録済みトレーニングでは表示）。
   */
  superset?: SupersetInfo | null;
}

/**
 * 種目別の休憩時間・スーパーセット設定モーダル（機能見直し-1-#1）。
 * 種目追加画面・トレーニング記録画面の種目名横「…」ボタンから開く。
 *
 * 休憩秒数はAPI（GET/PUT/DELETE /api/mobile/rest-preferences/{itemName}）を使い、
 * 「システム算出値を初期表示→任意で上書き編集」という形式にする（PR#169のAPIをそのまま流用）。
 */
export default function ItemSettingsModal({
  visible, onClose, itemName, fallbackSeconds, onSaved, onReset, superset,
}: Props) {
  const [loading, setLoading] = useState(true);
  const [seconds, setSeconds] = useState(DEFAULT_REST_SECONDS);
  const [isOverride, setIsOverride] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!visible) return;
    let cancelled = false;
    setLoading(true);
    setDirty(false);
    (async () => {
      try {
        const { data } = await restPreferenceApi.list();
        if (cancelled) return;
        const existing = data.find((p) => p.itemName === itemName);
        if (existing) {
          setSeconds(existing.restSeconds);
          setIsOverride(true);
        } else {
          setSeconds(fallbackSeconds ?? DEFAULT_REST_SECONDS);
          setIsOverride(false);
        }
      } catch {
        if (!cancelled) {
          setSeconds(fallbackSeconds ?? DEFAULT_REST_SECONDS);
          setIsOverride(false);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [visible, itemName]);

  function step(delta: number) {
    setSeconds((prev) => Math.max(MIN_REST_SECONDS, Math.min(MAX_REST_SECONDS, prev + delta)));
    setDirty(true);
  }

  async function handleSave() {
    setSaving(true);
    try {
      await restPreferenceApi.upsert(itemName, seconds);
      setIsOverride(true);
      setDirty(false);
      onSaved?.(seconds);
      onClose();
    } catch {
      Alert.alert('エラー', '休憩時間の保存に失敗しました');
    } finally {
      setSaving(false);
    }
  }

  async function handleReset() {
    setSaving(true);
    try {
      await restPreferenceApi.delete(itemName);
      setSeconds(fallbackSeconds ?? DEFAULT_REST_SECONDS);
      setIsOverride(false);
      setDirty(false);
      onReset?.();
      onClose();
    } catch {
      Alert.alert('エラー', 'システム算出値への変更に失敗しました');
    } finally {
      setSaving(false);
    }
  }

  const sourceLabel = dirty ? '編集中' : isOverride ? '個人設定（保存済み）' : 'システム算出値';

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <Pressable style={styles.backdrop} onPress={onClose}>
        <Pressable style={styles.sheet} onPress={() => {}}>
          <View style={styles.header}>
            <Text style={styles.title} numberOfLines={1} ellipsizeMode="tail">{itemName}</Text>
            <TouchableOpacity style={styles.closeBtn} onPress={onClose} accessibilityLabel="閉じる">
              <Text style={styles.closeBtnText}>✕</Text>
            </TouchableOpacity>
          </View>

          {loading ? (
            <ActivityIndicator style={{ marginVertical: 32 }} color="#4CAF50" />
          ) : (
            <View style={styles.body}>
              <View>
                <Text style={styles.fieldLabel}>休憩時間（秒）</Text>
                <View style={styles.intervalRow}>
                  <TouchableOpacity
                    style={styles.stepperBtn}
                    onPress={() => step(-STEP_SECONDS)}
                    disabled={saving}
                  >
                    <Text style={styles.stepperBtnText}>－</Text>
                  </TouchableOpacity>
                  <View style={styles.intervalValueBox}>
                    <Text style={styles.intervalValueText}>{seconds}</Text>
                  </View>
                  <TouchableOpacity
                    style={styles.stepperBtn}
                    onPress={() => step(STEP_SECONDS)}
                    disabled={saving}
                  >
                    <Text style={styles.stepperBtnText}>＋</Text>
                  </TouchableOpacity>
                </View>
                <View style={styles.sourceTag}>
                  <Text style={styles.sourceTagText}>{sourceLabel}</Text>
                </View>
                <Text style={styles.hintText}>
                  {isOverride
                    ? 'あなたが設定した休憩時間です。'
                    : '種目や重量に応じてシステムが算出したおすすめの休憩時間です。'}
                  {' '}値を変更して保存すると、次回からこの種目はこの秒数が使われます。
                </Text>
              </View>

              {superset && (
                <View style={styles.supersetBox}>
                  <View style={styles.supersetRow}>
                    <Text style={styles.supersetLabel}>スーパーセット</Text>
                    <Text style={styles.supersetBadge}>
                      {superset.role ? `SUPER ${superset.role}` : 'グループ中'}
                    </Text>
                  </View>
                  <TouchableOpacity
                    onPress={() => { superset.onUngroup(); onClose(); }}
                    style={styles.ungroupRow}
                  >
                    <Text style={styles.ungroupText}>グループを解除する</Text>
                  </TouchableOpacity>
                </View>
              )}

              <View style={styles.actions}>
                <TouchableOpacity
                  style={styles.resetBtn}
                  onPress={handleReset}
                  disabled={saving}
                >
                  <Text style={styles.resetBtnText}>算出値に戻す</Text>
                </TouchableOpacity>
                <TouchableOpacity
                  style={[styles.saveBtn, saving && styles.saveBtnDisabled]}
                  onPress={handleSave}
                  disabled={saving}
                >
                  {saving
                    ? <ActivityIndicator color="#fff" size="small" />
                    : <Text style={styles.saveBtnText}>この秒数で保存</Text>}
                </TouchableOpacity>
              </View>
            </View>
          )}
        </Pressable>
      </Pressable>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    flex: 1, backgroundColor: 'rgba(0,0,0,0.4)',
    justifyContent: 'flex-end',
  },
  sheet: {
    backgroundColor: '#fff', borderTopLeftRadius: 20, borderTopRightRadius: 20,
    paddingBottom: 24,
  },
  header: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    padding: 16, borderBottomWidth: 1, borderBottomColor: '#eee',
  },
  title: { flex: 1, fontSize: 16, fontWeight: '800', color: '#222', paddingRight: 8 },
  closeBtn: {
    width: 26, height: 26, borderRadius: 8, backgroundColor: '#f5f5f5',
    borderWidth: 1, borderColor: '#e0e0e0', alignItems: 'center', justifyContent: 'center',
  },
  closeBtnText: { fontSize: 12, color: '#888' },
  body: { padding: 16, gap: 18 },
  fieldLabel: {
    fontSize: 11.5, fontWeight: '700', color: '#888', textTransform: 'uppercase',
    letterSpacing: 0.4, marginBottom: 8,
  },
  intervalRow: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  stepperBtn: {
    width: 40, height: 40, borderRadius: 10, backgroundColor: '#f5f5f5',
    borderWidth: 1, borderColor: '#e0e0e0', alignItems: 'center', justifyContent: 'center',
  },
  stepperBtnText: { fontSize: 18, fontWeight: '700', color: '#4CAF50' },
  intervalValueBox: {
    flex: 1, alignItems: 'center', justifyContent: 'center',
    backgroundColor: '#F1F8F1', borderWidth: 1, borderColor: '#4CAF50',
    borderRadius: 10, paddingVertical: 10,
  },
  intervalValueText: { fontSize: 24, fontWeight: '800', color: '#222' },
  sourceTag: {
    alignSelf: 'flex-start', marginTop: 10, paddingHorizontal: 10, paddingVertical: 3,
    borderRadius: 999, backgroundColor: '#F1F8F1', borderWidth: 1, borderColor: '#4CAF50',
  },
  sourceTagText: { fontSize: 11, fontWeight: '700', color: '#2e7d32' },
  hintText: { fontSize: 12, color: '#999', lineHeight: 18, marginTop: 8 },
  supersetBox: {
    borderWidth: 1, borderColor: '#e0e0e0', borderRadius: 12, padding: 12, backgroundColor: '#fafafa',
  },
  supersetRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  supersetLabel: { fontSize: 13, color: '#333', fontWeight: '600' },
  supersetBadge: { fontSize: 11, fontWeight: '800', color: '#7c3aed' },
  ungroupRow: { marginTop: 8 },
  ungroupText: { fontSize: 12, color: '#7c3aed', fontWeight: '700' },
  actions: { flexDirection: 'row', gap: 10 },
  resetBtn: {
    flexShrink: 0, paddingVertical: 12, paddingHorizontal: 10,
    alignItems: 'center', justifyContent: 'center',
  },
  resetBtnText: { fontSize: 12.5, color: '#c0392b', fontWeight: '700' },
  saveBtn: {
    flex: 1, backgroundColor: '#4CAF50', borderRadius: 10, paddingVertical: 12,
    alignItems: 'center', justifyContent: 'center',
  },
  saveBtnDisabled: { opacity: 0.7 },
  saveBtnText: { color: '#fff', fontSize: 14.5, fontWeight: '700' },
});
