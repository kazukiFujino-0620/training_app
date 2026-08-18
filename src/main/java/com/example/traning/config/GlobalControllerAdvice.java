package com.example.traning.config;

import com.example.traning.dao.UserDao;
import com.example.traning.retention.RetentionPolicyException;
import com.example.traning.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.ui.Model;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
@Slf4j
public class GlobalControllerAdvice {

  private final UserDao userDao;

  public GlobalControllerAdvice(UserDao userDao) {
    this.userDao = userDao;
  }

  /** 全テンプレートにログインユーザー情報を渡す。 パスワード・OAuthID など機密フィールドを除いた安全な投影のみを渡す。 */
  @ModelAttribute
  public void addUserToModel(Model model, Principal principal) {
    if (principal == null) {
      return;
    }
    userDao
        .selectByEmail(principal.getName())
        .ifPresent(
            user -> {
              model.addAttribute("loginUser", new LoginUserView(user));
            });
  }

  /** 共通ヘッダー（設定歯車アイコン等）の表示制御に使うため、現在のリクエストパスを全テンプレートに渡す。 */
  @ModelAttribute("currentUri")
  public String addCurrentUriToModel(HttpServletRequest request) {
    return request.getRequestURI();
  }

  /**
   * ita2-4: 共通ヘッダーの「戻る」ボタンの戻り先URL・ラベルを{@link ScreenId}から解決してテンプレートに渡す。 未登録の画面（{@link
   * ScreenId#fromPath}が空を返す場合）は{@code screenBackUrl}が{@code null}のままとなり、 {@code
   * common.html}側で{@code history.back()}ボタンにフォールバックする。
   */
  @ModelAttribute
  public void addScreenBackTargetToModel(Model model, HttpServletRequest request) {
    ScreenId.fromPath(request.getRequestURI())
        .ifPresent(
            screen -> {
              model.addAttribute("screenBackUrl", screen.backUrl());
              model.addAttribute("screenBackLabel", screen.backLabel());
            });
  }

  // ── 例外ハンドラー ──────────────────────────────────────────────────────

