/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.print.printjob.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 打印确认结果 DTO（IF-BAS-PRT-32）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "打印确认结果")
public class ConfirmPrintResultDto {

  /** 是否受理。 */
  @Schema(description = "是否受理", example = "true")
  private Boolean success = Boolean.TRUE;
}
