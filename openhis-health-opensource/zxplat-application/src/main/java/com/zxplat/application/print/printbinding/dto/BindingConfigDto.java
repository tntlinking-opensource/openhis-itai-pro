/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.print.printbinding.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 绑定配置加载响应（IF-BAS-PRT-21）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "业务场景模板绑定配置")
public class BindingConfigDto {

  /** 场景绑定行列表。 */
  @Schema(description = "场景绑定行列表")
  private List<BindingItemDto> bindings = new ArrayList<>();

  /** 可选模板候选列表。 */
  @Schema(description = "可选模板候选列表")
  private List<TemplateCandidateDto> candidates = new ArrayList<>();
}
