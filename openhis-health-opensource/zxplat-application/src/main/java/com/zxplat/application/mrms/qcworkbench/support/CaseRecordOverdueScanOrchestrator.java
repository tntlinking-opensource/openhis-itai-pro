/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.qcworkbench.support;

import com.zxplat.domain.mrms.caserecord.entity.CaseRecord;
import com.zxplat.domain.mrms.caserecord.service.CaseRecordOverdueQueryService;
import com.zxplat.domain.mrms.qc.executor.QcRuleExecutor;
import com.zxplat.domain.mrms.qc.service.OverdueNotifyDomainService;
import com.zxplat.domain.share.constants.MrmsConstants;
import com.zxplat.domain.share.constants.MrmsOverdueNotifyTypes;
import com.zxplat.domain.share.enums.QcTrigger;
import com.zxplat.domain.infrastructure.integration.notification.StaffNotificationExtension;
import com.zxplat.domain.infrastructure.integration.notification.ExtensionIn.StaffNotificationExtensionIn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

/**
 * 病案提交/归档逾期扫描编排。
 *
 * <p>由 {@link com.zxplat.application.mrms.qcworkbench.service.MrmsCaseRecordQcAppService}
 * 的 Quartz 定时任务入口调用；扫描超期未提交首页与超期未归档病案并发站内通知，
 * 归档逾期另触发 SCHEDULED 质控。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CaseRecordOverdueScanOrchestrator {

    @Value("${mrms.overdue.submit-days:7}")
    private int submitDays;

    @Value("${mrms.overdue.archive-days:30}")
    private int archiveDays;

    private final CaseRecordOverdueQueryService caseRecordOverdueQueryService;
    private final OverdueNotifyDomainService overdueNotifyDomainService;
    private final StaffNotificationExtension staffNotificationExtension;
    private final QcRuleExecutor qcRuleExecutor;

    /**
     * 执行提交逾期与归档逾期两轮扫描。
     */
    @Transactional(rollbackFor = Exception.class)
    public void scanAll() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        scanSubmitOverdue(today, now);
        scanArchiveOverdue(today, now);
    }

    /**
     * 扫描出院后超期未提交首页的病案，每日每案最多通知一次。
     */
    private void scanSubmitOverdue(LocalDate today, LocalDateTime now) {
        LocalDateTime cutoff = now.minusDays(submitDays);
        List<CaseRecord> records = caseRecordOverdueQueryService.findSubmitOverdue(cutoff);
        for (CaseRecord record : records) {
            // 同日已通知则跳过，避免重复打扰
            if (overdueNotifyDomainService.alreadyNotified(
                    record.getId(), MrmsOverdueNotifyTypes.SUBMIT_OVERDUE, today)) {
                continue;
            }
            notifySubmitOverdue(record);
            overdueNotifyDomainService.recordNotified(
                    record.getId(),
                    record.getTenantId(),
                    MrmsOverdueNotifyTypes.SUBMIT_OVERDUE,
                    today);
        }
        log.debug("MRMS 提交逾期扫描完成 candidates={}", records.size());
    }

    /**
     * 扫描出院后超期未归档病案：先跑定时质控，再发通知。
     */
    private void scanArchiveOverdue(LocalDate today, LocalDateTime now) {
        LocalDateTime cutoff = now.minusDays(archiveDays);
        List<CaseRecord> records = caseRecordOverdueQueryService.findArchiveOverdue(cutoff);
        for (CaseRecord record : records) {
            // 归档逾期先补跑 SCHEDULED 质控，刷新缺陷清单
            qcRuleExecutor.execute(record.getId(), QcTrigger.SCHEDULED, MrmsConstants.SYSTEM_OPERATOR_ID);
            // 同日已通知则跳过归档逾期提醒
            if (overdueNotifyDomainService.alreadyNotified(
                    record.getId(), MrmsOverdueNotifyTypes.ARCHIVE_OVERDUE, today)) {
                continue;
            }
            notifyArchiveOverdue(record);
            overdueNotifyDomainService.recordNotified(
                    record.getId(),
                    record.getTenantId(),
                    MrmsOverdueNotifyTypes.ARCHIVE_OVERDUE,
                    today);
        }
        log.debug("MRMS 归档逾期扫描完成 candidates={}", records.size());
    }

    /**
     * 发送首页提交逾期站内通知（收件人为住院医师）。
     *
     * @param record 病案主档
     */
    private void notifySubmitOverdue(CaseRecord record) {
        StaffNotificationExtensionIn command = baseCommand(record, MrmsOverdueNotifyTypes.SUBMIT_OVERDUE);
        command.setTitle("病案首页提交逾期");
        command.setMessage(buildSubmitMessage(record));
        staffNotificationExtension.notify(command);
    }

    /**
     * 发送归档逾期站内通知（收件人为住院医师）。
     *
     * @param record 病案主档
     */
    private void notifyArchiveOverdue(CaseRecord record) {
        StaffNotificationExtensionIn command = baseCommand(record, MrmsOverdueNotifyTypes.ARCHIVE_OVERDUE);
        command.setTitle("病案归档逾期");
        command.setMessage(buildArchiveMessage(record));
        staffNotificationExtension.notify(command);
    }

    /**
     * 组装站内通知命令公共字段（收件人为住院医师）。
     *
     * @param record 病案主档
     * @param notifyTypeCode 通知类型 code
     * @return 待填充标题/正文的命令
     */
    private StaffNotificationExtensionIn baseCommand(CaseRecord record, String notifyTypeCode) {
        StaffNotificationExtensionIn command = new StaffNotificationExtensionIn();
        command.setCaseRecordId(record.getId());
        command.setTenantId(record.getTenantId());
        command.setRecipientPractitionerId(record.getAtddrPractitionerId());
        command.setNotifyTypeCode(notifyTypeCode);
        return command;
    }

    /**
     * 组装提交逾期通知正文。
     *
     * @param record 病案主档
     * @return 通知正文
     */
    private String buildSubmitMessage(CaseRecord record) {
        String name = trimToNull(record.getPsnName());
        // 姓名为空时用占位，保证通知可读
        if (name == null) {
            name = "患者";
        }
        return "住院号 " + nullToDash(record.getIptNo())
                + "（" + name + "）出院已超过 "
                + submitDays
                + " 天，病案首页尚未提交，请尽快处理。";
    }

    /**
     * 组装归档逾期通知正文。
     *
     * @param record 病案主档
     * @return 通知正文
     */
    private String buildArchiveMessage(CaseRecord record) {
        String name = trimToNull(record.getPsnName());
        // 姓名为空时用占位，保证通知可读
        if (name == null) {
            name = "患者";
        }
        return "住院号 " + nullToDash(record.getIptNo())
                + "（" + name + "）出院已超过 "
                + archiveDays
                + " 天，病案尚未归档，请关注。";
    }

    /**
     * 空/null 展示回落为 "-"，否则返回 trim 后原值。
     *
     * @param value 原始字符串
     * @return 展示文案
     */
    private String nullToDash(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return "-";
        }
        return trimmed;
    }
}
