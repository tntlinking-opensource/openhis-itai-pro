/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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
