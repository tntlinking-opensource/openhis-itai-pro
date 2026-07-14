/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.print.printlog.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.domain.print.printlog.command.AppendPrintLogCommand;
import com.openhis.health.domain.print.printlog.constants.PrintClientTypeDefaults;
import com.openhis.health.domain.print.printlog.constants.PrintLogStatus;
import com.openhis.health.domain.print.printlog.entity.PrtPrintEventLog;
import com.openhis.health.domain.print.printlog.repository.PrtPrintEventLogRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 打印审计日志写侧（append-only）。
 */
@Service
@RequiredArgsConstructor
public class PrtPrintLogWriter {

  private final PrtPrintEventLogRepository prtPrintEventLogRepository;

  /**
   * 追加一行打印审计日志（TC-PRT-LOG-APPEND-001）。
   *
   * @param cmd 日志命令（字段由 App Gate 预校验）
   * @return 持久化后的日志行
   */
  public PrtPrintEventLog appendLog(AppendPrintLogCommand cmd) {
    if (cmd.getTenantId() == null) {
      throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "租户不能为空");
    }
    String traceId = trimToNull(cmd.getTraceId());
    if (traceId == null) {
      throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "traceId 不能为空");
    }
    String busNo = trimToNull(cmd.getBusNo());
    if (busNo == null) {
      throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "busNo 不能为空");
    }
    String status = trimToNull(cmd.getStatus());
    if (status == null) {
      throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "status 不能为空");
    }
    if (!PrintLogStatus.SUCCESS.equals(status) && !PrintLogStatus.FAILED.equals(status)) {
      throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "status 须为 SUCCESS 或 FAILED");
    }

  // Cmd：组装实体并 insert。
    PrtPrintEventLog log = new PrtPrintEventLog();
    log.setTenantId(cmd.getTenantId());
    log.setOrganizationId(cmd.getOrganizationId());
    log.setTraceId(traceId);
    log.setBusNo(busNo);
    log.setBindingCode(trimToNull(cmd.getBindingCode()));
    log.setTemplateVersion(cmd.getTemplateVersion());
    log.setStatus(status);
    log.setPrinterName(trimToNull(cmd.getPrinterName()));
    log.setCopies(cmd.getCopies());
    log.setContentHash(trimToNull(cmd.getContentHash()));
    log.setErrorCode(trimToNull(cmd.getErrorCode()));
    log.setErrorMessage(trimToNull(cmd.getErrorMessage()));
    log.setBizRefJson(trimToNull(cmd.getBizRefJson()));
    String clientType = trimToNull(cmd.getClientType());
    if (clientType == null) {
      clientType = PrintClientTypeDefaults.OPENHIS_WIN;
    }
    log.setClientType(clientType);
    log.setOperatorId(cmd.getOperatorId());
    log.setOperatorName(trimToNull(cmd.getOperatorName()));
    return prtPrintEventLogRepository.save(log);
  }
}
