/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.print.printjob.mapper;

import com.openhis.health.application.print.printjob.dto.PreparePrintResultDto;
import com.openhis.health.application.print.printjob.request.ConfirmPrintRequest;
import com.openhis.health.domain.print.printlog.command.AppendPrintLogCommand;
import com.openhis.health.domain.print.printtemplate.entity.PrtTemplate;

/**
 * 打印任务运行时应用层装配：Request/实体 → Command/DTO。
 */
public interface PrintJobMapper {

  /**
   * 打印确认请求转追加日志命令（调用方已完成必填校验与 status 归一化）。
   *
   * @param request 确认请求
   * @param tenantId 当前租户
   * @param organizationId 当前机构；可 null
   * @param operatorId 操作人 id；可 null
   * @param operatorName 操作人姓名；可 null
   * @param contentHash printData SHA256；可 null
   * @param bizRefJson 业务引用 JSON 字符串；可 null
   * @param clientType 客户端类型（已兜底）
   * @param bindingCode 场景码/bindingCode 解析结果
   * @return 追加命令
   */
  AppendPrintLogCommand ConverterAppendPrintLogCommand(
      ConfirmPrintRequest request,
      Long tenantId,
      Long organizationId,
      Long operatorId,
      String operatorName,
      String contentHash,
      String bizRefJson,
      String clientType,
      String bindingCode);

  /**
   * published 模板 + traceId 转 prepare 出参。
   *
   * @param published 已解析的 published 模板
   * @param traceId 运行时 traceId
   * @param scenarioCode 场景码
   * @return prepare 出参
   */
  PreparePrintResultDto ConverterPreparePrintResultDto(
      PrtTemplate published, String traceId, String scenarioCode);
}
