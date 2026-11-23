package com.example.traning.follow;

import java.time.LocalDateTime;
import lombok.Data;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

/** 「対応済みにする」の記録（機能見直し-1-#12 Q12-2）。押した時点の理由の種類・押した人・日時。 */
@Entity
@Table(name = "member_follow_acknowledgements")
@Data
public class MemberFollowAcknowledgement {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "member_user_id")
  private Long memberUserId;

  /** {@link FollowReasonType}の名前のカンマ区切り。 */
  @Column(name = "reason_types")
  private String reasonTypes;

  @Column(name = "acted_by")
  private Long actedBy;

  @Column(name = "acted_at")
  private LocalDateTime actedAt;

  /** 押した人の名前（一覧の表示用。usersと結合して取得し、保存はしない）。 */
  @Column(name = "acted_by_name", insertable = false, updatable = false)
  private String actedByName;
}
