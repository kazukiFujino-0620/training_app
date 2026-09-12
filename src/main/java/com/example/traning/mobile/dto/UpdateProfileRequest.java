package com.example.traning.mobile.dto;

import com.example.traning.user.form.ProfileForm;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Data;

/**
 * モバイル: プロフィール基本項目更新API（PATCH /api/mobile/profile）のリクエスト（ita7-1）。
 *
 * <p>{@link ProfileForm}はWeb用フォームクラスのため、Web/モバイルDTO分離方針に沿ってモバイル用に複製する。
 */
@Data
public class UpdateProfileRequest {

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
  private LocalDate birthDate;

  public ProfileForm toProfileForm() {
    ProfileForm form = new ProfileForm();
    form.setUserName(userName);
    form.setHeightCm(heightCm);
    form.setWeightKg(weightKg);
    form.setGender(gender);
    form.setBirthDate(birthDate);
    return form;
  }
}
