package com.example.traning.smarttrainer.coaching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.traning.smarttrainer.recommendation.FatigueCalculator;
import com.example.traning.user.User;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** ita5-1 機能3: {@link AiFatigueCommentService}の同意ゲート・当日キャッシュを検証する。 */
@ExtendWith(MockitoExtension.class)
class AiFatigueCommentServiceTest {

  @Mock private AiFatigueCommentDao dao;
  @Mock private FatigueCoach fatigueCoach;

  private AiFatigueCommentService service;

  @BeforeEach
  void setUp() {
    service = new AiFatigueCommentService(dao, fatigueCoach);
  }

  private User user(int id, boolean consent) {
    return User.builder().userId(id).aiAdviceConsent(consent).build();
  }

  private FatigueCalculator.FatigueResult emptyResult() {
    return new FatigueCalculator.FatigueResult(
        new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>());
  }

  @Test
  void getOrGenerateTodayComment_同意していない場合は空を返しDAOを呼ばない() {
    User user = user(1, false);

    Optional<String> result = service.getOrGenerateTodayComment(user, emptyResult());

    assertThat(result).isEmpty();
    verify(dao, never()).selectByUserIdAndDate(any(), any());
    verify(fatigueCoach, never()).generateComment(any());
  }

  @Test
  void getOrGenerateTodayComment_当日キャッシュがあればそれを返す() {
    User user = user(1, true);
    AiFatigueComment cached = new AiFatigueComment();
    cached.setComment("キャッシュ済みコメント");
    when(dao.selectByUserIdAndDate(1L, LocalDate.now())).thenReturn(Optional.of(cached));

    Optional<String> result = service.getOrGenerateTodayComment(user, emptyResult());

    assertThat(result).contains("キャッシュ済みコメント");
    verify(fatigueCoach, never()).generateComment(any());
    verify(dao, never()).insert(any());
  }

  @Test
  void getOrGenerateTodayComment_キャッシュが無ければ生成して保存する() {
    User user = user(1, true);
    when(dao.selectByUserIdAndDate(1L, LocalDate.now())).thenReturn(Optional.empty());
    FatigueCalculator.FatigueResult fatigueResult = emptyResult();
    when(fatigueCoach.generateComment(fatigueResult)).thenReturn("生成コメント");
    when(fatigueCoach.source()).thenReturn("mock");

    Optional<String> result = service.getOrGenerateTodayComment(user, fatigueResult);

    assertThat(result).contains("生成コメント");
    ArgumentCaptor<AiFatigueComment> captor = ArgumentCaptor.forClass(AiFatigueComment.class);
    verify(dao).insert(captor.capture());
    assertThat(captor.getValue().getUserId()).isEqualTo(1L);
    assertThat(captor.getValue().getComment()).isEqualTo("生成コメント");
    assertThat(captor.getValue().getSource()).isEqualTo("mock");
  }
}
