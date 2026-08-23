package com.example.traning.user.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** トレーナー用新規登録フォーム（ita4-3）。一般ユーザー向け{@link SignupForm}とは別ルートで、招待コードを必須とする。 */
@Data
public class TrainerSignupForm {

  @NotBlank(message = "ユーザー名は必須です")
  @Size(min = 1, max = 50, message = "ユーザー名は1〜50文字で入力してください")
  private String username;

  @NotBlank(message = "メールアドレスは必須です")
  @Email(message = "メールアドレスの形式が正しくありません")
  @Size(max = 255, message = "メールアドレスは255文字以内で入力してください")
  private String email;

  @NotBlank(message = "パスワードは必須です")
  @Size(min = 8, max = 100, message = "パスワードは8〜100文字で入力してください")
  @Pattern(
      regexp =
          "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]).+$",
      message = "パスワードは大文字・小文字・数字・記号をそれぞれ1文字以上含む必要があります")
  private String password;

  @NotBlank(message = "パスワード確認は必須です")
  private String password_confirm;

  @NotBlank(message = "招待コードは必須です")
  private String inviteCode;
}
