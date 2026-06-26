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

package com.openhis.health.application.basic.printjob.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.application.basic.print.support.PrintJsonSupport;
import com.openhis.health.application.basic.print.support.PrintOperatorResolver;
import com.openhis.health.application.basic.printjob.dto.ConfirmPrintResultDto;
import com.openhis.health.application.basic.printjob.dto.PreparePrintResultDto;
import com.openhis.health.application.basic.printjob.request.ConfirmPrintRequest;
import com.openhis.health.application.basic.printjob.request.PreparePrintRequest;
import com.openhis.health.domain.custom.printlog.command.AppendPrintLogCommand;
import com.openhis.health.domain.custom.printlog.constants.PrintClientTypeDefaults;
import com.openhis.health.domain.custom.printlog.constants.PrintLogStatus;
import com.openhis.health.domain.custom.printlog.service.PrtPrintLogWriter;
import com.openhis.health.domain.custom.printtemplate.entity.PrtTemplate;
import com.openhis.health.domain.custom.printtemplate.service.PrtScenarioResolver;
import com.openhis.health.domain.custom.printtemplate.service.PrtTemplateManager;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.exception.ValidationException;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 打印任务运行时应用服务（IF-BAS-PRT-31～32）。
 */
@Service
@RequiredArgsConstructor
public class PrintJobAppService {

  private final PrtTemplateManager prtTemplateManager;
  private final PrtScenarioResolver prtScenarioResolver;
  private final PrtPrintLogWriter prtPrintLogWriter;
  private final PrintOperatorResolver printOperatorResolver;
  private final PrintJsonSupport printJsonSupport;

  /**
   * 打印准备（IF-BAS-PRT-31）。
   *
   * @param request scenarioCode 或 busNo（二选一）
   * @return traceId 与版式载荷
   */
  public PreparePrintResultDto preparePrint(PreparePrintRequest request) {
    Long tenantId = printOperatorResolver.requireTenantId();
    String scenarioCode = resolveScenarioCode(request);
    String busNo = trimToNull(request == null ? null : request.getBusNo());
    if (scenarioCode != null && busNo != null) {
      throw new ValidationException("scenarioCode 与 busNo 二选一");
    }
    PrtTemplate published;
    if (scenarioCode != null) {
      published = prtScenarioResolver.findPublishedByScenarioCode(scenarioCode, tenantId);
      if (published == null) {
        throw new BusinessException(ErrorCodeConstants.PRT_NOT_FOUND, "未找到场景模板：" + scenarioCode);
      }
    } else if (busNo != null) {
      published = prtTemplateManager.findPublished(busNo, tenantId);
      if (published == null) {
        throw new BusinessException(ErrorCodeConstants.PRT_NOT_FOUND, "未找到模板：" + busNo);
      }
      scenarioCode = trimToNull(published.getScenarioCode());
    } else {
      throw new ValidationException("scenarioCode 与 busNo 须提供一个");
    }
    PreparePrintResultDto dto = new PreparePrintResultDto();
    dto.setTraceId(UUID.randomUUID().toString());
    dto.setBusNo(published.getBusNo());
    dto.setScenarioCode(scenarioCode);
    dto.setTemplateVersion(published.getVersion());
    dto.setPrintJson(printJsonSupport.parseJsonOrNull(published.getPrintJson()));
    dto.setSamplePrintData(printJsonSupport.parseJsonOrNull(published.getSamplePrintData()));
    return dto;
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

  /**
   * 打印确认落库（IF-BAS-PRT-32）。
   *
   * @param request 确认载荷
   * @return 成功标记
   */
  public ConfirmPrintResultDto confirmPrint(ConfirmPrintRequest request) {
    if (request == null) {
      throw new ValidationException("请求不能为空");
    }
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
    if (!PrintLogStatus.SUCCESS.equals(status) && !PrintLogStatus.FAILED.equals(status)) {
      throw new ValidationException("status 须为 SUCCESS 或 FAILED");
    }
    String busNo = trimToNull(request.getBusNo());
    if (busNo == null) {
      throw new ValidationException("busNo 不能为空");
    }
  // Asm：组装 append 命令。
    AppendPrintLogCommand cmd = new AppendPrintLogCommand();
    cmd.setTenantId(tenantId);
    cmd.setOrganizationId(printOperatorResolver.findOrganizationIdOrNull());
    cmd.setTraceId(traceId);
    cmd.setBusNo(busNo);
    cmd.setBindingCode(resolveConfirmScenarioCode(request));
    cmd.setTemplateVersion(request.getTemplateVersion());
    cmd.setStatus(status);
    cmd.setPrinterName(trimToNull(request.getPrinter()));
    cmd.setCopies(request.getCopies());
    cmd.setContentHash(printJsonSupport.sha256HexOrNull(request.getPrintData()));
    cmd.setErrorCode(trimToNull(request.getErrorCode()));
    cmd.setErrorMessage(trimToNull(request.getErrorMessage()));
    cmd.setBizRefJson(printJsonSupport.toJsonString(request.getBizRef()));
    String clientType = trimToNull(request.getClientType());
    if (clientType == null) {
      clientType = PrintClientTypeDefaults.OPENHIS_WIN;
    }
    cmd.setClientType(clientType);
    cmd.setOperatorId(printOperatorResolver.findOperatorIdOrNull());
    cmd.setOperatorName(printOperatorResolver.findOperatorNameOrNull());
    prtPrintLogWriter.appendLog(cmd);
    return new ConfirmPrintResultDto();
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
