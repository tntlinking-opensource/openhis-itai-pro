/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.gateway;

import com.openhis.chs.api.ResultBody;
import com.openhis.chs.domain.gateway.entity.YbInvokeLog;
import com.openhis.chs.domain.gateway.repository.YbInvokeLogRepository;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 写入 {@code yb_invoke_log}（医保平台 request/response 全文）。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class YbInvokeLogWriter {

    private final YbInvokeLogRepository invokeLogRepository;

    /**
     * 记录一次医保平台调用。
     *
     * @return 日志行 id；跳过时返回 null
     */
    public Long write(YbInvokeLogCommand command) {
        if (command == null) {
            return null;
        }
        YbInvokeLog row = new YbInvokeLog();
        row.setContractId(command.getContractId());
        row.setContractBusNo(command.getContractBusNo());
        row.setInfno(command.getInfno());
        row.setMsgid(command.getMsgid());
        row.setEncounterId(command.getEncounterId());
        row.setPaymentDraftId(command.getPaymentDraftId());
        row.setChrgBchno(command.getChrgBchno());
        row.setPreSettleId(command.getPreSettleId());
        row.setSettlementRecordId(command.getSettlementRecordId());
        row.setInfcode(command.getInfcode());
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
     * 2207/2304 成功后回填结算回执关联。
     */
    public void linkSettlementRecord(Long invokeLogId, Long settlementRecordId) {
        if (invokeLogId == null || settlementRecordId == null) {
            return;
        }
        invokeLogRepository.findById(invokeLogId).ifPresent(row -> {
            row.setSettlementRecordId(settlementRecordId);
            invokeLogRepository.save(row);
        });
    }

    /**
     * 从 {@link ResultBody} 提取 infcode。
     *
     * @param body 平台响应
     * @return infcode 或 null
     */
    public static String extractInfcode(ResultBody body) {
        if (body == null) {
            return null;
        }
        return body.getInfcode();
    }
}
