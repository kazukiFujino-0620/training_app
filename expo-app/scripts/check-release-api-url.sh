#!/bin/bash
# Release ビルドのJSバンドルに埋め込まれた接続先が本番URLであることを確かめる（itバグ-33）。
# 使い方: expo-app/scripts/check-release-api-url.sh <app.app のパス または main.jsbundle のパス>
# 本番URLが含まれ、ローカル・LAN・ngrok の接続先が含まれていなければ 0 で終了する。

set -euo pipefail

PROD_API_URL="https://training-app-test.mydns.jp/api/mobile"

TARGET="${1:?app.app または main.jsbundle のパスを指定してください}"
if [ -d "$TARGET" ]; then
    BUNDLE="$TARGET/main.jsbundle"
else
    BUNDLE="$TARGET"
fi
if [ ! -f "$BUNDLE" ]; then
    echo "NG: JSバンドルが見つかりません: $BUNDLE" >&2
    exit 1
fi

# Hermes のバイトコードでも文字列テーブルは strings で読める
STRINGS=$(strings "$BUNDLE")

if ! grep -qF "$PROD_API_URL" <<< "$STRINGS"; then
    echo "NG: 本番URL（${PROD_API_URL}）がバンドルに含まれていません" >&2
    exit 1
fi

DEV_URLS=$(grep -oE "https?://(localhost|127\.0\.0\.1|10\.[0-9.]+|192\.168\.[0-9.]+|172\.(1[6-9]|2[0-9]|3[01])\.[0-9.]+)(:[0-9]+)?/api/mobile|[a-z0-9-]+\.ngrok(-free)?\.(app|io|dev)" <<< "$STRINGS" | sort -u || true)
if [ -n "$DEV_URLS" ]; then
    echo "NG: 開発用の接続先がバンドルに含まれています:" >&2
    echo "$DEV_URLS" >&2
    exit 1
fi

echo "OK: 接続先は本番URLです（${PROD_API_URL}）"
