package com.example.traning.smarttrainer.service;

import com.example.traning.dao.TrainingMasterDao;
import com.example.traning.entity.TrainingItemMaster;
import com.example.traning.organization.Organization;
import com.example.traning.user.Role;
import com.example.traning.user.User;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 種目マスタの個別追加（ita1-1 未実施分。組織/店舗固有種目の登録UI）。
 *
 * <p>既存のCSV一括登録（{@link MasterUpdateService}）はプラットフォーム共通種目専用のまま変更しない。
 * 組織・店舗固有の種目はこのサービス経由の個別追加フォームでのみ登録する。
 *
 * <p>公開範囲は操作者のロールに応じてサーバー側で自動的に決定する（クライアントからの指定は受け付けない）。 ROLE_ADMIN→共通（{@link
 * Organization#ALL_ORGANIZATION_ID}）、ROLE_ORG_ADMIN→自組織（GYM）、 ROLE_STORE_ADMIN→自店舗（STORE）。
 */
@Service
public class TrainingItemMasterService {

  private final TrainingMasterDao trainingMasterDao;

  public TrainingItemMasterService(TrainingMasterDao trainingMasterDao) {
    this.trainingMasterDao = trainingMasterDao;
  }

  @Transactional
  public void addItem(String partCode, String itemName, User currentAdmin) {
    Long organizationId = resolveOrganizationId(currentAdmin);

    List<TrainingItemMaster> existing = trainingMasterDao.selectItemsByPart(partCode);
    int maxOrder =
        existing.stream()
            .mapToInt(i -> i.getDisplayOrder() != null ? i.getDisplayOrder() : 0)
            .max()
            .orElse(0);

    TrainingItemMaster item = new TrainingItemMaster();
    item.setPartCode(partCode);
    item.setItemName(itemName);
    item.setMasterFlg(1);
    item.setOrganizationId(organizationId);
    item.setDisplayOrder(maxOrder + 1);
    // range_of_motion_mはDB上NOT NULL（DEFAULT 0.40）。Domaの自動生成INSERTは全列を明示的に列挙するため、
    // ここで未設定のままだとNULLが明示的にバインドされDBのDEFAULT句が効かず登録に失敗する。
    item.setRangeOfMotionM(new BigDecimal("0.40"));
    trainingMasterDao.insertItem(item);
  }

  private Long resolveOrganizationId(User currentAdmin) {
    Role role = Role.fromValue(currentAdmin.getRole());
    if (role == Role.ADMIN) {
      return Organization.ALL_ORGANIZATION_ID;
    }
    if (role == Role.ORG_ADMIN || role == Role.STORE_ADMIN) {
      return currentAdmin.getOrganizationId();
    }
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "種目を追加する権限がありません");
  }
}
