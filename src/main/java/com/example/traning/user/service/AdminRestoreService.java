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
}
