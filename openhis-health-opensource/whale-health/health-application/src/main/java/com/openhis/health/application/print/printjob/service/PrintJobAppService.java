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

package com.openhis.health.application.print.printjob.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.application.print.printjob.dto.ConfirmPrintResultDto;
import com.openhis.health.application.print.printjob.dto.PreparePrintResultDto;
import com.openhis.health.application.print.printjob.mapper.PrintJobMapper;
import com.openhis.health.application.print.printjob.request.ConfirmPrintRequest;
import com.openhis.health.application.print.printjob.request.PreparePrintRequest;
import com.openhis.health.application.print.printtemplate.support.PrintJsonSupport;
import com.openhis.health.module.common.validator.PrintOperatorResolver;
import com.openhis.health.domain.print.printlog.command.AppendPrintLogCommand;
import com.openhis.health.domain.share.enums.PrintClientType;
import com.openhis.health.domain.share.enums.PrintLogStatus;
import com.openhis.health.domain.print.printlog.service.PrtPrintEventLogDomainService;
import com.openhis.health.domain.print.printtemplate.entity.PrtTemplate;
import com.openhis.health.domain.print.printtemplate.service.PrtTemplateQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.exception.ValidationException;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 打印任务运行时应用服务（AS-BAS-PRT，IF-BAS-PRT-31～32）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>prepare 解析 published 模板并签发 traceId；</li>
 *   <li>confirm 追加审计日志（append-only）；</li>
 *   <li>出参/命令经 {@link PrintJobMapper} 装配。</li>
 * </ul>
 *
 * <p>由 AppServiceControllerRegistrar 暴露 REST，本类不含 Controller。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrintJobAppService {

  /** 模板读侧：按场景码或 busNo 解析 published。 */
  private final PrtTemplateQueryService prtTemplateQueryService;

  /** 打印日志写侧：append-only 追加审计行。 */
  private final PrtPrintEventLogDomainService prtPrintEventLogDomainService;

  /** 会话租户、机构与操作人解析。 */
  private final PrintOperatorResolver printOperatorResolver;

  /** PrintJson 序列化与 contentHash。 */
  private final PrintJsonSupport printJsonSupport;

  /** prepare/confirm 出参与 Command 装配。 */
  private final PrintJobMapper printJobMapper;

  /**
   * 打印准备（IF-BAS-PRT-31）：解析模板并返回 traceId 与版式载荷。
   *
   * @param request scenarioCode 与 busNo 二选一
   * @return traceId、busNo、PrintJson 等运行时载荷
   * @throws ValidationException 入参互斥或均缺失
   * @throws BusinessException 模板不存在
   */
  public PreparePrintResultDto preparePrint(PreparePrintRequest request) {
    // Gate：须已选租户；scenarioCode 与 busNo 不可同时传入。
    Long tenantId = printOperatorResolver.requireTenantId();
    String scenarioCode = resolveScenarioCode(request);
    String busNo = null;
    if (request != null) {
      busNo = trimToNull(request.getBusNo());
    }
    if (scenarioCode != null && busNo != null) {
      throw new ValidationException("scenarioCode 与 busNo 二选一");
    }
    // Pre：按场景码或 busNo 解析 published 模板（租户优先、平台 fallback）。
    PrtTemplate published;
    if (scenarioCode != null) {
      published = prtTemplateQueryService.findPublishedByScenarioCode(scenarioCode, tenantId);
      if (published == null) {
        throw new BusinessException(ErrorCodeConstants.PRT_NOT_FOUND, "未找到场景模板：" + scenarioCode);
      }
    } else if (busNo != null) {
      published = prtTemplateQueryService.findPublished(busNo, tenantId);
      if (published == null) {
        throw new BusinessException(ErrorCodeConstants.PRT_NOT_FOUND, "未找到模板：" + busNo);
      }
      scenarioCode = trimToNull(published.getScenarioCode());
    } else {
      throw new ValidationException("scenarioCode 与 busNo 须提供一个");
    }
    // Asm：签发 traceId 并经 Mapper 结构化 PrintJson 出参。
    String traceId = UUID.randomUUID().toString();
    return printJobMapper.ConverterPreparePrintResultDto(published, traceId, scenarioCode);
  }

  /**
   * 打印确认落库（IF-BAS-PRT-32）：追加一行审计日志。
   *
   * @param request 确认载荷（含 traceId、status、printData 等）
   * @return 成功标记
   * @throws ValidationException 必填字段缺失或 status 非法
   */
  public ConfirmPrintResultDto confirmPrint(ConfirmPrintRequest request) {
    if (request == null) {
      throw new ValidationException("请求不能为空");
    }
    // Gate：租户、traceId、status、busNo 必填；status 仅 SUCCESS/FAILED。
    Long tenantId = printOperatorResolver.requireTenantId();
    String traceId = trimToNull(request.getTraceId());
    if (traceId == null) {
      throw new ValidationException("traceId 不能为空");
    }
    String status = trimToNull(request.getStatus());
    if (status == null) {
      throw new ValidationException("status 不能为空");
    }
    status = status.toUpperCase(Locale.ROOT);
    if (!PrintLogStatus.isValidCode(status)) {
      throw new ValidationException("status 须为 SUCCESS 或 FAILED");
    }
    String busNo = trimToNull(request.getBusNo());
    if (busNo == null) {
      throw new ValidationException("busNo 不能为空");
    }
    // Pre：客户端类型兜底 + contentHash/bizRef 序列化。
    String clientType = trimToNull(request.getClientType());
    if (clientType == null) {
      clientType = PrintClientType.OPENHIS_WIN.getCode();
    }
    request.setStatus(status);
    String contentHash = printJsonSupport.sha256HexOrNull(request.getPrintData());
    String bizRefJson = printJsonSupport.toJsonString(request.getBizRef());
    String bindingCode = resolveConfirmScenarioCode(request);
    // Cmd：Mapper 装配 append 命令并写库。
    AppendPrintLogCommand cmd =
        printJobMapper.ConverterAppendPrintLogCommand(
            request,
            tenantId,
            printOperatorResolver.findOrganizationIdOrNull(),
            printOperatorResolver.findOperatorIdOrNull(),
            printOperatorResolver.findOperatorNameOrNull(),
            contentHash,
            bizRefJson,
            clientType,
            bindingCode);
    prtPrintEventLogDomainService.appendLog(cmd);
    return new ConfirmPrintResultDto();
  }

  private String resolveScenarioCode(PreparePrintRequest request) {
    if (request == null) {
      return null;
    }
    String scenarioCode = trimToNull(request.getScenarioCode());
    if (scenarioCode != null) {
      return scenarioCode;
    }
    return trimToNull(request.getBindingCode());
  }

  private String resolveConfirmScenarioCode(ConfirmPrintRequest request) {
    if (request == null) {
      return null;
    }
    String scenarioCode = trimToNull(request.getScenarioCode());
    if (scenarioCode != null) {
      return scenarioCode;
    }
    return trimToNull(request.getBindingCode());
  }
}
