package com.example.traning.mobile.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.traning.mobile.dto.MobileWorkoutGenerationDtos.AddExclusionRequest;
import com.example.traning.mobile.dto.MobileWorkoutGenerationDtos.GenerateRequest;
import com.example.traning.smarttrainer.autogen.WorkoutGenerationService;
import com.example.traning.smarttrainer.autogen.WorkoutGenerationViews.GenerateCommand;
import com.example.traning.smarttrainer.autogen.WorkoutGenerationViews.GeneratedAlternative;
import com.example.traning.smarttrainer.autogen.WorkoutGenerationViews.GeneratedItem;
import com.example.traning.smarttrainer.autogen.WorkoutGenerationViews.GenerationResult;
import com.example.traning.smarttrainer.autogen.WorkoutGenerationViews.Mode;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

/** 機能見直し-1-#4: メニュー自動作成のモバイルAPI。 */
@ExtendWith(MockitoExtension.class)
class MobileWorkoutGenerationControllerTest {

  private static final Long USER_ID = 5L;

  @Mock private WorkoutGenerationService service;

  private MockMvc mvc;
  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @BeforeEach
  void setUp() {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
    mvc =
        MockMvcBuilders.standaloneSetup(new MobileWorkoutGenerationController(service))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setControllerAdvice(new MobileExceptionHandler())
            .build();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private static GenerationResult sampleResult() {
    GeneratedItem item =
        new GeneratedItem(
            "ベンチプレス",
            "CHEST",
            "胸",
            true,
            false,
            4,
            10,
            70.0,
            120,
            "胸を指定。複合種目を先にしています",
            null,
            List.of(
                new GeneratedAlternative(
                    "ディップス", "CHEST", true, false, 10, null, 120, "記録が無いため…")));
    return new GenerationResult("OK", Mode.ADHOC, null, 70.0, 60, 53, List.of(item), List.of());
  }

  @Test
  void generate_JWTのユーザー本人で生成し_JSONのキーはレコードの名前どおり() throws Exception {
    when(service.generate(eq(USER_ID), any())).thenReturn(sampleResult());

    mvc.perform(
            post("/api/mobile/workout-generation/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"availableMinutes\":60,\"desiredPartCodes\":[\"CHEST\",\"ARM\"],"
                        + "\"availableEquipmentIds\":[1,2],\"excludeItemNames\":[\"スクワット\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OK"))
        .andExpect(jsonPath("$.mode").value("ADHOC"))
        .andExpect(jsonPath("$.estimatedMinutes").value(53))
        .andExpect(jsonPath("$.items[0].itemName").value("ベンチプレス"))
        .andExpect(jsonPath("$.items[0].compound").value(true))
        .andExpect(jsonPath("$.items[0].bodyweight").value(false))
        .andExpect(jsonPath("$.items[0].weightKg").value(70.0))
        .andExpect(jsonPath("$.items[0].alternatives[0].itemName").value("ディップス"))
        .andExpect(jsonPath("$.items[0].alternatives[0].weightKg").isEmpty())
        .andExpect(jsonPath("$.itemsNeedingReconfirmation").isArray());

    ArgumentCaptor<GenerateCommand> cap = ArgumentCaptor.forClass(GenerateCommand.class);
    verify(service).generate(eq(USER_ID), cap.capture());
    assertThat(cap.getValue().availableMinutes()).isEqualTo(60);
    assertThat(cap.getValue().desiredPartCodes()).containsExactly("CHEST", "ARM");
    assertThat(cap.getValue().availableEquipmentIds()).containsExactly(1L, 2L);
    assertThat(cap.getValue().excludeItemNames()).containsExactly("スクワット");
  }

  @Test
  void generate_時間が15分未満なら400で文言を返す() throws Exception {
    mvc.perform(
            post("/api/mobile/workout-generation/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"availableMinutes\":10}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("使える時間は15分以上で指定してください"));
    verifyNoInteractions(service);
  }

  @Test
  void generate_JSONが壊れていれば400() throws Exception {
    mvc.perform(
            post("/api/mobile/workout-generation/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"availableMinutes\":"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void generate_サービスの入力チェックの400はステータスどおり返す() throws Exception {
    when(service.generate(eq(USER_ID), any()))
        .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "部位の指定が正しくありません"));
    mvc.perform(
            post("/api/mobile/workout-generation/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"availableMinutes\":60,\"desiredPartCodes\":[\"NECK\"]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("部位の指定が正しくありません"));
  }

  @Test
  void addExclusion_本人の除外として登録し201() throws Exception {
    mvc.perform(
            post("/api/mobile/workout-generation/exclusions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemName\":\"ベンチプレス\",\"reason\":\"右肩に違和感\"}"))
        .andExpect(status().isCreated());
    verify(service).addExclusion(USER_ID, "ベンチプレス", "右肩に違和感");
  }

  @Test
  void confirmExclusion_keepの値をそのまま渡す_他人の除外は404() throws Exception {
    mvc.perform(put("/api/mobile/workout-generation/exclusions/ベンチプレス/confirm?keep=true"))
        .andExpect(status().isNoContent());
    verify(service).confirmExclusion(USER_ID, "ベンチプレス", true);

    org.mockito.Mockito.doThrow(
            new ResponseStatusException(HttpStatus.NOT_FOUND, "除外している種目が見つかりません"))
        .when(service)
        .confirmExclusion(USER_ID, "スクワット", false);
    mvc.perform(put("/api/mobile/workout-generation/exclusions/スクワット/confirm?keep=false"))
        .andExpect(status().isNotFound());
  }

  @Test
  void confirmExclusion_keepが無ければ400() throws Exception {
    mvc.perform(put("/api/mobile/workout-generation/exclusions/ベンチプレス/confirm"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void removeExclusion_本人の除外を削除し204() throws Exception {
    mvc.perform(delete("/api/mobile/workout-generation/exclusions/ベンチプレス"))
        .andExpect(status().isNoContent());
    verify(service).removeExclusion(USER_ID, "ベンチプレス");
  }

  @Test
  void リクエストの検証() {
    GenerateRequest g = new GenerateRequest();
    assertThat(validator.validate(g)).hasSize(1);
    g.setAvailableMinutes(15);
    assertThat(validator.validate(g)).isEmpty();
    g.setAvailableMinutes(241);
    assertThat(validator.validate(g)).hasSize(1);

    AddExclusionRequest a = new AddExclusionRequest();
    a.setItemName(" ");
    assertThat(validator.validate(a)).hasSize(1);
    a.setItemName("ベンチプレス");
    a.setReason("あ".repeat(201));
    assertThat(validator.validate(a)).hasSize(1);
  }
}
