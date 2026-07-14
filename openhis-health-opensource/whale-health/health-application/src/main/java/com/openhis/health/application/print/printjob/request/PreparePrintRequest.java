/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.print.printjob.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 打印准备请求（IF-BAS-PRT-31）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "打印准备请求")
public class PreparePrintRequest {

  /** 模板 busNo（与 scenarioCode 二选一）。 */
  @Schema(description = "模板 busNo（与 scenarioCode 二选一）", example = "PT20260101001")
  private String busNo;

  /** 业务场景码（与 busNo 二选一）。 */
  @Schema(description = "业务场景码（与 busNo 二选一）", example = "outpatient.rx.print")
  private String scenarioCode;

  /** 兼容旧客户端的场景码。 */
  @Schema(description = "兼容旧客户端的场景码", example = "outpatient.rx.print")
  private String bindingCode;
}
