package com.example.traning.user.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.traning.dao.UserDao;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.seasar.doma.jdbc.Result;

/**
 * itバグ-48: {@link UserService#updateUserInfo} が、役割が変わったときだけ対象ユーザーのログイン状態を無効にすることを検証する。
 * 役割の選び直し（一覧のバナー）・ユーザー編集画面のどちらも、この更新処理を通る。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceRoleChangeTest {

  @Mock private UserDao userDao;
  @Mock private TrainingDao trainingDao;
  @Mock private UserSessionInvalidator userSessionInvalidator;

  private UserService service;

  private final User before =
      User.builder().userId(20).email("staff@example.com").role("ROLE_STORE_ADMIN").build();

  @BeforeEach
  void setUp() {
    service = new UserService(userDao, trainingDao, userSessionInvalidator);
    when(userDao.selectById(20)).thenReturn(before);
  }

  @Test
  void 役割が変わった場合はログイン状態を無効にする() {
    User after = before.toBuilder().role("ROLE_TRAINER").build();
    when(userDao.update(after)).thenReturn(new Result<>(1, after));

    service.updateUserInfo(after);

    verify(userSessionInvalidator).invalidateAll(before);
  }

  @Test
  void 役割が変わらない場合はログイン状態を維持する() {
    User after = before.toBuilder().userName("名前だけ変更").build();
    when(userDao.update(after)).thenReturn(new Result<>(1, after));

    service.updateUserInfo(after);

    verify(userSessionInvalidator, never()).invalidateAll(any());
  }

  @Test
  void 更新されなかった場合はログイン状態を維持する() {
    User after = before.toBuilder().role("ROLE_TRAINER").build();
    when(userDao.update(after)).thenReturn(new Result<>(0, after));

    service.updateUserInfo(after);

    verify(userSessionInvalidator, never()).invalidateAll(any());
  }
}
