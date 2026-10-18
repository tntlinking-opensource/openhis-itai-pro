/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.print.printlog.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 打印日志日汇总查询请求（IF-BAS-PRT-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "打印日志日汇总查询请求")
public class QueryPrintDailySummaryRequest {

  /** 汇总起始日（含）。 */
  @Schema(description = "汇总起始日（含）", example = "2026-01-01")
  private LocalDate dateFrom;

  /** 汇总结束日（含）。 */
  @Schema(description = "汇总结束日（含）", example = "2026-01-31")
  private LocalDate dateTo;

  /** 模板 busNo 精确筛选。 */
  @Schema(description = "模板 busNo 精确筛选", example = "PT20260101001")
  private String busNo;
}
