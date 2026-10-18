/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.print.printjob.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 打印确认请求（IF-BAS-PRT-32）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "打印确认落库请求")
public class ConfirmPrintRequest {

  /** prepare 返回的 traceId。 */
  @Schema(description = "prepare 返回的 traceId", requiredMode = Schema.RequiredMode.REQUIRED,
      example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
  private String traceId;

  /** 模板 busNo。 */
  @Schema(description = "模板 busNo", requiredMode = Schema.RequiredMode.REQUIRED,
      example = "PT20260101001")
  private String busNo;

  /** 业务场景码。 */
  @Schema(description = "业务场景码", example = "outpatient.rx.print")
  private String scenarioCode;

  /** 模板版本号。 */
  @Schema(description = "模板版本号", example = "2")
  private Integer templateVersion;

  /** 兼容旧客户端的场景码。 */
  @Schema(description = "兼容旧客户端的场景码", example = "outpatient.rx.print")
  private String bindingCode;

  /** 打印结果：SUCCESS / FAILED。 */
  @Schema(description = "打印结果：SUCCESS / FAILED", requiredMode = Schema.RequiredMode.REQUIRED,
      example = "SUCCESS")
  private String status;

  /** 打印机名称。 */
  @Schema(description = "打印机名称", example = "HP LaserJet")
  private String printer;

  /** 打印份数。 */
  @Schema(description = "打印份数", example = "1")
  private Integer copies;

  /** 实际打印数据 JSON。 */
  @Schema(description = "实际打印数据 JSON")
  private Object printData;

  /** 业务关联 JSON。 */
  @Schema(description = "业务关联 JSON")
  private Object bizRef;

  /** 客户端类型。 */
  @Schema(description = "客户端类型", example = "zxplat-win")
  private String clientType;

  /** 失败错误码。 */
  @Schema(description = "失败错误码", example = "PRT_PRINT_FAILED")
  private String errorCode;

  /** 失败错误信息。 */
  @Schema(description = "失败错误信息")
  private String errorMessage;
}
