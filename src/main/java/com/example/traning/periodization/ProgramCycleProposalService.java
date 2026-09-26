package com.example.traning.periodization;

import com.example.traning.trainer.TrainerAdviceService;
import com.example.traning.user.User;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * トレーナーからの期分けプログラムの「案」とトレーニーの承認（詳細設計書3-5節、2026-09-23 USER確定）。
 *
 * <p>トレーナーの操作だけでトレーニーのサイクルが作られることはない。トレーニーが「今すぐ切り替える」 「今のプログラムが終わったら開始（予約）」「断る」から選ぶ。
 */
@Service
@RequiredArgsConstructor
public class ProgramCycleProposalService {

  private final ProgramCycleProposalDao proposalDao;
  private final ProgramCycleDao programCycleDao;
  private final PeriodizationService periodizationService;
  private final TrainerAdviceService trainerAdviceService;

  /** 送信前の確認表示用に、宛先トレーニーの返事待ちの案（新しい案で置き換わる）と予約中の案（そのまま残る）を返す。 担当関係の検証はsendProposalと同じ。 */
  @Transactional(readOnly = true)
  public OutstandingProposals getOutstanding(User trainer, Long traineeUserId) {
    requireAssigned(trainer, traineeUserId);
    return new OutstandingProposals(
        proposalDao.selectPendingByTrainee(traineeUserId),
        proposalDao.selectScheduledByTrainee(traineeUserId));
  }

  /** 宛先トレーニーの未完了の案。pendingは新しい案を送ると置き換わり、scheduledはそのまま残る。 */
  public record OutstandingProposals(
      List<ProgramCycleProposal> pending, List<ProgramCycleProposal> scheduled) {}

