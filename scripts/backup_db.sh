#!/bin/bash
# =============================================================================
# MySQL → GCS 自動バックアップスクリプト
# 配置先: /opt/trainingapp/scripts/backup_db.sh
# 実行者: trainingapp (systemd と同じユーザー)
# =============================================================================

set -euo pipefail

# ── 設定 ─────────────────────────────────────────────────────────────────────
DB_NAME="training_db"
DB_USER="springuser"
GCS_BUCKET="gs://training-app-backup-$(hostname -s)"   # セットアップ時に書き換える
KEEP_BACKUPS=30                                          # GCS上に保持するバックアップ世代数
LOCAL_TMP="/tmp"
LOG_FILE="/var/log/trainingapp/backup.log"

# ── パスワードの取得（.env から読み込む） ──────────────────────────────────────
ENV_FILE="/opt/trainingapp/.env"
if [[ ! -f "$ENV_FILE" ]]; then
  echo "[ERROR] .env ファイルが見つかりません: $ENV_FILE" >&2
  exit 1
fi
# SPRING_DATASOURCE_PASSWORD or DB_PASSWORD を読み込む
DB_PASS=$(grep -E '^(SPRING_DATASOURCE_PASSWORD|DB_PASSWORD)=' "$ENV_FILE" \
  | head -1 | cut -d'=' -f2- | tr -d '"' | tr -d "'")

if [[ -z "$DB_PASS" ]]; then
  echo "[ERROR] .env から DB パスワードを取得できませんでした" >&2
  exit 1
fi

# ── ログ出力関数 ───────────────────────────────────────────────────────────────
log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" | tee -a "$LOG_FILE"
}

# ── ログディレクトリの確認 ─────────────────────────────────────────────────────
mkdir -p "$(dirname "$LOG_FILE")"

# ── バックアップファイル名 ──────────────────────────────────────────────────────
DATE=$(date '+%Y%m%d_%H%M%S')
BACKUP_FILE="${LOCAL_TMP}/${DB_NAME}_${DATE}.sql.gz"

log "===== バックアップ開始 ====="
log "対象DB: $DB_NAME"
log "保存先: ${GCS_BUCKET}/"

# ── mysqldump → gzip ────────────────────────────────────────────────────────
log "mysqldump 実行中..."
mysqldump \
  -u "$DB_USER" \
  -p"$DB_PASS" \
  --single-transaction \
  --routines \
  --triggers \
  --events \
  --hex-blob \
  "$DB_NAME" | gzip > "$BACKUP_FILE"

BACKUP_SIZE=$(du -sh "$BACKUP_FILE" | cut -f1)
log "ダンプ完了: $BACKUP_FILE (${BACKUP_SIZE})"

# ── GCS へアップロード ───────────────────────────────────────────────────────
log "GCS へアップロード中..."
gsutil -q cp "$BACKUP_FILE" "${GCS_BUCKET}/"
log "アップロード完了: ${GCS_BUCKET}/$(basename "$BACKUP_FILE")"

# ── ローカル一時ファイルを削除 ────────────────────────────────────────────────
rm -f "$BACKUP_FILE"
log "ローカル一時ファイル削除完了"

# ── 古いバックアップを GCS から削除（KEEP_BACKUPS 世代を超えたもの）────────────
log "古いバックアップを整理中（保持: ${KEEP_BACKUPS}世代）..."
OLD_FILES=$(gsutil ls "${GCS_BUCKET}/${DB_NAME}_*.sql.gz" 2>/dev/null \
  | sort | head -n "-${KEEP_BACKUPS}" || true)

if [[ -n "$OLD_FILES" ]]; then
  echo "$OLD_FILES" | xargs gsutil -q rm
  COUNT=$(echo "$OLD_FILES" | wc -l)
  log "削除した古いバックアップ: ${COUNT}件"
else
  log "削除対象の古いバックアップなし"
fi

log "===== バックアップ完了 ====="
exit 0
