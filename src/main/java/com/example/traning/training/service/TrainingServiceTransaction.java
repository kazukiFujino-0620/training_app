package com.example.traning.training.service;

import com.example.traning.dao.UserDao;
import com.example.traning.training.Training;
import com.example.traning.training.TrainingDetail;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.training.dao.TrainingDetailDao;
import java.security.Principal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrainingServiceTransaction {
  private final TrainingDao trainingDao;
  private final TrainingDetailDao trainingDetailDao;
  private final UserDao userDao;

  public TrainingServiceTransaction(
      TrainingDao trainingDao, TrainingDetailDao trainingDetailDao, UserDao userDao) {
    this.trainingDao = trainingDao;
    this.trainingDetailDao = trainingDetailDao;
    this.userDao = userDao;
  }

  @Transactional
  public void execute(Training training, Principal principal) {
    // organization_idはDB上NOT NULL。trainingsのINSERT/UPDATEは全列を明示的に列挙する自動生成SQLのため、
    // 呼び出し元（Controller）が設定していなくても、常にここで所有ユーザーの所属組織から解決して補完する
    // （更新時にNULLで上書きしてしまう事故を防ぐための一元的なガード）。
    training.setOrganizationId(userDao.selectOrganizationIdById(training.getUserId()));

    if (training.getId() == null) {
      trainingDao.insert(training);
    } else {
      trainingDetailDao.deleteByTrainingId(training.getId());
      trainingDao.update(training);
    }

    for (int i = 0; i < training.getDetails().size(); i++) {
      TrainingDetail d = training.getDetails().get(i);
      d.setTrainingId(training.getId());
      d.setSetNumber(i + 1);
      if (d.getCount() == null) {
        d.setCount(d.getReps() != null ? d.getReps() : 0);
      }
      trainingDetailDao.insert(d);
    }
  }
}
