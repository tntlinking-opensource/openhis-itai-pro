/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.gateway;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.api.ChsInfNo;
import com.openhis.chs.api.ChsPlatformCodes;
import com.openhis.chs.api.ResultBody;
import com.openhis.chs.domain.gateway.entity.YbInvokeLog;
import com.openhis.chs.domain.gateway.repository.YbInvokeLogRepository;
import com.openhis.chs.domain.support.ChsTextSupport;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 写入 {@code yb_invoke_log}（医保平台 request/response 全文审计）。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class YbInvokeLogWriter {

    private static final int ERR_MSG_MAX = 512;

    private final YbInvokeLogRepository invokeLogRepository;

    /**
     * 记录一次医保平台调用。
     *
     * <p>使用 {@code REQUIRES_NEW}：业务 AppService UoW 因 {@code infcode!=0} 回滚时，失败审计仍保留。</p>
     *
     * @param command 审计字段（由 {@link PersistingInsuranceGatewayClient} 组装）
     * @return 日志行 id；command 为 null 时返回 null
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long write(YbInvokeLogCommand command) {
        if (command == null) {
            return null;
        }
        // Cmd：装配实体并落库（infname/err_msg 便于 SQL 筛选联调问题）
        YbInvokeLog row = new YbInvokeLog();
        row.setContractId(command.getContractId());
        row.setContractBusNo(command.getContractBusNo());
        row.setInfno(command.getInfno());
        row.setInfname(resolveInfname(command));
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
     * 2207/2304 成功后回填结算回执与 invoke 日志关联。
     *
     * @param invokeLogId {@code yb_invoke_log.id}
     * @param settlementRecordId {@code yb_settlement_record.id}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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

    /**
     * 序列化平台响应全文（含 err_msg、warnMsg）。
     */
    public static String serializeResponseBody(ResultBody body) {
        if (body == null) {
            return null;
        }
        return JSON.toJSONString(body);
    }

    /**
     * 提取平台 err_msg；无响应时返回 null。
     */
    public static String extractPlatformErrMsg(ResultBody body) {
        if (body == null) {
            return null;
        }
        return trimErrMsg(ChsTextSupport.platformErrMsg(body, null));
    }

    /**
     * 调用异常摘要（HTTP 超时、签到失败等）。
     */
    public static String serializeInvokeException(RuntimeException ex) {
        if (ex == null) {
            return null;
        }
        JSONObject json = new JSONObject();
        json.put("exceptionType", ex.getClass().getSimpleName());
        json.put("exceptionMessage", ex.getMessage());
        return json.toJSONString();
    }

    /**
     * 平台 infcode 是否成功。
     */
    public static boolean isPlatformSuccess(ResultBody body) {
        return body != null && ChsPlatformCodes.INFCODE_SUCCESS.equals(body.getInfcode());
    }

    private static String resolveInfname(YbInvokeLogCommand command) {
        if (StringUtils.isNotBlank(command.getInfname())) {
            return command.getInfname().trim();
        }
        return ChsInfNo.resolveDisplayName(command.getInfno());
    }

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
