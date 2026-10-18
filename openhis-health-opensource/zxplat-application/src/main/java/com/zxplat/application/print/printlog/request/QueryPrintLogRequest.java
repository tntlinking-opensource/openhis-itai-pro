/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.print.printlog.request;

import com.zxplat.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 打印日志明细分页查询请求（IF-BAS-PRT-01）。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Schema(description = "打印日志明细分页查询请求")
public class QueryPrintLogRequest extends AbstractHealthPageRequest {

  /** 模板 busNo 精确筛选。 */
  @Schema(description = "模板 busNo 精确筛选", example = "PT20260101001")
  private String busNo;

  /** 打印结果：SUCCESS / FAILED。 */
  @Schema(description = "打印结果：SUCCESS / FAILED", example = "SUCCESS")
  private String status;

  /** 创建日起（含）。 */
  @Schema(description = "创建日起（含）", example = "2026-01-01")
  private LocalDate dateFrom;

  /** 创建日止（含）。 */
  @Schema(description = "创建日止（含）", example = "2026-01-31")
  private LocalDate dateTo;
}
