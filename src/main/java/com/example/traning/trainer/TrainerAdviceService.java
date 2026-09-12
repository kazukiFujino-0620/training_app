package com.example.traning.trainer;

import com.example.traning.organization.OrganizationScopeResolver;
import com.example.traning.user.Role;
import com.example.traning.user.User;
import com.example.traning.user.service.ProfileService;
import com.example.traning.user.service.UserService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * トレーナーアドバイス機能（ita4-4 (A)）。特定のトレーニング記録には紐付かない時系列・自由記述のメッセージを、 トレーナーが自スコープ内のトレーニー（ROLE_USER）宛に送信する。
 */
@Service
public class TrainerAdviceService {

  private final TrainerAdviceDao trainerAdviceDao;
  private final UserService userService;
  private final OrganizationScopeResolver organizationScopeResolver;
  private final ProfileService profileService;

  public TrainerAdviceService(
      TrainerAdviceDao trainerAdviceDao,
      UserService userService,
      OrganizationScopeResolver organizationScopeResolver,
      ProfileService profileService) {
    this.trainerAdviceDao = trainerAdviceDao;
    this.userService = userService;
    this.organizationScopeResolver = organizationScopeResolver;
    this.profileService = profileService;
  }

  /**
   * トレーナーの担当範囲（自店舗＋兼任店舗）内の一般ユーザー（ROLE_USER）のうち、 担当トレーナーが未割り当て、または自分が担当トレーナーであるユーザー一覧を返す（宛先選択用）。
   *
   * <p>ita4結合試験バグ6対応: 1トレーニーにつき担当トレーナーは1人のみとし、他のトレーナーからは 一覧・送信ともに見えなくする（{@link #send}
   * で最初の送信時に自動的に割り当てられる）。
   */
  public List<User> listTrainees(User trainer) {
    Set<Long> accessible = organizationScopeResolver.resolveAccessibleOrganizationIds(trainer);
    return userService.findAll().stream()
        .filter(u -> Role.USER.value().equals(u.getRole()))
        .filter(u -> accessible == null || accessible.contains(u.getOrganizationId()))
        .filter(
            u ->
                u.getAssignedTrainerId() == null
                    || u.getAssignedTrainerId().longValue() == trainer.getUserId().longValue())
        .collect(Collectors.toList());
  }

  /** トレーナーが送信したアドバイス一覧（削除済み除く、新しい順）。 */
  public List<TrainerAdvice> listSentByTrainer(User trainer) {
    return trainerAdviceDao.selectActiveByTrainerId(trainer.getUserId().longValue());
  }

  /**
   * トレーナーの担当範囲内の一般ユーザー（ROLE_USER）を、担当トレーナーの割り当て状況に関わらず全員返す。 担当トレーナー変更UI（宛先一覧・現在の担当表示）用。{@link
   * #listTrainees} とは異なり、他のトレーナーが 担当のトレーニーも含める。
   */
  public List<User> listTraineesForAssignmentManagement(User trainer) {
    Set<Long> accessible = organizationScopeResolver.resolveAccessibleOrganizationIds(trainer);
    return userService.findAll().stream()
        .filter(u -> Role.USER.value().equals(u.getRole()))
        .filter(u -> accessible == null || accessible.contains(u.getOrganizationId()))
        .collect(Collectors.toList());
  }

  /** トレーナーと同じ担当範囲内で、担当トレーナーの変更先として選択可能なトレーナー（ORG_ADMIN/STORE_ADMIN）一覧を返す。 */
  public List<User> listEligibleTrainers(User trainer) {
    Set<Long> accessible = organizationScopeResolver.resolveAccessibleOrganizationIds(trainer);
    return userService.findAll().stream()
        .filter(
            u ->
                Role.ORG_ADMIN.value().equals(u.getRole())
                    || Role.STORE_ADMIN.value().equals(u.getRole()))
        .filter(u -> accessible == null || accessible.contains(u.getOrganizationId()))
        .collect(Collectors.toList());
  }

