package com.example.traning.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** itバグ-13: テンプレート一覧・編集画面での種目グループ化表示に使う{@link TrainingTemplate#getItemGroups()}を検証する。 */
class TrainingTemplateItemGroupTest {

  private TrainingTemplateItem item(
      String itemName, int setNumber, String setType, Integer weight) {
    TrainingTemplateItem item = new TrainingTemplateItem();
    item.setItemName(itemName);
    item.setSetNumber(setNumber);
    item.setSetType(setType);
    item.setWeight(weight == null ? null : BigDecimal.valueOf(weight));
    return item;
  }

  @Test
  void 種目が空なら空リストを返す() {
    TrainingTemplate template = new TrainingTemplate();
    template.setItems(List.of());

    assertThat(template.getItemGroups()).isEmpty();
  }

  @Test
  void itemsがnullでも空リストを返す() {
    TrainingTemplate template = new TrainingTemplate();

    assertThat(template.getItemGroups()).isEmpty();
  }

  @Test
  void 同一種目名のセットを1グループにまとめる() {
    TrainingTemplate template = new TrainingTemplate();
    template.setItems(
        List.of(
            item("ベンチプレス", 1, "DROP", 60),
            item("ベンチプレス", 2, "MAIN", 50),
            item("ルーマニアンデッドリフト", 1, "MAIN", null)));

    List<TrainingTemplate.ItemGroup> groups = template.getItemGroups();

    assertThat(groups).hasSize(2);
    assertThat(groups.get(0).itemName()).isEqualTo("ベンチプレス");
    assertThat(groups.get(0).sets()).hasSize(2);
    assertThat(groups.get(1).itemName()).isEqualTo("ルーマニアンデッドリフト");
    assertThat(groups.get(1).sets()).hasSize(1);
  }

  @Test
  void 種目の登場順を維持する() {
    TrainingTemplate template = new TrainingTemplate();
    template.setItems(
        List.of(
            item("スクワット", 1, "MAIN", 80),
            item("ベンチプレス", 1, "MAIN", 60),
            item("スクワット", 2, "MAIN", 80)));

    List<TrainingTemplate.ItemGroup> groups = template.getItemGroups();

    assertThat(groups)
        .extracting(TrainingTemplate.ItemGroup::itemName)
        .containsExactly("スクワット", "ベンチプレス");
    assertThat(groups.get(0).sets()).hasSize(2);
  }
}
