#!/bin/bash
# =============================================================================
# バックアップ環境セットアップスクリプト（VM上で一度だけ実行）
# 実行方法: sudo bash /opt/trainingapp/scripts/setup_backup.sh
# =============================================================================

set -euo pipefail

SCRIPT_DIR="/opt/trainingapp/scripts"
BACKUP_SCRIPT="${SCRIPT_DIR}/backup_db.sh"
LOG_DIR="/var/log/trainingapp"
APP_USER="trainingapp"

echo "======================================"
echo " TrainingApp DB バックアップ セットアップ"
echo "======================================"

# ── Step 1: バケット名の決定 ───────────────────────────────────────────────────
PROJECT_ID=$(gcloud config get-value project 2>/dev/null || echo "")
VM_NAME=$(hostname -s)
BUCKET_NAME="training-app-backup-${VM_NAME}"
BUCKET_GS="gs://${BUCKET_NAME}"

echo ""
echo "[Step 1] GCS バケット名: ${BUCKET_NAME}"

# ── Step 2: GCS バケット作成（存在しない場合のみ） ───────────────────────────────
echo "[Step 2] GCS バケット確認・作成..."
if gsutil ls "$BUCKET_GS" > /dev/null 2>&1; then
  echo "  → バケット既存: $BUCKET_GS"
else
  REGION=$(curl -sf "http://metadata.google.internal/computeMetadata/v1/instance/zone" \
    -H "Metadata-Flavor: Google" | awk -F'/' '{print $NF}' | sed 's/-[a-z]$//' || echo "asia-northeast1")
  gsutil mb -l "$REGION" -c STANDARD "$BUCKET_GS"
  echo "  → バケット作成完了: $BUCKET_GS (リージョン: $REGION)"
fi

# ── Step 3: バケットのライフサイクル設定（90日で自動削除） ──────────────────────
echo "[Step 3] バケット ライフサイクル設定（90日自動削除）..."
cat > /tmp/lifecycle.json << 'EOF'
{
  "rule": [
    {
      "action": {"type": "Delete"},
      "condition": {"age": 90}
    }
  ]
}
EOF
gsutil lifecycle set /tmp/lifecycle.json "$BUCKET_GS"
rm -f /tmp/lifecycle.json
echo "  → ライフサイクル設定完了"

# ── Step 4: バックアップスクリプトの配置 ────────────────────────────────────────
echo "[Step 4] バックアップスクリプト配置..."
mkdir -p "$SCRIPT_DIR"

# backup_db.sh の GCS_BUCKET を実際のバケット名に書き換える
sed "s|gs://training-app-backup-\$(hostname -s)|${BUCKET_GS}|g" \
  "${SCRIPT_DIR}/backup_db.sh" > "${BACKUP_SCRIPT}.tmp"
mv "${BACKUP_SCRIPT}.tmp" "$BACKUP_SCRIPT"
chmod 750 "$BACKUP_SCRIPT"
chown "${APP_USER}:${APP_USER}" "$BACKUP_SCRIPT"
echo "  → 配置完了: $BACKUP_SCRIPT"

# ── Step 5: ログディレクトリの準備 ────────────────────────────────────────────
echo "[Step 5] ログディレクトリ準備..."
mkdir -p "$LOG_DIR"
chown "${APP_USER}:${APP_USER}" "$LOG_DIR"
echo "  → $LOG_DIR"

# アプリケーション logback 用ディレクトリ（GCP プロファイルでファイル出力先）
APP_LOG_DIR="/opt/trainingapp/logs"
mkdir -p "$APP_LOG_DIR"
chown "${APP_USER}:${APP_USER}" "$APP_LOG_DIR"
echo "  → $APP_LOG_DIR (アプリログ出力先)"

# ── Step 6: cron 設定 ─────────────────────────────────────────────────────────
echo "[Step 6] cron 設定（毎日 03:00 実行）..."
CRON_LINE="0 3 * * * ${BACKUP_SCRIPT} >> ${LOG_DIR}/backup.log 2>&1"
# trainingapp ユーザーの crontab に追加（重複チェックあり）
if crontab -u "$APP_USER" -l 2>/dev/null | grep -qF "$BACKUP_SCRIPT"; then
  echo "  → cron 既に設定済み"
else
  (crontab -u "$APP_USER" -l 2>/dev/null; echo "$CRON_LINE") | crontab -u "$APP_USER" -
  echo "  → cron 設定完了: 毎日 03:00"
fi

# ── Step 7: 動作テスト ─────────────────────────────────────────────────────────
echo ""
echo "[Step 7] 動作テスト実行中..."
sudo -u "$APP_USER" bash "$BACKUP_SCRIPT"
echo "  → テスト成功"

# ── 完了メッセージ ─────────────────────────────────────────────────────────────
echo ""
echo "======================================"
echo "  セットアップ完了！"
echo "======================================"
echo ""
echo "  バックアップ先: ${BUCKET_GS}/"
echo "  実行スケジュール: 毎日 03:00"
echo "  保持世代: 30世代（最大90日）"
echo "  ログ: ${LOG_DIR}/backup.log"
echo ""
echo "  確認コマンド:"
echo "    gsutil ls ${BUCKET_GS}/"
echo "    tail -f ${LOG_DIR}/backup.log"
echo ""
