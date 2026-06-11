package com.example.traning.user.form;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

@Data
@Getter
@Setter
public class SignupForm {

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

  private String googleId;

  private String lineId;

  // 任意の身体情報
  @DecimalMin(value = "100.0", message = "身長は100cm以上で入力してください")
  @DecimalMax(value = "250.0", message = "身長は250cm以下で入力してください")
  private Double heightCm;

  @DecimalMin(value = "20.0", message = "体重は20kg以上で入力してください")
  @DecimalMax(value = "300.0", message = "体重は300kg以下で入力してください")
  private Double weightKg;

  private String gender;

  @Past(message = "生年月日は過去の日付を入力してください")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate birthDate;

  public boolean isOAuth2Signup() {
    return (googleId != null && !googleId.isEmpty()) || (lineId != null && !lineId.isEmpty());
  }
}
