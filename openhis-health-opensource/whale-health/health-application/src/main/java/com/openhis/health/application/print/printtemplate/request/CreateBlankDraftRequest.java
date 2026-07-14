/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.print.printtemplate.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 新建空白草稿请求（IF-BAS-PRT-12）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "新建空白画布打印模板草稿请求")
public class CreateBlankDraftRequest {

  /** 模板展示名称。 */
  @Schema(description = "模板展示名称", example = "门诊处方 A5")
  private String name;

  /** 业务场景码。 */
  @Schema(description = "业务场景码", example = "outpatient.rx.print")
  private String scenarioCode;
}
