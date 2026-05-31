package com.example.traning.filter;

import jakarta.servlet.http.HttpServletRequest;

public final class IpUtils {

    private IpUtils() {}

    /**
     * クライアントIPを解決する。
     * GCP Cloud Run はリバースプロキシ経由のため X-Forwarded-For 先頭エントリを使用する。
     */
    public static String resolveClientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String firstIp = forwarded.split(",")[0].trim();
            if (!firstIp.isEmpty()) return firstIp;
        }
        return req.getRemoteAddr();
    }
}
