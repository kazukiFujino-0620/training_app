package com.example.traning.user.service;

import com.example.traning.dao.UserDao;
import com.example.traning.training.Training;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.training.dao.TrainingDetailDao;
import com.example.traning.user.User;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminRestoreService {

  private final UserDao userDao;
  private final TrainingDao trainingDao;
  private final TrainingDetailDao trainingDetailDao;

  public AdminRestoreService(
      UserDao userDao, TrainingDao trainingDao, TrainingDetailDao trainingDetailDao) {
    this.userDao = userDao;
    this.trainingDao = trainingDao;
    this.trainingDetailDao = trainingDetailDao;
  }

  @Transactional(readOnly = true)
  public List<User> findDeletedUsers() {
    return userDao.selectDeleted();
  }

  @Transactional(readOnly = true)
  public List<Training> findDeletedTrainings() {
    return trainingDao.selectDeleted();
  }

  @Transactional
  public void restoreUser(Integer userId) {
    userDao.restoreById(userId);
  }

  @Transactional
  public void restoreTraining(Long trainingId) {
    trainingDetailDao.restoreByTrainingId(trainingId);
    trainingDao.restoreById(trainingId);
  }

  /**
   * IDOR対策・組織スコープ判定用（ita1-1 フェーズ3）。ソフトデリート済みのユーザーも対象に含む。
   *
   * @return 対象ユーザーが存在しない場合は {@code null}
   */
  @Transactional(readOnly = true)
  public Long getUserOrganizationId(Integer userId) {
    return userDao.selectOrganizationIdById(userId.longValue());
  }

  /**
   * IDOR対策・組織スコープ判定用（ita1-1 フェーズ3）。ソフトデリート済みのトレーニングも対象に含む。
   *
   * @return 対象トレーニングが存在しない場合は {@code null}
   */
  @Transactional(readOnly = true)
  public Long getTrainingOrganizationId(Long trainingId) {
    return trainingDao.selectOrganizationIdById(trainingId);
  }
}
