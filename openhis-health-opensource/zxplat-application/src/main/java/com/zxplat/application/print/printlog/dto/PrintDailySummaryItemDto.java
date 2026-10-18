/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.print.printlog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 打印审计日汇总行 DTO（IF-BAS-PRT-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "打印审计日汇总行")
public class PrintDailySummaryItemDto {

  /** 汇总日期。 */
  @Schema(description = "汇总日期", example = "2026-01-15")
  private LocalDate day;

  /** 模板 busNo。 */
  @Schema(description = "模板 busNo", example = "PT20260101001")
  private String busNo;

  /** 成功次数。 */
  @Schema(description = "成功次数", example = "42")
  private Integer successCount;

  /** 失败次数。 */
  @Schema(description = "失败次数", example = "3")
  private Integer failedCount;
}
