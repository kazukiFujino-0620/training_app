package com.example.traning.periodization;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 期分けプログラム画面（機能見直し-1-#3、モックアップ版11の01/02/03/04/06/08）。 表示内容は画面のJSが /api/periodization/**
 * から取得する（状態によって01・02・04を切り替え、08の案カードを最上部に重ねる）。
 */
@Controller
@PreAuthorize("isAuthenticated()")
public class PeriodizationPageController {

  @GetMapping("/user/program-cycle")
  public String programCycle() {
    return "user/program_cycle";
  }
}
