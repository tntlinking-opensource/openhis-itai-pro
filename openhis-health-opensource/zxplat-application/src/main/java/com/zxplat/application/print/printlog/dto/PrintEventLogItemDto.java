/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.print.printlog.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 打印审计日志明细行 DTO（IF-BAS-PRT-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "打印审计日志明细行")
public class PrintEventLogItemDto {

  /** 日志主键。 */
  @Schema(description = "日志主键", example = "1234567890123456789")
  @JsonSerialize(using = ToStringSerializer.class)
  private Long id;

  /** 运行时 traceId。 */
  @Schema(description = "运行时 traceId", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
  private String traceId;

  /** 模板 busNo。 */
  @Schema(description = "模板 busNo", example = "PT20260101001")
  private String busNo;

  /** 场景入口码。 */
  @Schema(description = "场景入口码", example = "outpatient.rx.print")
  private String bindingCode;

  /** 模板版本号。 */
  @Schema(description = "模板版本号", example = "2")
  private Integer templateVersion;

  /** 打印结果：SUCCESS / FAILED。 */
  @Schema(description = "打印结果：SUCCESS / FAILED", example = "SUCCESS")
  private String status;

  /** 打印机名称。 */
  @Schema(description = "打印机名称", example = "HP LaserJet")
  private String printerName;

  /** 打印份数。 */
  @Schema(description = "打印份数", example = "1")
  private Integer copies;

  /** 操作人主键。 */
  @Schema(description = "操作人主键", example = "1234567890123456789")
  @JsonSerialize(using = ToStringSerializer.class)
  private Long operatorId;

  /** 操作人姓名。 */
  @Schema(description = "操作人姓名", example = "张医生")
  private String operatorName;

  /** 失败错误码。 */
  @Schema(description = "失败错误码", example = "PRT_PRINT_FAILED")
  private String errorCode;

  /** 失败错误信息。 */
  @Schema(description = "失败错误信息")
  private String errorMessage;

  /** 创建时间。 */
  @Schema(description = "创建时间", example = "2026-01-15T10:30:00")
  private LocalDateTime createdAt;
}
