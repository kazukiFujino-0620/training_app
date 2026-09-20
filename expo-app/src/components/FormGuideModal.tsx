import React, { useEffect, useState } from 'react';
import {
  Modal, View, Text, TouchableOpacity, Pressable, StyleSheet, ActivityIndicator, Image,
  ScrollView, Linking,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { formGuideApi } from '../api/client';
import { SERVER_ORIGIN } from '../config';
import type { FormGuide } from '../api/types';

interface Props {
  visible: boolean;
  onClose: () => void;
  itemName: string;
}

/** image_urlが相対パス（/images/exercise-guides/...）の場合はサーバーオリジンを付与する。
 * 絶対URL（将来CDN移行時等）はそのまま使う。 */
function resolveImageUrl(imageUrl: string): string {
  return /^https?:\/\//.test(imageUrl) ? imageUrl : `${SERVER_ORIGIN}${imageUrl}`;
}

/**
 * 種目のフォーム解説モーダル（機能見直し-1-#2）。
 * 対象8種目（複合種目Tier1想定）のみ、種目一覧・おすすめメニューからタップして開く。
 * 自己完結コンポーネントで、開いたタイミングで自身がGET /api/mobile/form-guides/{itemName}を呼ぶ。
 *
 * 画像/動画の実素材は本実装時点でまだ準備されていないため、データ未投入の種目は404を受けて
 * 「準備中」表示にフォールバックする（DB設計・API・UI導線は先行して完成させ、素材投入は別タスク）。
 */
export default function FormGuideModal({ visible, onClose, itemName }: Props) {
  const [loading, setLoading] = useState(true);
  const [guide, setGuide] = useState<FormGuide | null>(null);
  const [notFound, setNotFound] = useState(false);

  useEffect(() => {
    if (!visible) return;
    let cancelled = false;
    setLoading(true);
    setNotFound(false);
    setGuide(null);
    (async () => {
      try {
        const { data } = await formGuideApi.get(itemName);
        if (!cancelled) setGuide(data);
      } catch (e: any) {
        if (cancelled) return;
        if (e?.response?.status === 404) {
          setNotFound(true);
        } else {
          setNotFound(true);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [visible, itemName]);

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
          ) : notFound || !guide ? (
            <View style={styles.preparingBox}>
              <Ionicons name="construct-outline" size={28} color="#aaa" />
              <Text style={styles.preparingText}>フォーム解説は準備中です</Text>
              <Text style={styles.preparingSubText}>画像・動画・注意事項は今後追加予定です</Text>
            </View>
          ) : (
            <ScrollView style={styles.body} contentContainerStyle={{ paddingBottom: 8 }}>
              {/* 画像 or 動画リンク（どちらも無い場合は準備中表示） */}
              {guide.imageUrl ? (
                <Image
                  source={{ uri: resolveImageUrl(guide.imageUrl) }}
                  style={styles.image}
                  resizeMode="cover"
                />
              ) : !guide.videoUrl ? (
                <View style={styles.imagePlaceholder}>
                  <Ionicons name="image-outline" size={28} color="#bbb" />
                  <Text style={styles.imagePlaceholderText}>画像は準備中です</Text>
                </View>
              ) : null}

              {guide.videoUrl && (
                <TouchableOpacity
                  style={styles.videoBtn}
                  onPress={() => Linking.openURL(guide.videoUrl!)}
                >
                  <Ionicons name="logo-youtube" size={18} color="#fff" />
                  <Text style={styles.videoBtnText}>解説動画を見る</Text>
                </TouchableOpacity>
              )}

              {guide.jointAngleNote && (
                <Text style={styles.jointAngleNote}>※ {guide.jointAngleNote}</Text>
              )}

              {guide.cautions.length > 0 && (
                <View style={styles.cautionSection}>
                  <Text style={styles.cautionSectionTitle}>よくある誤り</Text>
                  {guide.cautions.map((c, idx) => (
                    <View key={idx} style={styles.cautionCard}>
                      <Text style={styles.cautionTitle}>{c.title}</Text>
                      <Text style={styles.cautionDescription}>{c.description}</Text>
                      <Text style={styles.cautionReason}>{c.reason}</Text>
                    </View>
                  ))}
                </View>
              )}
            </ScrollView>
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
    paddingBottom: 24, maxHeight: '85%',
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
  body: { paddingHorizontal: 16, paddingTop: 16 },
  preparingBox: {
    alignItems: 'center', justifyContent: 'center', paddingVertical: 40, gap: 6,
  },
  preparingText: { fontSize: 14, fontWeight: '700', color: '#999' },
  preparingSubText: { fontSize: 12, color: '#bbb' },
  image: {
    width: '100%', height: 200, borderRadius: 12, backgroundColor: '#f0f0f0',
  },
  imagePlaceholder: {
    width: '100%', height: 160, borderRadius: 12, backgroundColor: '#f5f5f5',
    alignItems: 'center', justifyContent: 'center', gap: 6,
    borderWidth: 1, borderColor: '#e8e8e8', borderStyle: 'dashed',
  },
  imagePlaceholderText: { fontSize: 12, color: '#aaa' },
  videoBtn: {
    marginTop: 12, flexDirection: 'row', alignItems: 'center', justifyContent: 'center',
    gap: 8, backgroundColor: '#c0392b', borderRadius: 10, paddingVertical: 12,
  },
  videoBtnText: { color: '#fff', fontSize: 14, fontWeight: '700' },
  jointAngleNote: { marginTop: 10, fontSize: 11.5, color: '#999', lineHeight: 16 },
  cautionSection: { marginTop: 20 },
  cautionSectionTitle: {
    fontSize: 12, fontWeight: '800', color: '#888', textTransform: 'uppercase',
    letterSpacing: 0.4, marginBottom: 10,
  },
  cautionCard: {
    borderWidth: 1, borderColor: '#f0d0d0', backgroundColor: '#fff8f8',
    borderRadius: 12, padding: 12, marginBottom: 10,
  },
  cautionTitle: { fontSize: 14, fontWeight: '800', color: '#c0392b', marginBottom: 4 },
  cautionDescription: { fontSize: 13, color: '#333', lineHeight: 19, marginBottom: 4 },
  cautionReason: { fontSize: 12, color: '#777', lineHeight: 17 },
});
