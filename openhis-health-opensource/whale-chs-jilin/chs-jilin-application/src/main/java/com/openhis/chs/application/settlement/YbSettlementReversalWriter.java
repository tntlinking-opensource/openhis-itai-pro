/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.settlement;

import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.application.outpatient.support.ChsSetlinfoSupport;
import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import com.openhis.chs.domain.settlement.entity.YbSettlementReversal;
import com.openhis.chs.domain.settlement.repository.YbSettlementReversalRepository;
import com.openhis.chs.domain.settlement.service.ChargeBatchLifecycle;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 2208/2305 撤销凭证落库。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class YbSettlementReversalWriter {

    private final YbSettlementReversalRepository reversalRepository;
    private final ChargeBatchLifecycle chargeBatchLifecycle;

    /**
     * 写入撤销凭证并将原结算回执标记为 REVOKED。
     *
     * @param invokeLogId 2208/2305 对应 yb_invoke_log.id（可空）
     */
    public Long persistReversal(
        YbSettlementRecord record,
        JSONObject revokeSetlinfo,
        String setldetailJson,
        Long operatorId,
        Long invokeLogId) {
        // Gate：回执与平台撤销 setlinfo 缺一不可
        if (record == null || revokeSetlinfo == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        // Cmd：写入 yb_settlement_reversal 撤销凭证
        YbSettlementReversal reversal = new YbSettlementReversal();
        reversal.setSettlementRecordId(record.getId());
        reversal.setRevokeSetlId(text(revokeSetlinfo, "setl_id", record.getSetlId()));
        reversal.setMdtrtId(text(revokeSetlinfo, "mdtrt_id", record.getMdtrtId()));
        reversal.setPsnNo(text(revokeSetlinfo, "psn_no", record.getPsnNo()));
        reversal.setEncounterClass(record.getEncounterClass());
        reversal.setMedfeeSumamt(decimal(revokeSetlinfo, "medfee_sumamt", record.getMedfeeSumamt()));
        reversal.setFundPaySumamt(decimal(revokeSetlinfo, "fund_pay_sumamt", record.getFundPaySumamt()));
        reversal.setAcctPay(decimal(revokeSetlinfo, "acct_pay", record.getAcctPay()));
        reversal.setPsnCashPay(decimal(revokeSetlinfo, "psn_cash_pay", record.getPsnCashPay()));
        if (StringUtils.isNotBlank(setldetailJson)) {
            reversal.setFundBreakdownJson(setldetailJson);
        }
        reversal.setInvokeLogId(invokeLogId);
        reversal.setReversedAt(now);
        reversal.setTenantId(record.getTenantId());
        reversal.setCreatedAt(now);
        if (operatorId != null) {
            reversal.setCreatedBy(operatorId);
        }
        reversalRepository.saveAndFlush(reversal);

        // Asm：标记原回执 REVOKED 并同步 ChargeBatch 凭证状态
        chargeBatchLifecycle.markVoucherRevoked(record);
        record.setUpdatedAt(now);
        if (operatorId != null) {
            record.setUpdatedBy(operatorId);
        }
        return reversal.getId();
    }

    /** 2601 冲正成功后本地标记撤销（无 2208/2305 平台 setlinfo）。 */
    public Long persist2601Correction(YbSettlementRecord record, Long operatorId, Long invokeLogId) {
        if (record == null) {
            return null;
        }
        JSONObject synthetic = new JSONObject();
        synthetic.put("setl_id", record.getSetlId());
        synthetic.put("mdtrt_id", record.getMdtrtId());
        synthetic.put("psn_no", record.getPsnNo());
        synthetic.put("medfee_sumamt", record.getMedfeeSumamt());
        synthetic.put("fund_pay_sumamt", record.getFundPaySumamt());
        synthetic.put("acct_pay", record.getAcctPay());
        synthetic.put("psn_cash_pay", record.getPsnCashPay());
        return persistReversal(record, synthetic, null, operatorId, invokeLogId);
    }

    /** 兼容旧签名。 */
    public Long persistReversal(
        YbSettlementRecord record,
        JSONObject revokeSetlinfo,
        String setldetailJson,
        Long operatorId) {
        return persistReversal(record, revokeSetlinfo, setldetailJson, operatorId, null);
    }

    private static String text(JSONObject json, String key, String fallback) {
        String value = ChsSetlinfoSupport.text(json, key);
        if (StringUtils.isNotBlank(value)) {
            return value;
        }
        return fallback;
    }

    private static java.math.BigDecimal decimal(
        JSONObject json,
        String key,
        java.math.BigDecimal fallback) {
        return ChsSetlinfoSupport.decimal(json, key, fallback);
    }
}
