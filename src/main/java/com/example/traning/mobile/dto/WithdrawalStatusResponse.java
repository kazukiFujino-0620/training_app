package com.example.traning.mobile.dto;

/**
 * モバイル: 退会画面の表示分岐に使う現在状態（ita3-3関連）。
 *
 * @param isGeneralUser 招待コードなし登録・デフォルト組織所属の一般ユーザーか（true=即時削除フロー、false=申請制フロー）
 * @param hasPendingRequest 申請中の退会申請があるか（一般ユーザーの場合は常にfalse）
 */
public record WithdrawalStatusResponse(boolean isGeneralUser, boolean hasPendingRequest) {}
