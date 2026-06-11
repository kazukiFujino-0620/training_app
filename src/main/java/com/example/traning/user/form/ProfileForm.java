package com.example.traning.user.form;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

@Data
public class ProfileForm {

  @Size(max = 50, message = "ユーザー名は50文字以内で入力してください")
  private String userName;

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
}
