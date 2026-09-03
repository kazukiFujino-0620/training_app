package com.example.traning.staticpage;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * App Store / Google Play審査提出用のプライバシーポリシー公開ページ（ita3-3関連、審査準備対応）。
 * 未ログインでも閲覧できる必要があるため{@code SecurityConfig}のPRIVACY_PATHで許可している。
 */
@Controller
public class PrivacyController {

  @GetMapping("/privacy")
  public String privacy() {
    return "privacy";
  }
}
