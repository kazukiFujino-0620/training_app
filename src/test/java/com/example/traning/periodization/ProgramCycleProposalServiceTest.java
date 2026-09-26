package com.example.traning.periodization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.traning.trainer.TrainerAdviceService;
import com.example.traning.user.User;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

/** 詳細設計書3-5節（2026-09-23 USER確定）: トレーナーの案とトレーニーの承認。 */
@ExtendWith(MockitoExtension.class)
class ProgramCycleProposalServiceTest {

  @Mock private ProgramCycleProposalDao proposalDao;
  @Mock private ProgramCycleDao programCycleDao;
  @Mock private PeriodizationService periodizationService;
  @Mock private TrainerAdviceService trainerAdviceService;

  private ProgramCycleProposalService service;
  private final User trainer = User.builder().userId(42).role("ROLE_STORE_ADMIN").build();

  @BeforeEach
  void setUp() {
    service =
        new ProgramCycleProposalService(
            proposalDao, programCycleDao, periodizationService, trainerAdviceService);
  }

  private PresetProgram preset() {
    PresetProgram p = new PresetProgram();
    p.setId(7L);
    return p;
  }

  private static PeriodizationViews.CustomCycleInput content(String name, int weeks) {
    List<PeriodizationViews.WeekInput> w = new java.util.ArrayList<>();
    for (int i = 1; i <= weeks; i++) {
      w.add(new PeriodizationViews.WeekInput(i, new java.math.BigDecimal("70.0"), i == weeks));
    }
    return new PeriodizationViews.CustomCycleInput(
        name,
        weeks,
        w,
        List.of(
            new PeriodizationViews.DayInput(
                1, "MON", "CHEST", List.of(new PeriodizationViews.ItemInput("ベンチプレス", 3)))));
  }

  private void assigned() {
    when(trainerAdviceService.listTrainees(trainer))
        .thenReturn(List.of(User.builder().userId(5).assignedTrainerId(42L).build()));
  }

  @Test
  void updateContent_返事待ちと予約中の案は中身を置き換え編集日時を記録する() {
    assigned();
    ProgramCycleProposal scheduled = pending(5L);
    scheduled.setStatus("SCHEDULED");
    when(proposalDao.selectById(300L)).thenReturn(Optional.of(scheduled));
    PeriodizationViews.CustomCycleInput edited = content("王道4週（佐藤編集）", 3);
    when(periodizationService.validateCustomCycle(edited)).thenReturn(edited);

    service.updateContent(trainer, 300L, edited);

    verify(proposalDao).deleteItemsByProposalId(300L);
    verify(proposalDao).deleteDayTemplatesByProposalId(300L);
    verify(proposalDao).deleteWeeksByProposalId(300L);
    verify(proposalDao, org.mockito.Mockito.times(3)).insertWeek(any());
    verify(proposalDao).updateContentMeta(eq(300L), eq("王道4週（佐藤編集）"), eq(3), any());
  }

  @Test
  void updateContent_開始後の案は編集できず409() {
    assigned();
    ProgramCycleProposal started = pending(5L);
    started.setStatus("STARTED");
    when(proposalDao.selectById(300L)).thenReturn(Optional.of(started));
    assertThatThrownBy(() -> service.updateContent(trainer, 300L, content("x", 3)))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409");
    verify(proposalDao, never()).deleteWeeksByProposalId(anyLong());
  }

