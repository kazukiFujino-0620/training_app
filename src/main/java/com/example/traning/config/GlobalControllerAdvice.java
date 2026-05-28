package com.example.traning.config;

import java.security.Principal;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;

import com.example.traning.dao.UserDao;
import com.example.traning.retention.RetentionPolicyException;
import com.example.traning.user.User;

import lombok.extern.slf4j.Slf4j;

@ControllerAdvice
@Slf4j
public class GlobalControllerAdvice {

    private final UserDao userDao;

    public GlobalControllerAdvice(UserDao userDao) {
        this.userDao = userDao;
    }

    /**
     * 全テンプレートにログインユーザー情報を渡す。
     * パスワード・OAuthID など機密フィールドを除いた安全な投影のみを渡す。
     */
    @ModelAttribute
    public void addUserToModel(Model model, Principal principal) {
        if (principal == null) {
            return;
        }
        userDao.selectByEmail(principal.getName()).ifPresent(user -> {
            model.addAttribute("loginUser", new LoginUserView(user));
        });
    }

    // ── 例外ハンドラー ──────────────────────────────────────────────────────

    // @Valid @RequestBody のバリデーション失敗時に JSON でエラーメッセージを返す。
    // ModelAndView を返すと REST クライアントが HTML を受け取って処理できないため分離。
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation error: {}", message);
        return ResponseEntity.badRequest().body("入力値が不正です: " + message);
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

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ModelAndView handleTypeMismatch(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex) {
        log.warn("Type mismatch: {}", ex.getMessage());
        ModelAndView mav = new ModelAndView("error/500");
        mav.addObject("message", "リクエストパラメータの形式が正しくありません。");
        return mav;
    }

    @ExceptionHandler(org.springframework.security.core.userdetails.UsernameNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ModelAndView handleUsernameNotFoundException(org.springframework.security.core.userdetails.UsernameNotFoundException ex) {
        log.warn("User not found");
        ModelAndView mav = new ModelAndView("error/404");
        mav.addObject("message", "お探しのリソースが見つかりませんでした。");
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
            Integer userId,
            String email,
            String userName,
            String role,
            Boolean enabled) {

        public LoginUserView(User user) {
            this(user.getUserId(), user.getEmail(), user.getUserName(),
                 user.getRole(), user.getEnabled());
        }
    }
}
