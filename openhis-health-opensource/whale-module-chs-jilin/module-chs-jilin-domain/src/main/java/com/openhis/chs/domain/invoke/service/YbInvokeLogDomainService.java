/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.invoke.service;

import com.openhis.chs.domain.invoke.command.AppendYbInvokeLogCommand;
import com.openhis.chs.domain.invoke.command.LinkYbInvokeLogSettlementCommand;
import com.openhis.chs.domain.invoke.entity.YbInvokeLog;
import com.openhis.chs.domain.invoke.repository.YbInvokeLogRepository;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** {@code yb_invoke_log} 写服务 */
@Service
@RequiredArgsConstructor
public class YbInvokeLogDomainService {

    private static final int ERR_MSG_MAX = 512;

    private final YbInvokeLogRepository invokeLogRepository;

    /**
     * 记录一次医保平台调用。
     *
     * @param command 审计字段；{@code null} 时跳过
     * @return 新行主键；跳过时 {@code null}
     *
     * <p>使用 {@code REQUIRES_NEW}：业务 UoW 因 {@code infcode!=0} 回滚时，失败审计仍保留。<。</p> */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long append(AppendYbInvokeLogCommand command) {
        if (command == null) {
            return null;
        }
        YbInvokeLog row = new YbInvokeLog();
        row.setContractId(command.getContractId());
        row.setContractBusNo(command.getContractBusNo());
        row.setInfno(command.getInfno());
        row.setInfname(trimToNull(command.getInfname()));
        row.setMsgid(command.getMsgid());
        row.setEncounterId(command.getEncounterId());
        row.setPaymentDraftId(command.getPaymentDraftId());
        row.setChrgBchno(command.getChrgBchno());
        row.setPreSettleId(command.getPreSettleId());
        row.setSettlementRecordId(command.getSettlementRecordId());
        row.setInfcode(command.getInfcode());
        row.setErrMsg(trimErrMsg(command.getErrMsg()));
        row.setDurationMs(command.getDurationMs());
        row.setRequestJson(command.getRequestJson());
        row.setResponseJson(command.getResponseJson());
        Long tenantId = command.getTenantId();
        if (tenantId == null) {
            tenantId = TenantUtils.getCurrentTenantId();
        }
        row.setTenantId(tenantId);
        invokeLogRepository.save(row);
        return row.getId();
    }

    /**
     * 将 invoke 日志关联到正式结算回执。
     *
     * @param command 含 invokeLogId 与 settlementRecordId；缺任一 ID 时跳过 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void linkSettlementRecord(LinkYbInvokeLogSettlementCommand command) {
        if (command == null || command.getInvokeLogId() == null || command.getSettlementRecordId() == null) {
            return;
        }
        invokeLogRepository.findById(command.getInvokeLogId()).ifPresent(row -> {
            row.setSettlementRecordId(command.getSettlementRecordId());
            invokeLogRepository.save(row);
        });
    }

    /**
     * 裁剪空白字符串，空串转为 {@code null}。
     *
     * @param value 原始字符串
     * @return 裁剪后非空值；空时 {@code null}
     */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }

    /**
     * 裁剪并截断错误消息至最大长度。
     *
     * @param errMsg 原始错误消息
     * @return 裁剪后消息；超长时截断至 {@link #ERR_MSG_MAX}
     */
    private static String trimErrMsg(String errMsg) {
        if (errMsg == null) {
            return null;
        }
        String trimmed = errMsg.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() <= ERR_MSG_MAX) {
            return trimmed;
        }
        return trimmed.substring(0, ERR_MSG_MAX);
    }
}