  /**
   * トレーニーの担当トレーナーを変更する。操作者の担当範囲内のトレーニー・変更先トレーナーであることを検証する。 {@code newTrainerId} が{@code
   * null}の場合は未割り当てに戻す。
   *
   * @throws IllegalArgumentException トレーニー・変更先トレーナーが操作者の担当範囲外の場合
   */
  @Transactional
  public void reassignTrainer(User actingTrainer, Long traineeId, Long newTrainerId) {
    boolean isValidTrainee =
        listTraineesForAssignmentManagement(actingTrainer).stream()
            .anyMatch(u -> u.getUserId().longValue() == traineeId);
    if (!isValidTrainee) {
      throw new IllegalArgumentException("このトレーニーの担当は変更できません");
    }

    if (newTrainerId != null) {
      boolean isValidTrainer =
          listEligibleTrainers(actingTrainer).stream()
              .anyMatch(u -> u.getUserId().longValue() == newTrainerId);
      if (!isValidTrainer) {
        throw new IllegalArgumentException("変更先のトレーナーが不正です");
      }
    }

    profileService.updateAssignedTrainer(traineeId.intValue(), newTrainerId);
  }

  /** トレーニー向け: 自分宛のアドバイス一覧（削除済み除く、新しい順）。/notices画面での統合表示に使う。 */
  public List<TrainerAdvice> getActiveForUser(Long userId) {
    return trainerAdviceDao.selectActiveByTargetUserId(userId);
  }

  /** トレーニー向け: 自分宛・指定日が対象日のアドバイス一覧（削除済み除く、新しい順）。トレーニング詳細画面での表示に使う。 */
  public List<TrainerAdvice> getActiveForUserAndDate(Long userId, LocalDate date) {
    return trainerAdviceDao.selectActiveByTargetUserIdAndDate(userId, date);
  }

  /**
   * 渡されたアドバイスのうち未読（read_atがnull）のものを既読にする。 呼び出し側は、既読にする前に{@code advice.getReadAt() ==
   * null}で「今回のハイライト対象」を判定してから このメソッドを呼ぶこと（本文自体は既読後も履歴として表示し続けるため、削除はしない）。
   */
  @Transactional
  public void markAsRead(List<TrainerAdvice> advices) {
    LocalDateTime now = LocalDateTime.now();
    for (TrainerAdvice advice : advices) {
      if (advice.getReadAt() == null) {
        advice.setReadAt(now);
        trainerAdviceDao.update(advice);
      }
    }
  }

  /**
   * アドバイスを送信する。宛先が操作者のスコープ内のROLE_USERであることを検証する。
   *
   * @throws IllegalArgumentException 本文が空、宛先が不正、または対象日が未指定の場合
   */
  @Transactional
  public TrainerAdvice send(User trainer, Long targetUserId, String body, LocalDate targetDate) {
    if (body == null || body.isBlank()) {
      throw new IllegalArgumentException("メッセージを入力してください");
    }
    if (body.length() > 1000) {
      throw new IllegalArgumentException("メッセージは1000文字以内で入力してください");
    }
    if (targetUserId == null) {
      throw new IllegalArgumentException("宛先を選択してください");
    }
    if (targetDate == null) {
      throw new IllegalArgumentException("対象日を選択してください");
    }

    User target =
        listTrainees(trainer).stream()
            .filter(u -> u.getUserId().longValue() == targetUserId)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("この宛先にはメッセージを送信できません"));

    if (target.getAssignedTrainerId() == null) {
      profileService.updateAssignedTrainer(target.getUserId(), trainer.getUserId().longValue());
    }

    TrainerAdvice advice = new TrainerAdvice();
    advice.setTrainerId(trainer.getUserId().longValue());
    advice.setTargetUserId(targetUserId);
    advice.setTargetDate(targetDate);
    advice.setBody(body.trim());
    trainerAdviceDao.insert(advice);
    return advice;
  }

  /** 誤って送信したアドバイスを取り下げる（論理削除、トレーニー側から非表示になる）。送信者本人のみ可能。 */
  @Transactional
  public void delete(User trainer, Long adviceId) {
    TrainerAdvice advice =
        trainerAdviceDao
            .selectById(adviceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "アドバイスが見つかりません"));
    if (trainer.getUserId().longValue() != advice.getTrainerId()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "このアドバイスを削除する権限がありません");
    }
    advice.setDeletedAt(LocalDateTime.now());
    trainerAdviceDao.update(advice);
  }
}