  private void requireAssigned(User trainer, Long traineeUserId) {
    if (traineeUserId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "宛先を選択してください");
    }
    long trainerId = trainer.getUserId().longValue();
    boolean assigned =
        trainerAdviceService.listTrainees(trainer).stream()
            .anyMatch(
                u ->
                    u.getUserId().longValue() == traineeUserId
                        && u.getAssignedTrainerId() != null
                        && u.getAssignedTrainerId() == trainerId);
    if (!assigned) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "担当トレーニー以外には案を送れません");
    }
  }

  /** 案を送る。担当関係とプリセットの参照範囲（宛先トレーニーの店舗と全組織共通）を検証し、PENDINGで保存する。 */
  @Transactional
  public Long sendProposal(User trainer, Long traineeUserId, Long presetProgramId) {
    requireAssigned(trainer, traineeUserId);
    long trainerId = trainer.getUserId().longValue();
    PresetProgram preset = periodizationService.findVisiblePreset(traineeUserId, presetProgramId);

    // 2026-09-24 USER確定B: 返事待ちの前の案は新しい案に置き換える（トレーニーには常に最新の1件だけが届く）。
    // 予約中（SCHEDULED）の案はそのまま残す。
    proposalDao.supersedePendingByTrainee(traineeUserId);

    // 案の中身はプリセットからコピーして案ごとに持つ（開始前にトレーナーが編集できるようにするため）
    PeriodizationViews.CustomCycleInput content = periodizationService.presetContent(preset);
    ProgramCycleProposal p = new ProgramCycleProposal();
    p.setTraineeUserId(traineeUserId);
    p.setTrainerUserId(trainerId);
    p.setSourcePresetProgramId(preset.getId());
    p.setName(content.name());
    p.setTotalWeeks(content.totalWeeks());
    p.setStatus(ProposalStatus.PENDING.name());
    proposalDao.insert(p);
    saveContent(p.getId(), content);
    return p.getId();
  }

  /**
   * 開始前（返事待ち・予約中）の案の中身をトレーナーが編集する（2026-09-26 USER確定）。トレーニーの承認は不要。
   * 送ったトレーナー本人で、かつ現在も担当であることが必要。入力チェックは白紙作成と同じ。
   */
  @Transactional
  public void updateContent(
      User trainer, Long proposalId, PeriodizationViews.CustomCycleInput input) {
    ProgramCycleProposal p = requireOwnProposal(trainer, proposalId);
    if (!ProposalStatus.PENDING.name().equals(p.getStatus())
        && !ProposalStatus.SCHEDULED.name().equals(p.getStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "開始前の案だけ編集できます");
    }
    PeriodizationViews.CustomCycleInput valid = periodizationService.validateCustomCycle(input);
    proposalDao.deleteItemsByProposalId(p.getId());
    proposalDao.deleteDayTemplatesByProposalId(p.getId());
    proposalDao.deleteWeeksByProposalId(p.getId());
    saveContent(p.getId(), valid);
    proposalDao.updateContentMeta(p.getId(), valid.name(), valid.totalWeeks(), LocalDateTime.now());
  }

  /** 返事待ちの案を取り下げる（2026-09-26 USER確定B）。予約中・開始後は取り下げ不可。 */
  @Transactional
  public void withdraw(User trainer, Long proposalId) {
    ProgramCycleProposal p = requireOwnProposal(trainer, proposalId);
    if (!ProposalStatus.PENDING.name().equals(p.getStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "返事待ちの案だけ取り下げられます");
    }
    proposalDao.updateStatus(p.getId(), ProposalStatus.WITHDRAWN.name());
  }

  /** 案の中身（トレーニーの案カード・トレーナーの編集画面の表示用）。宛先トレーニーまたは送ったトレーナーのみ。 */
  @Transactional(readOnly = true)
  public PeriodizationViews.CustomCycleInput getContent(Long viewerUserId, Long proposalId) {
    ProgramCycleProposal p =
        proposalDao
            .selectById(proposalId)
            .filter(
                x ->
                    x.getTraineeUserId().equals(viewerUserId)
                        || x.getTrainerUserId().equals(viewerUserId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "案が見つかりません"));
    return periodizationService.proposalContent(p);
  }

  private ProgramCycleProposal requireOwnProposal(User trainer, Long proposalId) {
    long trainerId = trainer.getUserId().longValue();
    ProgramCycleProposal p =
        proposalDao
            .selectById(proposalId)
            .filter(x -> x.getTrainerUserId() == trainerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "案が見つかりません"));
    requireAssigned(trainer, p.getTraineeUserId());
    return p;
  }

  private void saveContent(Long proposalId, PeriodizationViews.CustomCycleInput content) {
    for (PeriodizationViews.WeekInput wi : content.weeks()) {
      ProgramCycleProposalWeek w = new ProgramCycleProposalWeek();
      w.setProposalId(proposalId);
      w.setWeekNumber(wi.weekNumber());
      w.setTargetIntensityPct(wi.targetIntensityPct());
      w.setDeload(Boolean.TRUE.equals(wi.deload()));
      proposalDao.insertWeek(w);
    }
    for (PeriodizationViews.DayInput di : content.days()) {
      ProgramCycleProposalDayTemplate d = new ProgramCycleProposalDayTemplate();
      d.setProposalId(proposalId);
      d.setWeekNumber(di.weekNumber());
      d.setDayOfWeek(di.dayOfWeek());
      d.setPartCode(di.partCode());
      proposalDao.insertDayTemplate(d);
      int order = 1;
      for (PeriodizationViews.ItemInput ii : di.items()) {
        ProgramCycleProposalDayTemplateItem item = new ProgramCycleProposalDayTemplateItem();
        item.setDayTemplateId(d.getId());
        item.setItemName(ii.itemName());
        item.setDisplayOrder(order++);
        item.setTargetSets(ii.targetSets());
        proposalDao.insertDayTemplateItem(item);
      }
    }
  }

  @Transactional(readOnly = true)
  public List<ProgramCycleProposal> listPendingForTrainee(Long traineeUserId) {
    return proposalDao.selectPendingByTrainee(traineeUserId);
  }

  @Transactional(readOnly = true)
  public List<ProgramCycleProposal> listScheduledForTrainee(Long traineeUserId) {
    return proposalDao.selectScheduledByTrainee(traineeUserId);
  }

  @Transactional(readOnly = true)
  public List<ProgramCycleProposal> listSentByTrainer(Long trainerUserId) {
    return proposalDao.selectByTrainer(trainerUserId);
  }

  /**
   * トレーニーが案に対して選ぶ。本人宛てかつ承認待ち（PENDING）の案のみ受け付ける。
   *
   * @return START_NOWの場合は開始したサイクルID、それ以外はnull
   */
  @Transactional
  public Long respond(Long traineeUserId, Long proposalId, ProposalResponse response) {
    if (response == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "選択肢を指定してください");
    }
    ProgramCycleProposal p =
        proposalDao
            .selectById(proposalId)
            .filter(x -> x.getTraineeUserId().equals(traineeUserId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "案が見つかりません"));
    if (!ProposalStatus.PENDING.name().equals(p.getStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "この案はすでに選択済みです");
    }
    LocalDateTime now = LocalDateTime.now();
    return switch (response) {
      case DECLINE -> {
        proposalDao.markResponded(p.getId(), ProposalStatus.DECLINED.name(), now);
        yield null;
      }
      case SCHEDULE -> {
        // 2026-09-24 USER確定B: 新しい案を予約できるのは、今の予約が始まった後
        if (!proposalDao.selectScheduledByTrainee(traineeUserId).isEmpty()) {
          throw new ResponseStatusException(
              HttpStatus.CONFLICT, "すでに予約中の案があります。今すぐ切り替えるか断るを選んでください");
        }
        if (programCycleDao.selectActiveByUserId(traineeUserId).isEmpty()) {
          throw new ResponseStatusException(
              HttpStatus.CONFLICT, "実施中のプログラムが無いため予約できません。今すぐ始めるか断るを選んでください");
        }
        proposalDao.markResponded(p.getId(), ProposalStatus.SCHEDULED.name(), now);
        yield null;
      }
      case START_NOW -> {
        // 予約中の別の案があれば、本人が別のプログラムに切り替えたものとして取り消す（2026-09-26 USER確定B）
        periodizationService.cancelScheduledProposalsBySwitch(traineeUserId);
        Long cycleId = startFromProposal(p, LocalDate.now());
        proposalDao.markResponded(p.getId(), ProposalStatus.STARTED.name(), now);
        proposalDao.markStarted(p.getId(), cycleId, now);
        yield cycleId;
      }
    };
  }

  /** 案ごとに持つ中身（編集後の内容）から TRAINER_MANAGED のサイクルを開始する（実施中のサイクルは退避される）。 */
  private Long startFromProposal(ProgramCycleProposal p, LocalDate startDate) {
    return periodizationService.createCycleFromContent(
        p.getTraineeUserId(),
        periodizationService.proposalContent(p),
        CycleTier.TRAINER_MANAGED,
        p.getSourcePresetProgramId(),
        p.getTrainerUserId(),
        startDate);
  }
}
