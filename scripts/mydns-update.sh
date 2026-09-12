#!/bin/bash
# =============================================================================
# MyDNS.jp 自動IP更新スクリプト
# 配置先: /usr/local/bin/mydns-update.sh
# 実行者: root (cron)
# 認証情報: /etc/mydns-update.env （このリポジトリには含めない。サーバー上にのみ配置）
#
# 背景（itバグ-24）:
#   旧スクリプトはcurlの終了コード・HTTPステータスを記録しておらず、
#   「認証失敗」と「MyDNS.jp側の一時的な異常（輻輳等）」を区別できなかった。
#   間欠的な失敗が2026-08-31から20回連続で続き、MyDNS.jpの「1週間更新なし
#   で無効化」ルールに抵触してドメインが無効化される障害が発生した。
#   詳細: training-app Obsidianボルト 調査結果/MyDNS自動更新cron失敗の調査.md
# =============================================================================

set -uo pipefail

ENV_FILE="/etc/mydns-update.env"
LOG_FILE="/var/log/mydns-update.log"
MAX_ATTEMPTS=3
RETRY_WAIT_SECONDS=30
CURL_TIMEOUT_SECONDS=10

log() { echo "$(date '+%Y-%m-%d %H:%M:%S') $*" >> "$LOG_FILE"; }

if [[ ! -f "$ENV_FILE" ]]; then
  log "result=fail reason=env_file_missing"
  exit 1
fi
# shellcheck source=/dev/null
source "$ENV_FILE"

if [[ -z "${MYDNS_MASTER_ID:-}" || -z "${MYDNS_PASSWORD:-}" ]]; then
  log "result=fail reason=credentials_missing"
  exit 1
fi

notify_slack() {
  local message="$1"
  if [[ -n "${SLACK_WEBHOOK_URL:-}" ]]; then
    curl -s --max-time "$CURL_TIMEOUT_SECONDS" -X POST -H 'Content-Type: application/json' \
      -d "{\"text\": \"[training-app] MyDNS更新失敗: ${message}\"}" \
      "$SLACK_WEBHOOK_URL" > /dev/null 2>&1 || true
  fi
}

attempt=1
while (( attempt <= MAX_ATTEMPTS )); do
  http_code=$(curl -s -o /tmp/mydns-update-response.$$ --max-time "$CURL_TIMEOUT_SECONDS" \
    -w '%{http_code}' \
    "https://${MYDNS_MASTER_ID}:${MYDNS_PASSWORD}@ipv4.mydns.jp/login.html")
  curl_exit=$?
  login_status=$(grep -o 'login_status = [0-9]*' /tmp/mydns-update-response.$$ 2>/dev/null | grep -o '[0-9]*')
  rm -f /tmp/mydns-update-response.$$

  log "attempt=${attempt}/${MAX_ATTEMPTS} curl_exit=${curl_exit} http_code=${http_code} login_status=${login_status:-}"

  if [[ "$curl_exit" == "0" && "$http_code" == "200" && "$login_status" == "1" ]]; then
    log "result=success"
    exit 0
  fi

  if (( attempt < MAX_ATTEMPTS )); then
    sleep "$RETRY_WAIT_SECONDS"
  fi
  (( attempt++ ))
done

log "result=fail reason=all_attempts_exhausted"
notify_slack "curl_exit=${curl_exit} http_code=${http_code} login_status=${login_status:-empty} (${MAX_ATTEMPTS}回試行して失敗)"
exit 1
