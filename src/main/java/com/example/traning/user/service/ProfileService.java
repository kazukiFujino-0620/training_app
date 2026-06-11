package com.example.traning.user.service;

import com.example.traning.dao.UserDao;
import com.example.traning.user.User;
import com.example.traning.user.form.ProfileForm;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

  private final UserDao userDao;

  public ProfileService(UserDao userDao) {
    this.userDao = userDao;
  }

  @Transactional
  public void updateProfile(String email, ProfileForm form) {
    User existing =
        userDao
            .selectByEmail(email)
            .orElseThrow(() -> new RuntimeException("ユーザーが見つかりません email:" + email));
    String newName =
        (form.getUserName() != null && !form.getUserName().isBlank())
            ? form.getUserName()
            : existing.getUserName();
    User updated =
        existing.toBuilder()
            .userName(newName)
            .heightCm(form.getHeightCm())
            .weightKg(form.getWeightKg())
            .gender(form.getGender())
            .birthDate(form.getBirthDate())
            .updatedDatetime(LocalDateTime.now())
            .build();
    userDao.updateProfile(updated);
  }
}
