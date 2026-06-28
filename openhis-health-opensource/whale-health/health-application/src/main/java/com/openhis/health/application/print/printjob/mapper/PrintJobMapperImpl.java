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

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.application.print.printjob.dto.PreparePrintResultDto;
import com.openhis.health.application.print.printjob.request.ConfirmPrintRequest;
import com.openhis.health.application.print.printtemplate.support.PrintJsonSupport;
import com.openhis.health.domain.print.printlog.command.AppendPrintLogCommand;
import com.openhis.health.domain.print.printtemplate.entity.PrtTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link PrintJobMapper} 实现：字段直拷 + PrintJson 结构化解析。
 */
@Component
@RequiredArgsConstructor
public class PrintJobMapperImpl implements PrintJobMapper {

  /** PrintJson 序列化/反序列化。 */
  private final PrintJsonSupport printJsonSupport;

  /**
   * 打印确认请求转追加日志命令。
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
  @Override
  public AppendPrintLogCommand ConverterAppendPrintLogCommand(
      ConfirmPrintRequest request,
      Long tenantId,
      Long organizationId,
      Long operatorId,
      String operatorName,
      String contentHash,
      String bizRefJson,
      String clientType,
      String bindingCode) {
    AppendPrintLogCommand cmd = new AppendPrintLogCommand();
    cmd.setTenantId(tenantId);
    cmd.setOrganizationId(organizationId);
    cmd.setTraceId(trimToNull(request.getTraceId()));
    cmd.setBusNo(trimToNull(request.getBusNo()));
    cmd.setBindingCode(bindingCode);
    cmd.setTemplateVersion(request.getTemplateVersion());
    cmd.setStatus(trimToNull(request.getStatus()));
    cmd.setPrinterName(trimToNull(request.getPrinter()));
    cmd.setCopies(request.getCopies());
    cmd.setContentHash(contentHash);
    cmd.setErrorCode(trimToNull(request.getErrorCode()));
    cmd.setErrorMessage(trimToNull(request.getErrorMessage()));
    cmd.setBizRefJson(bizRefJson);
    cmd.setClientType(clientType);
    cmd.setOperatorId(operatorId);
    cmd.setOperatorName(operatorName);
    return cmd;
  }

  /**
   * published 模板 + traceId 转 prepare 出参。
   *
   * @param published 已解析的 published 模板
   * @param traceId 运行时 traceId
   * @param scenarioCode 场景码
   * @return prepare 出参
   */
  @Override
  public PreparePrintResultDto ConverterPreparePrintResultDto(
      PrtTemplate published, String traceId, String scenarioCode) {
    PreparePrintResultDto dto = new PreparePrintResultDto();
    dto.setTraceId(traceId);
    dto.setBusNo(published.getBusNo());
    dto.setScenarioCode(scenarioCode);
    dto.setTemplateVersion(published.getVersion());
    dto.setPrintJson(printJsonSupport.parseJsonOrNull(published.getPrintJson()));
    dto.setSamplePrintData(printJsonSupport.parseJsonOrNull(published.getSamplePrintData()));
    return dto;
  }
}
