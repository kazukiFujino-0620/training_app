package com.example.traning.organization;

import com.example.traning.user.Role;
import com.example.traning.user.User;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * スタッフ（システム管理者・ジム管理者・店舗管理者・トレーナー）が、ある会員に対して何をできるかを判定する（機能見直し-1-#15）。
 *
 * <ul>
 *   <li>システム管理者・ジム管理者・店舗管理者: {@link OrganizationScopeResolver} の範囲内なら {@link
 *       MemberAccessLevel#FULL}（今までどおり）
 *   <li>トレーナー: 担当会員（{@code users.assigned_trainer_id}が自分）は {@link MemberAccessLevel#FULL}。
 *       自分の店舗の担当外の会員（ROLE_USER）は {@link MemberAccessLevel#RECORDS_ONLY}。それ以外（他の店舗の会員・スタッフ）は {@link
 *       MemberAccessLevel#NONE}（Q15-1）
 *   <li>会員: 自分自身のみ {@link MemberAccessLevel#FULL}
 * </ul>
 *
 * <p>健康に関わる情報を見られるかは別に {@link com.example.traning.health.HealthInfoAccessPolicy} で判定する。
 */
@Component
public class MemberAccessPolicy {

  private final OrganizationScopeResolver organizationScopeResolver;

  public MemberAccessPolicy(OrganizationScopeResolver organizationScopeResolver) {
    this.organizationScopeResolver = organizationScopeResolver;
  }

  public MemberAccessLevel resolve(User viewer, User member) {
    if (viewer == null || member == null || viewer.getUserId() == null) {
      return MemberAccessLevel.NONE;
    }
    if (viewer.getUserId().equals(member.getUserId())) {
      return MemberAccessLevel.FULL;
    }
    Role role = Role.fromValue(viewer.getRole());
    if (role == null) {
      return MemberAccessLevel.NONE;
    }
    switch (role) {
      case ADMIN, ORG_ADMIN, STORE_ADMIN -> {
        return organizationScopeResolver.canAccessOrganization(viewer, member.getOrganizationId())
            ? MemberAccessLevel.FULL
            : MemberAccessLevel.NONE;
      }
      case TRAINER -> {
        if (!Role.USER.value().equals(member.getRole())) {
          return MemberAccessLevel.NONE;
        }
        if (isAssignedTo(member, viewer)) {
          return MemberAccessLevel.FULL;
        }
        return organizationScopeResolver.canAccessOrganization(viewer, member.getOrganizationId())
            ? MemberAccessLevel.RECORDS_ONLY
            : MemberAccessLevel.NONE;
      }
      default -> {
        return MemberAccessLevel.NONE;
      }
    }
  }

  /** トレーニング記録を見られない場合は403を投げる。 */
  public MemberAccessLevel requireRecordsView(User viewer, User member) {
    MemberAccessLevel level = resolve(viewer, member);
    if (!level.canViewRecords()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "対象のユーザーにアクセスする権限がありません");
    }
    return level;
  }

  /**
   * トレーナーが一覧に出してよい会員か（担当会員、または自分の店舗の会員）。{@code accessibleOrganizationIds} は {@link
   * OrganizationScopeResolver#resolveAccessibleOrganizationIds} の結果を渡す（一覧で毎回引き直さないため）。
   */
  public boolean isVisibleToTrainer(
      User trainer, User member, Set<Long> accessibleOrganizationIds) {
    if (!Role.USER.value().equals(member.getRole())) {
      return false;
    }
    return isAssignedTo(member, trainer)
        || (accessibleOrganizationIds != null
            && accessibleOrganizationIds.contains(member.getOrganizationId()));
  }

  /**
   * 会員に対してスタッフとして対応できるか（#12の「対応済みにする」、#20の目標の提案）。トレーナーは担当会員のみ。
   * 管理者（店舗・ジム・システム管理者）は範囲内の会員（担当でなくてもよい）。対象は会員（ROLE_USER）だけ。
   */
  public boolean isResponsibleStaff(User viewer, User member) {
    if (viewer == null || member == null || !Role.USER.value().equals(member.getRole())) {
      return false;
    }
    Role role = Role.fromValue(viewer.getRole());
    if (role == null) return false;
    return switch (role) {
      case TRAINER -> isAssignedTo(member, viewer);
      case ADMIN, ORG_ADMIN, STORE_ADMIN -> resolve(viewer, member) == MemberAccessLevel.FULL;
      default -> false;
    };
  }

  /** 会員の担当トレーナーが{@code staff}か。 */
  public static boolean isAssignedTo(User member, User staff) {
    return member.getAssignedTrainerId() != null
        && staff.getUserId() != null
        && member.getAssignedTrainerId().longValue() == staff.getUserId().longValue();
  }
}
