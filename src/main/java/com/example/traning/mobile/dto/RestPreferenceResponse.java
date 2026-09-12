package com.example.traning.mobile.dto;

import com.example.traning.restpreference.UserItemRestPreference;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RestPreferenceResponse {

  private String itemName;
  private int restSeconds;

  public static RestPreferenceResponse from(UserItemRestPreference pref) {
    return new RestPreferenceResponse(pref.getItemName(), pref.getRestSeconds());
  }
}
