package com.example.traning.smarttrainer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.traning.dao.TrainingMasterDao;
import com.example.traning.entity.TrainingItemMaster;
import com.example.traning.entity.TrainingMaster;
import com.example.traning.organization.Organization;
import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * itバグ-32: {@link MasterUpdateService#importCsv}で、Doma自動生成SQL（全列を列挙）に渡すエンティティの NOT
 * NULL列（is_compound等）が設定・引き継ぎされていることを検証する。
 */
@ExtendWith(MockitoExtension.class)
class MasterUpdateServiceTest {

  @Mock private TrainingMasterDao trainingMasterDao;

  @TempDir Path tempDir;

  private MasterUpdateService service;

  @BeforeEach
  void setUp() {
    service = new MasterUpdateService(trainingMasterDao);
    ReflectionTestUtils.setField(service, "allowedDirectory", tempDir.toString());
  }

  private File writeCsv(String... rows) throws Exception {
    Path csv = tempDir.resolve("master.csv");
    StringBuilder sb = new StringBuilder("parts_code,item_name,master_flg\n");
    for (String row : rows) {
      sb.append(row).append('\n');
    }
    Files.writeString(csv, sb.toString(), StandardCharsets.UTF_8);
    return csv.toFile();
  }

  private TrainingMaster part(String partCode) {
    TrainingMaster part = new TrainingMaster();
    part.setPartCode(partCode);
    return part;
  }

  private TrainingItemMaster existingItem(
      long id, String partCode, String itemName, int masterFlg, boolean isCompound) {
    TrainingItemMaster item = new TrainingItemMaster();
    item.setId(id);
    item.setPartCode(partCode);
    item.setItemName(itemName);
    item.setDisplayOrder(3);
    item.setMasterFlg(masterFlg);
    item.setOrganizationId(5L);
    item.setRangeOfMotionM(new BigDecimal("0.55"));
    item.setIsCompound(isCompound);
    return item;
  }

  @SuppressWarnings("unchecked")
  @Test
  void importCsv_新規種目はis_compound等のNOT_NULL列が設定されて登録される() throws Exception {
    when(trainingMasterDao.selectItemsByPart("CHEST")).thenReturn(List.of());
    File csv = writeCsv("CHEST,新規種目,1");

    int processed = service.importCsv(csv, List.of(part("CHEST")));

    ArgumentCaptor<List<TrainingItemMaster>> captor = ArgumentCaptor.forClass(List.class);
    verify(trainingMasterDao).batchUpsert(captor.capture());
    assertThat(processed).isEqualTo(1);
    TrainingItemMaster inserted = captor.getValue().get(0);
    assertThat(inserted.getIsCompound()).isTrue();
    assertThat(inserted.getRangeOfMotionM()).isEqualByComparingTo("0.40");
    assertThat(inserted.getOrganizationId()).isEqualTo(Organization.ALL_ORGANIZATION_ID);
    assertThat(inserted.getMasterFlg()).isEqualTo(1);
    assertThat(inserted.getDisplayOrder()).isEqualTo(1);
    verify(trainingMasterDao, never()).batchUpdateMasterFlg(any());
  }

  @SuppressWarnings("unchecked")
  @Test
  void importCsv_既存種目のmaster_flg更新ではis_compound等の既存値が引き継がれる() throws Exception {
    // 単関節種目（is_compound=false）が複合扱いに書き換わらないことも合わせて確認する
    when(trainingMasterDao.selectItemsByPart("ARM"))
        .thenReturn(List.of(existingItem(10L, "ARM", "アームカール", 1, false)));
    File csv = writeCsv("ARM,アームカール,0");

    int processed = service.importCsv(csv, List.of(part("ARM")));

    ArgumentCaptor<List<TrainingItemMaster>> captor = ArgumentCaptor.forClass(List.class);
    verify(trainingMasterDao).batchUpdateMasterFlg(captor.capture());
    assertThat(processed).isEqualTo(1);
    TrainingItemMaster updated = captor.getValue().get(0);
    assertThat(updated.getId()).isEqualTo(10L);
    assertThat(updated.getMasterFlg()).isEqualTo(0);
    assertThat(updated.getIsCompound()).isFalse();
    assertThat(updated.getOrganizationId()).isEqualTo(5L);
    assertThat(updated.getRangeOfMotionM()).isEqualByComparingTo("0.55");
    assertThat(updated.getDisplayOrder()).isEqualTo(3);
    verify(trainingMasterDao, never()).batchUpsert(any());
  }

  @Test
  void importCsv_既存種目でmaster_flgに差分が無ければ更新しない() throws Exception {
    when(trainingMasterDao.selectItemsByPart("ARM"))
        .thenReturn(List.of(existingItem(10L, "ARM", "アームカール", 1, false)));
    File csv = writeCsv("ARM,アームカール,1");

    int processed = service.importCsv(csv, List.of(part("ARM")));

    assertThat(processed).isZero();
    verify(trainingMasterDao, never()).batchUpsert(any());
    verify(trainingMasterDao, never()).batchUpdateMasterFlg(any());
  }
}
