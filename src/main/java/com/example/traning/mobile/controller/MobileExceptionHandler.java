package com.example.traning.mobile.controller;

import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** /api/mobile/** 向け JSON エラーレスポンス専用ハンドラー（GlobalControllerAdvice より優先） */
@RestControllerAdvice(basePackages = "com.example.traning.mobile.controller")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MobileExceptionHandler {

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
		return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
		String msg = ex.getBindingResult().getAllErrors().stream()
				.map(e -> e.getDefaultMessage())
				.findFirst().orElse("入力値が正しくありません");
		return ResponseEntity.badRequest().body(Map.of("error", msg));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, String>> handleGeneral(Exception ex) {
		return ResponseEntity.internalServerError().body(Map.of("error", "サーバーエラーが発生しました"));
	}
}
