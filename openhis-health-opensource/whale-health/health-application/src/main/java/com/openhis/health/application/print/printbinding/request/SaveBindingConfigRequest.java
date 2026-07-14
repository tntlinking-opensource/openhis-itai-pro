/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.print.printbinding.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 保存绑定配置请求（IF-BAS-PRT-22）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "保存业务场景模板绑定配置请求")
public class SaveBindingConfigRequest {

  /** 全量 bindingCode→busNo 映射。 */
  @Schema(description = "全量 bindingCode→busNo 映射", requiredMode = Schema.RequiredMode.REQUIRED)
  private Map<String, String> bindings;
}
