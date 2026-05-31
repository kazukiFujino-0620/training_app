#!/bin/bash
# =============================================================================
# journald サイズ上限設定スクリプト（VM上で一度だけ実行）
# 実行方法: sudo bash /opt/trainingapp/scripts/setup_journald.sh
# =============================================================================

set -euo pipefail

JOURNALD_CONF="/etc/systemd/journald.conf.d/trainingapp.conf"

echo "======================================"
echo " journald サイズ制限 セットアップ"
echo "======================================"

# ── 現在の状況確認 ─────────────────────────────────────────────────────────────
echo ""
echo "[確認] 現在の journald ディスク使用量:"
journalctl --disk-usage 2>/dev/null || echo "  (取得失敗)"

# ── Step 1: 設定ファイル作成 ───────────────────────────────────────────────────
echo ""
echo "[Step 1] journald 設定ファイル作成..."
mkdir -p "$(dirname "$JOURNALD_CONF")"

cat > "$JOURNALD_CONF" << 'EOF'
[Journal]
# ディスク使用量の上限（VM 30GBに対して安全圏）
SystemMaxUse=300M
# 最低限この空き容量を確保する
SystemKeepFree=1G
# 7日以上前のログを自動削除
MaxRetentionSec=7day
# 圧縮を有効にして容量節約
Compress=yes
EOF

echo "  → 作成完了: $JOURNALD_CONF"
echo ""
cat "$JOURNALD_CONF"

# ── Step 2: journald 再起動して設定を反映 ──────────────────────────────────────
echo ""
echo "[Step 2] journald 再起動..."
systemctl restart systemd-journald
echo "  → 再起動完了"

# ── Step 3: 古いログを即時削除（制限値に合わせてトリム） ───────────────────────
echo ""
echo "[Step 3] 古いログをトリム..."
journalctl --vacuum-size=300M --vacuum-time=7d
echo "  → トリム完了"

# ── 設定後の状況確認 ────────────────────────────────────────────────────────────
echo ""
echo "[確認] 設定後の journald ディスク使用量:"
journalctl --disk-usage 2>/dev/null || echo "  (取得失敗)"

# ── 完了メッセージ ──────────────────────────────────────────────────────────────
echo ""
echo "======================================"
echo "  セットアップ完了！"
echo "======================================"
echo ""
echo "  上限: 300MB"
echo "  最低空き: 1GB"
echo "  保持期間: 7日"
echo ""
echo "  確認コマンド:"
echo "    journalctl --disk-usage"
echo "    journalctl -u trainingapp --since '1 hour ago'"
echo ""
