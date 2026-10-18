/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.print.printtemplate.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 发布模板请求（IF-BAS-PRT-15）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "发布画布打印模板请求")
public class PublishTemplateRequest {

  /** 模板 busNo。 */
  @Schema(description = "模板 busNo", requiredMode = Schema.RequiredMode.REQUIRED,
      example = "PT20260101001")
  private String busNo;
}
