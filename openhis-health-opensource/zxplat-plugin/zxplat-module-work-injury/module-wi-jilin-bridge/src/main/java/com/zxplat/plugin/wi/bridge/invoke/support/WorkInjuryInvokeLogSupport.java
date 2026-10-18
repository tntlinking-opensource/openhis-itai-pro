/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.bridge.invoke.support;

import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;
import com.alibaba.fastjson2.JSON;
import com.zxplat.plugin.wi.domain.share.WorkInjuryConstants;
import com.zxplat.plugin.wi.gateway.scheme.shared.model.WiInfnoConstants;
import com.zxplat.plugin.chs.domain.invoke.entity.YbInvokeLog;
import com.zxplat.plugin.chs.domain.invoke.repository.YbInvokeLogRepository;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 工伤 invoke 审计写入 {@code yb_invoke_log}（scheme 隔离：contract_bus_no=H22010402403-GS）。
 */
@Component
@RequiredArgsConstructor
public class WorkInjuryInvokeLogSupport {

    private static final int ERR_MSG_MAX = 512;

    private final YbInvokeLogRepository invokeLogRepository;

    /**
     * 记录一次 Win/DLL 回写结果。
     *
     * @param contractId      契约 id
     * @param encounterId     就诊 id
     * @param paymentDraftId  草稿 id
     * @param infno           交易号
     * @param msgid           Win msgid
     * @param infcode         平台码
     * @param errMsg          错误信息
     * @param output          平台 output
     * @param success         是否成功
     * @param requestJson     Win 实际发送的 inputdata JSON
     * @return 日志行 id
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long write(
            Long contractId,
            Long encounterId,
            Long paymentDraftId,
            String infno,
            String msgid,
            String infcode,
            String errMsg,
            Map<String, Object> output,
            boolean success,
            String requestJson) {
        YbInvokeLog row = new YbInvokeLog();
        row.setContractId(contractId);
        row.setContractBusNo(WorkInjuryConstants.CONTRACT_BUS_NO);
        row.setInfno(infno);
        row.setInfname(WiInfnoConstants.resolveDisplayName(infno));
        row.setMsgid(msgid);
        row.setEncounterId(encounterId);
        row.setPaymentDraftId(paymentDraftId);
        if (success) {
            row.setInfcode(infcode);
        } else {
            row.setInfcode(infcode);
            row.setErrMsg(trimErrMsg(errMsg));
        }
        String requestJsonStored = "{}";
        if (isNotEmpty(requestJson)) {
            requestJsonStored = requestJson.trim();
        }
        row.setRequestJson(requestJsonStored);
        if (output != null) {
            row.setResponseJson(JSON.toJSONString(output));
        }
        row.setTenantId(TenantUtils.getCurrentTenantId());
        invokeLogRepository.save(row);
        return row.getId();
    }

    /**
     * 2207 落账后关联 invoke 日志与结算回执。
     *
     * @param invokeLogId         yb_invoke_log.id
     * @param settlementRecordId  yb_settlement_record.id
     * @param preSettleId         yb_wi_pre_settle.id
     * @param chrgBchno           收费批次
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void linkSettlementRecord(
            Long invokeLogId,
            Long settlementRecordId,
            Long preSettleId,
            String chrgBchno) {
        if (invokeLogId == null || settlementRecordId == null) {
            return;
        }
        invokeLogRepository.findById(invokeLogId).ifPresent(row -> {
            row.setSettlementRecordId(settlementRecordId);
            if (preSettleId != null) {
                row.setPreSettleId(preSettleId);
            }
            if (isNotEmpty(chrgBchno)) {
                row.setChrgBchno(chrgBchno.trim());
            }
            invokeLogRepository.save(row);
        });
    }

    /**
     * 就诊是否已有平台 2207 成功审计（单边判定：平台已结、本地可能未落）。
     *
     * @param encounterId 就诊 ID
     * @return true 表示存在 infcode=0 的 2207 日志
     */
    public boolean hasSuccessful2207(Long encounterId) {
        if (encounterId == null) {
            return false;
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        return !invokeLogRepository.findAll((root, query, cb) -> cb.and(
                cb.equal(root.get("encounterId"), encounterId),
                cb.equal(root.get("infno"), WiInfnoConstants.OUTPATIENT_SETTLE),
                cb.equal(root.get("tenantId"), tenantId),
                cb.equal(root.get("isDeleted"), Boolean.FALSE),
                cb.equal(root.get("infcode"), "0"))).isEmpty();
    }

    /** 截断 err_msg 至 {@link #ERR_MSG_MAX} 字符（yb_invoke_log 列宽）。 */
    private String trimErrMsg(String errMsg) {
        if (errMsg == null) {
            return null;
        }
        if (errMsg.length() <= ERR_MSG_MAX) {
            return errMsg;
        }
        return errMsg.substring(0, ERR_MSG_MAX);
    }
}
