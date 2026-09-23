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

  private ProgramCycleProposal pending(Long traineeId) {
    ProgramCycleProposal p = new ProgramCycleProposal();
    p.setId(300L);
    p.setTraineeUserId(traineeId);
    p.setTrainerUserId(42L);
    p.setPresetProgramId(7L);
    p.setStatus("PENDING");
    return p;
  }

  @Test
  void sendProposal_担当トレーニーにはPENDINGで保存しサイクルは作らない() {
    when(trainerAdviceService.listTrainees(trainer))
        .thenReturn(List.of(User.builder().userId(5).assignedTrainerId(42L).build()));
    when(periodizationService.findVisiblePreset(5L, 7L)).thenReturn(preset());

    service.sendProposal(trainer, 5L, 7L);

    ArgumentCaptor<ProgramCycleProposal> saved =
        ArgumentCaptor.forClass(ProgramCycleProposal.class);
    verify(proposalDao).insert(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo("PENDING");
    assertThat(saved.getValue().getTraineeUserId()).isEqualTo(5L);
    verify(periodizationService, never()).createCycleFromPreset(any(), any(), any(), any(), any());
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
    when(periodizationService.findVisiblePreset(5L, 7L)).thenReturn(preset());
    when(periodizationService.createCycleFromPreset(
            eq(5L), any(), eq(CycleTier.TRAINER_MANAGED), eq(42L), any()))
        .thenReturn(88L);

    assertThat(service.respond(5L, 300L, ProposalResponse.START_NOW)).isEqualTo(88L);
    verify(proposalDao).markStarted(eq(300L), eq(88L), any());
  }

  @Test
  void respond_予約は実施中サイクルがあればSCHEDULED() {
    when(proposalDao.selectById(300L)).thenReturn(Optional.of(pending(5L)));
    when(programCycleDao.selectActiveByUserId(5L)).thenReturn(Optional.of(new ProgramCycle()));

    service.respond(5L, 300L, ProposalResponse.SCHEDULE);

    verify(proposalDao).markResponded(eq(300L), eq("SCHEDULED"), any());
    verify(periodizationService, never()).createCycleFromPreset(any(), any(), any(), any(), any());
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