  @Test
  void updateContent_他のトレーナーが送った案は404() {
    ProgramCycleProposal other = pending(5L);
    other.setTrainerUserId(99L);
    when(proposalDao.selectById(300L)).thenReturn(Optional.of(other));
    assertThatThrownBy(() -> service.updateContent(trainer, 300L, content("x", 3)))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void withdraw_返事待ちの案だけ取り下げられる() {
    assigned();
    when(proposalDao.selectById(300L)).thenReturn(Optional.of(pending(5L)));
    service.withdraw(trainer, 300L);
    verify(proposalDao).updateStatus(300L, "WITHDRAWN");

    ProgramCycleProposal scheduled = pending(5L);
    scheduled.setId(301L);
    scheduled.setStatus("SCHEDULED");
    when(proposalDao.selectById(301L)).thenReturn(Optional.of(scheduled));
    assertThatThrownBy(() -> service.withdraw(trainer, 301L))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409");
  }

  @Test
  void respond_取り下げられた案には選択できず409() {
    ProgramCycleProposal withdrawn = pending(5L);
    withdrawn.setStatus("WITHDRAWN");
    when(proposalDao.selectById(300L)).thenReturn(Optional.of(withdrawn));
    assertThatThrownBy(() -> service.respond(5L, 300L, ProposalResponse.START_NOW))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409");
  }

  private ProgramCycleProposal pending(Long traineeId) {
    ProgramCycleProposal p = new ProgramCycleProposal();
    p.setId(300L);
    p.setTraineeUserId(traineeId);
    p.setTrainerUserId(42L);
    p.setSourcePresetProgramId(7L);
    p.setName("王道4週");
    p.setTotalWeeks(4);
    p.setStatus("PENDING");
    return p;
  }

  @Test
  void sendProposal_担当トレーニーにはPENDINGで保存しサイクルは作らない() {
    when(trainerAdviceService.listTrainees(trainer))
        .thenReturn(List.of(User.builder().userId(5).assignedTrainerId(42L).build()));
    when(periodizationService.findVisiblePreset(5L, 7L)).thenReturn(preset());
    when(periodizationService.presetContent(any())).thenReturn(content("王道4週", 4));

    service.sendProposal(trainer, 5L, 7L);

    ArgumentCaptor<ProgramCycleProposal> saved =
        ArgumentCaptor.forClass(ProgramCycleProposal.class);
    verify(proposalDao).insert(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo("PENDING");
    assertThat(saved.getValue().getTraineeUserId()).isEqualTo(5L);
    assertThat(saved.getValue().getSourcePresetProgramId()).isEqualTo(7L);
    // 案の中身はプリセットからコピーして案ごとに持つ
    verify(proposalDao, org.mockito.Mockito.times(4)).insertWeek(any());
    verify(proposalDao).insertDayTemplateItem(any());
    verify(periodizationService, never())
        .createCycleFromContent(any(), any(), any(), any(), any(), any());
    // 返事待ちの前の案は新しい案に置き換える（予約中は対象外のSQL）
    verify(proposalDao).supersedePendingByTrainee(5L);
  }

  @Test
  void getOutstanding_返事待ちと予約中の案を返す() {
    when(trainerAdviceService.listTrainees(trainer))
        .thenReturn(List.of(User.builder().userId(5).assignedTrainerId(42L).build()));
    ProgramCycleProposal scheduled = pending(5L);
    scheduled.setStatus("SCHEDULED");
    when(proposalDao.selectPendingByTrainee(5L)).thenReturn(List.of(pending(5L)));
    when(proposalDao.selectScheduledByTrainee(5L)).thenReturn(List.of(scheduled));

    var outstanding = service.getOutstanding(trainer, 5L);

    assertThat(outstanding.pending()).hasSize(1);
    assertThat(outstanding.scheduled()).hasSize(1);
  }

  @Test
  void sendProposal_担当でないトレーニーには403() {
    when(trainerAdviceService.listTrainees(trainer))
        .thenReturn(List.of(User.builder().userId(5).assignedTrainerId(99L).build()));
    assertThatThrownBy(() -> service.sendProposal(trainer, 5L, 7L))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("403");
    verify(proposalDao, never()).insert(any());
  }

  @Test
  void respond_今すぐ切り替えるとTRAINER_MANAGEDで開始し案をSTARTEDにする() {
    when(proposalDao.selectById(300L)).thenReturn(Optional.of(pending(5L)));
    PeriodizationViews.CustomCycleInput edited = content("王道4週（佐藤編集）", 4);
    when(periodizationService.proposalContent(any())).thenReturn(edited);
    when(periodizationService.createCycleFromContent(
            eq(5L), eq(edited), eq(CycleTier.TRAINER_MANAGED), eq(7L), eq(42L), any()))
        .thenReturn(88L);

    assertThat(service.respond(5L, 300L, ProposalResponse.START_NOW)).isEqualTo(88L);
    // 予約中の別の案があれば、本人が別のプログラムに切り替えたものとして取り消す
    verify(periodizationService).cancelScheduledProposalsBySwitch(5L);
    verify(proposalDao).markStarted(eq(300L), eq(88L), any());
  }

  @Test
  void respond_予約は実施中サイクルがあればSCHEDULED() {
    when(proposalDao.selectById(300L)).thenReturn(Optional.of(pending(5L)));
    when(programCycleDao.selectActiveByUserId(5L)).thenReturn(Optional.of(new ProgramCycle()));

    service.respond(5L, 300L, ProposalResponse.SCHEDULE);

    verify(proposalDao).markResponded(eq(300L), eq("SCHEDULED"), any());
    verify(periodizationService, never()).cancelScheduledProposalsBySwitch(anyLong());
    verify(periodizationService, never())
        .createCycleFromContent(any(), any(), any(), any(), any(), any());
  }

  @Test
  void respond_すでに予約中の案があれば新しい案は予約できず409() {
    when(proposalDao.selectById(300L)).thenReturn(Optional.of(pending(5L)));
    ProgramCycleProposal scheduled = pending(5L);
    scheduled.setStatus("SCHEDULED");
    when(proposalDao.selectScheduledByTrainee(5L)).thenReturn(List.of(scheduled));

    assertThatThrownBy(() -> service.respond(5L, 300L, ProposalResponse.SCHEDULE))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409");
    verify(proposalDao, never()).markResponded(anyLong(), any(), any());
  }

  @Test
  void respond_置き換えられた案には選択できず409() {
    ProgramCycleProposal superseded = pending(5L);
    superseded.setStatus("SUPERSEDED");
    when(proposalDao.selectById(300L)).thenReturn(Optional.of(superseded));
    assertThatThrownBy(() -> service.respond(5L, 300L, ProposalResponse.START_NOW))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409");
  }

  @Test
  void respond_実施中サイクルが無ければ予約は409() {
    when(proposalDao.selectById(300L)).thenReturn(Optional.of(pending(5L)));
    when(programCycleDao.selectActiveByUserId(5L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.respond(5L, 300L, ProposalResponse.SCHEDULE))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409");
  }

  @Test
  void respond_断るとDECLINED() {
    when(proposalDao.selectById(300L)).thenReturn(Optional.of(pending(5L)));
    service.respond(5L, 300L, ProposalResponse.DECLINE);
    verify(proposalDao).markResponded(eq(300L), eq("DECLINED"), any());
  }

  @Test
  void respond_他人宛ての案は404_選択済みは409() {
    when(proposalDao.selectById(300L)).thenReturn(Optional.of(pending(6L)));
    assertThatThrownBy(() -> service.respond(5L, 300L, ProposalResponse.DECLINE))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");

    ProgramCycleProposal done = pending(5L);
    done.setStatus("DECLINED");
    when(proposalDao.selectById(301L)).thenReturn(Optional.of(done));
    assertThatThrownBy(() -> service.respond(5L, 301L, ProposalResponse.START_NOW))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409");
    verify(proposalDao, never()).markResponded(anyLong(), any(), any());
  }
}
