/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.notice.support;

import com.openhis.chs.domain.notice.command.RunComplianceCommand;
import com.openhis.chs.domain.notice.model.RunComplianceResult;
import com.openhis.chs.domain.notice.port.CatalogNoticePostImportHookPort;
import com.openhis.chs.domain.notice.service.CatalogNoticeComplianceDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 合规校验编排：CAT-002 入库成功回调 + 手动 rerun。
 * <p>报告失败不回滚目录下载（BR-07）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoticeComplianceWorkflow implements CatalogNoticePostImportHookPort {

    private final CatalogNoticeComplianceDomainService complianceDomainService;

    /**
     * CAT-002 入库成功回调；异常仅记日志。
     */
    @Override
    public void onCatalogImportSuccess(Long tenantId, Long contractId, String listType, Long operatorId) {
        try {
            RunComplianceCommand command = new RunComplianceCommand();
            command.setTenantId(tenantId);
            command.setContractId(contractId);
            command.setListType(listType);
            command.setOperatorId(operatorId);
            RunComplianceResult result = complianceDomainService.runCompliance(command);
            log.info("[notice-compliance][after-import] reportId={} issues={} listType={}",
                result.getReportId(), result.getIssueCount(), listType);
        } catch (Exception ex) {
            log.warn("[notice-compliance][after-import-failed] contractId={} listType={} err={}",
                contractId, listType, ex.getMessage(), ex);
        }
    }

    /**
     * 手动重验（W-05）；失败向上抛出 NOTICE_COMPLIANCE_FAILED。
     */
    public RunComplianceResult rerunNoticeCompliance(RunComplianceCommand command) {
        return complianceDomainService.runCompliance(command);
    }
}
