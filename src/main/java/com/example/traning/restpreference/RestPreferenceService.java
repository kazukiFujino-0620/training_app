package com.example.traning.restpreference;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RestPreferenceService {

  /** 未登録種目のデフォルトレスト時間(秒)。17番要件「未登録時は2:00で良い」より。 */
  public static final int DEFAULT_REST_SECONDS = 120;

  private static final int MIN_REST_SECONDS = 10;
  private static final int MAX_REST_SECONDS = 600;

  private final RestPreferenceDao restPreferenceDao;

  public RestPreferenceService(RestPreferenceDao restPreferenceDao) {
    this.restPreferenceDao = restPreferenceDao;
  }

  @Transactional(readOnly = true)
  public List<UserItemRestPreference> listByUserId(Long userId) {
    return restPreferenceDao.selectByUserId(userId);
  }

  @Transactional(readOnly = true)
  public Optional<UserItemRestPreference> find(Long userId, String itemName) {
    return restPreferenceDao.selectByUserIdAndItemName(userId, itemName);
  }

  @Transactional
  public UserItemRestPreference upsert(Long userId, String itemName, int restSeconds) {
    if (restSeconds < MIN_REST_SECONDS || restSeconds > MAX_REST_SECONDS) {
      throw new IllegalArgumentException(
          "レスト時間は" + MIN_REST_SECONDS + "〜" + MAX_REST_SECONDS + "秒の範囲で指定してください");
    }

    Optional<UserItemRestPreference> existing =
        restPreferenceDao.selectByUserIdAndItemName(userId, itemName);

    UserItemRestPreference pref = existing.orElseGet(UserItemRestPreference::new);
    pref.setUserId(userId);
    pref.setItemName(itemName);
    pref.setRestSeconds(restSeconds);
    pref.setUpdatedAt(LocalDateTime.now());

    if (existing.isPresent()) {
      restPreferenceDao.update(pref);
    } else {
      pref.setCreatedAt(LocalDateTime.now());
      restPreferenceDao.insert(pref);
    }
    return pref;
  }

  @Transactional
  public void delete(Long userId, String itemName) {
    restPreferenceDao
        .selectByUserIdAndItemName(userId, itemName)
        .ifPresent(restPreferenceDao::delete);
  }
}
