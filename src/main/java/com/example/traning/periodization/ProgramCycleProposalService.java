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

  /** 案を送る。担当関係とプリセットの参照範囲（宛先トレーニーの店舗と全組織共通）を検証し、PENDINGで保存する。 */
  @Transactional
  public Long sendProposal(User trainer, Long traineeUserId, Long presetProgramId) {
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
    PresetProgram preset = periodizationService.findVisiblePreset(traineeUserId, presetProgramId);

    ProgramCycleProposal p = new ProgramCycleProposal();
    p.setTraineeUserId(traineeUserId);
    p.setTrainerUserId(trainerId);
    p.setPresetProgramId(preset.getId());
    p.setStatus(ProposalStatus.PENDING.name());
    proposalDao.insert(p);
    return p.getId();
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
        if (programCycleDao.selectActiveByUserId(traineeUserId).isEmpty()) {
          throw new ResponseStatusException(
              HttpStatus.CONFLICT, "実施中のプログラムが無いため予約できません。今すぐ始めるか断るを選んでください");
        }
        proposalDao.markResponded(p.getId(), ProposalStatus.SCHEDULED.name(), now);
        yield null;
      }
      case START_NOW -> {
        Long cycleId = startFromProposal(p, LocalDate.now());
        proposalDao.markResponded(p.getId(), ProposalStatus.STARTED.name(), now);
        proposalDao.markStarted(p.getId(), cycleId, now);
        yield cycleId;
      }
    };
  }

  /** 案のプリセットから TRAINER_MANAGED のサイクルを開始する（実施中のサイクルは退避される）。 */
  private Long startFromProposal(ProgramCycleProposal p, LocalDate startDate) {
    PresetProgram preset =
        periodizationService.findVisiblePreset(p.getTraineeUserId(), p.getPresetProgramId());
    return periodizationService.createCycleFromPreset(
        p.getTraineeUserId(), preset, CycleTier.TRAINER_MANAGED, p.getTrainerUserId(), startDate);
  }
}
