package com.example.traning.periodization.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/** 曜日別種目リストの全置換保存リクエスト（Web用）。itemsの並び順がそのままdisplay_orderになる。 */
public record CustomizeItemsRequest(@NotNull List<Item> items) {
  public record Item(String itemName, Integer targetSets) {}
}
