/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.settlement;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.application.outpatient.support.ChsSetlinfoSupport;
import com.openhis.chs.domain.settlement.entity.YbSettlementFundDetail;
import com.openhis.chs.domain.settlement.repository.YbSettlementFundDetailRepository;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 将 2207/2304 setldetail 写入 {@code yb_settlement_fund_detail}。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class YbSettlementFundDetailWriter {

    private final YbSettlementFundDetailRepository fundDetailRepository;

    /**
     * 解析 setldetail JSON 并落库。
     *
     * @param settlementRecordId 结算回执 id
     * @param setldetailJson setldetail 数组 JSON
     * @param tenantId 租户
     */
    public void persistFromSetldetail(Long settlementRecordId, String setldetailJson, Long tenantId) {
        // Gate：回执 id 与 setldetail JSON 必填
        if (settlementRecordId == null || StringUtils.isBlank(setldetailJson)) {
            return;
        }
        JSONArray array = JSON.parseArray(setldetailJson);
        if (array == null || array.isEmpty()) {
            return;
        }
        // Pre：租户 id 缺省取当前线程
        Long resolvedTenantId = TenantUtils.getCurrentTenantId();
        if (tenantId != null) {
            resolvedTenantId = tenantId;
        }
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < array.size(); i++) {
            JSONObject item = array.getJSONObject(i);
            if (item == null) {
                continue;
            }
            YbSettlementFundDetail row = new YbSettlementFundDetail();
            row.setSettlementRecordId(settlementRecordId);
            row.setFundPayType(item.getString("fund_pay_type"));
            row.setFundPayTypeName(item.getString("fund_pay_type_name"));
            row.setInscpScpAmt(ChsSetlinfoSupport.decimal(item, "inscp_scp_amt", null));
            row.setCrtPaybLmtAmt(ChsSetlinfoSupport.decimal(item, "crt_payb_lmt_amt", null));
            row.setFundPayamt(ChsSetlinfoSupport.decimal(item, "fund_payamt", null));
            row.setSetlProcInfo(item.getString("setl_proc_info"));
            row.setTenantId(resolvedTenantId);
            row.setCreatedAt(now);
            fundDetailRepository.save(row);
        }
    }
}
