package com.example.traning.smarttrainer.coaching;

import com.example.traning.smarttrainer.recommendation.FatigueCalculator;
import com.example.traning.user.User;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ita5-1 機能3: 筋肉疲労度マップのAI分析。オンデマンド生成＋当日キャッシュ（機能1と同じ案B）。
 *
 * <p>呼び出し元（{@code MenuController}）は、その日のトレーニングが完了したタイミング（1日1回）でのみ
 * {@link #getOrGenerateTodayComment}を呼ぶこと（種目登録のたびに呼ばない、確定済み設計）。
 */
@Service
public class AiFatigueCommentService {

  private final AiFatigueCommentDao dao;
  private final FatigueCoach fatigueCoach;

  public AiFatigueCommentService(AiFatigueCommentDao dao, FatigueCoach fatigueCoach) {
    this.dao = dao;
    this.fatigueCoach = fatigueCoach;
  }

  /** 同意していないユーザーには{@code Optional.empty()}を返す。 */
  @Transactional
  public Optional<String> getOrGenerateTodayComment(
      User user, FatigueCalculator.FatigueResult fatigueResult) {
    if (!Boolean.TRUE.equals(user.getAiAdviceConsent())) {
      return Optional.empty();
    }

    Long userId = user.getUserId().longValue();
    LocalDate today = LocalDate.now();

    Optional<AiFatigueComment> cached = dao.selectByUserIdAndDate(userId, today);
    if (cached.isPresent()) {
      return Optional.of(cached.get().getComment());
    }

    String comment = fatigueCoach.generateComment(fatigueResult);

    AiFatigueComment entity = new AiFatigueComment();
    entity.setUserId(userId);
    entity.setTargetDate(today);
    entity.setComment(comment);
    entity.setSource(fatigueCoach.source());
    dao.insert(entity);

    return Optional.of(comment);
  }
}
