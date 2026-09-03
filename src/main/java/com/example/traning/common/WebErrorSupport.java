package com.example.traning.common;

import org.springframework.ui.Model;

/** Controller側で errorMessage と errorCode を必ず対で model にセットするためのヘルパー。 */
public final class WebErrorSupport {

  private WebErrorSupport() {}

  public static void setError(Model model, String message, String errorCode) {
    model.addAttribute("errorMessage", message);
    model.addAttribute("errorCode", errorCode);
  }
}
