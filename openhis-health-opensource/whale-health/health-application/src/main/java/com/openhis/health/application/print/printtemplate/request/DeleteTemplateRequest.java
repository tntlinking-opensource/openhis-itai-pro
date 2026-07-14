/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.print.printtemplate.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 删除租户模板请求（IF-BAS-PRT-17）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "删除画布打印模板请求")
public class DeleteTemplateRequest {

  /** 模板 busNo。 */
  @Schema(description = "模板 busNo", requiredMode = Schema.RequiredMode.REQUIRED,
      example = "PT20260101001")
  private String busNo;
}