  // @Valid @RequestBody のバリデーション失敗時に JSON でエラーメッセージを返す。
  // ModelAndView を返すと REST クライアントが HTML を受け取って処理できないため分離。
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<String> handleValidationException(MethodArgumentNotValidException ex) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getDefaultMessage())
            .collect(Collectors.joining(", "));
    log.warn("Validation error: {}", message);
    return ResponseEntity.badRequest().body("入力値が不正です: " + message);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<String> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
    log.warn("JSON parse error: {}", ex.getMessage());
    return ResponseEntity.badRequest().body("リクエストの形式が正しくありません");
  }

  @ExceptionHandler(RetentionPolicyException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ModelAndView handleRetentionPolicyException(RetentionPolicyException ex) {
    log.warn("Retention policy violation: {}", ex.getMessage());
    ModelAndView mav = new ModelAndView("error/409");
    mav.addObject("message", ex.getMessage());
    return mav;
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ModelAndView handleIllegalArgumentException(IllegalArgumentException ex) {
    log.warn("Invalid request: {}", ex.getMessage());
    ModelAndView mav = new ModelAndView("error/500");
    mav.addObject("message", "リクエストの内容が正しくありません。");
    return mav;
  }

  @ExceptionHandler(
      org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ModelAndView handleTypeMismatch(
      org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex) {
    log.warn("Type mismatch: {}", ex.getMessage());
    ModelAndView mav = new ModelAndView("error/500");
    mav.addObject("message", "リクエストパラメータの形式が正しくありません。");
    return mav;
  }

  @ExceptionHandler(org.springframework.security.core.userdetails.UsernameNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ModelAndView handleUsernameNotFoundException(
      org.springframework.security.core.userdetails.UsernameNotFoundException ex) {
    log.warn("User not found");
    ModelAndView mav = new ModelAndView("error/404");
    mav.addObject("message", "お探しのリソースが見つかりませんでした。");
    return mav;
  }

  /**
   * ita1-1 フェーズ3: {@code assertAccessible} 等が投げる {@link ResponseStatusException}（IDOR対策の403等）を処理する。
   *
   * <p>{@code ResponseStatusException} は {@link RuntimeException} のサブクラスのため、専用ハンドラーが無いと下の {@code
   * handleRuntimeException} に握りつぶされ、常に500になってしまう（実装中に発見した既存バグ）。 ステータスコードを尊重しつつ、{@code Accept:
   * application/json} の場合はJSON、それ以外はHTMLエラーページで応答する。
   */
  @ExceptionHandler(ResponseStatusException.class)
  public ModelAndView handleResponseStatusException(
      ResponseStatusException ex, HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
    String reason = ex.getReason() != null ? ex.getReason() : "リクエストを処理できませんでした。";
    log.warn(
        "ResponseStatusException: status={}, reason={}, path={}",
        status,
        reason,
        request.getRequestURI());

    String accept = request.getHeader("Accept");
    if (accept != null && accept.contains("application/json")) {
      response.setStatus(status.value());
      response.setContentType("application/json;charset=UTF-8");
      response.getWriter().write("{\"error\":\"" + reason.replace("\"", "'") + "\"}");
      return null;
    }

    // テンプレートが用意されているステータスのみ専用ページを使い、それ以外は500ページで代替する
    // （存在しないテンプレート名を指定するとThymeleafのレンダリング自体が失敗するため）。
    String viewName =
        switch (status) {
          case FORBIDDEN -> "error/403";
          case NOT_FOUND -> "error/404";
          case CONFLICT -> "error/409";
          default -> "error/500";
        };
    ModelAndView mav = new ModelAndView(viewName);
    mav.setStatus(status);
    mav.addObject("message", reason);
    return mav;
  }

  /**
   * ita2-5: {@code @PreAuthorize} が投げる {@link
   * org.springframework.security.access.AccessDeniedException} を処理する。
   *
   * <p>{@code AccessDeniedException} も {@link RuntimeException} のサブクラスのため、専用ハンドラーが無いと下の {@code
   * handleRuntimeException} に握りつぶされ、常に500になってしまう（{@link ResponseStatusException} と同種の既存バグ、
   * NoticeController実装中に発見）。URLパターンレベルの認可（{@code authorizeHttpRequests}）はServletフィルタ側で {@code
   * ExceptionTranslationFilter} が正しく403に変換するため影響を受けないが、メソッドレベルの {@code @PreAuthorize}
   * のみで保護している箇所はDispatcherServlet内で例外解決されるためこのハンドラーが必要。
   */
  @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
  public ModelAndView handleAccessDeniedException(
      org.springframework.security.access.AccessDeniedException ex,
      HttpServletRequest request,
      HttpServletResponse response)
      throws IOException {
    String requestUri = request.getRequestURI();
    String safeRequestUri =
        requestUri == null ? null : requestUri.replace('\r', '_').replace('\n', '_');
    log.warn("Access denied: path={}", safeRequestUri);

    String accept = request.getHeader("Accept");
    if (accept != null && accept.contains("application/json")) {
      response.setStatus(HttpStatus.FORBIDDEN.value());
      response.setContentType("application/json;charset=UTF-8");
      response.getWriter().write("{\"error\":\"この操作を行う権限がありません。\"}");
      return null;
    }

    ModelAndView mav = new ModelAndView("error/403");
    mav.setStatus(HttpStatus.FORBIDDEN);
    mav.addObject("message", "この操作を行う権限がありません。");
    return mav;
  }

  @ExceptionHandler(RuntimeException.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ModelAndView handleRuntimeException(RuntimeException ex) {
    log.error("Unexpected runtime error: {}", ex.getMessage());
    ModelAndView mav = new ModelAndView("error/500");
    mav.addObject("message", "予期しないエラーが発生しました。時間をおいて再度お試しください。");
    return mav;
  }

  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ModelAndView handleException(Exception ex) {
    log.error("Unexpected error", ex);
    ModelAndView mav = new ModelAndView("error/500");
    mav.addObject("message", "予期しないエラーが発生しました。時間をおいて再度お試しください。");
    return mav;
  }

  // ── パスワード・OAuthID を除いた安全な投影クラス ─────────────────────────
  public record LoginUserView(
      Integer userId, String email, String userName, String role, Boolean enabled) {

    public LoginUserView(User user) {
      this(
          user.getUserId(), user.getEmail(), user.getUserName(), user.getRole(), user.getEnabled());
    }
  }
}
