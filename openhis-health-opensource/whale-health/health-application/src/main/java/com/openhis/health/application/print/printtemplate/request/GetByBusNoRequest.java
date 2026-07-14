/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.print.printtemplate.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 按 busNo 加载/确保草稿请求（IF-BAS-PRT-13）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "按 busNo 加载画布打印模板请求")
public class GetByBusNoRequest {

  /** 模板业务键。 */
  @Schema(description = "模板业务键", requiredMode = Schema.RequiredMode.REQUIRED,
      example = "PT20260101001")
  private String busNo;
}
