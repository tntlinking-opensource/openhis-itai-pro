/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.settlement.service;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.chs.domain.settlement.entity.YbSettlementFundDetail;
import com.zxplat.plugin.chs.domain.settlement.repository.YbSettlementFundDetailRepository;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 将 2207/2304 setldetail 写入 {@code yb_settlement_fund_detail} */
@Service
@RequiredArgsConstructor
public class YbSettlementFundDetailDomainService {

    private final YbSettlementFundDetailRepository fundDetailRepository;

    /**
     * 将 2207/2304 setldetail 解析并写入基金分项明细表。
     *
     * @param settlementRecordId 结算回执主键
     * @param setldetailJson     setldetail 数组 JSON
     * @param tenantId           租户 ID；为空时取当前租户
     */
    public void persistFromSetldetail(Long settlementRecordId, String setldetailJson, Long tenantId) {
        if (settlementRecordId == null || isEmpty(setldetailJson)) {
            return;
        }
        JSONArray array = JSON.parseArray(setldetailJson);
        if (array == null || array.isEmpty()) {
            return;
        }
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
            row.setInscpScpAmt(decimal(item, "inscp_scp_amt"));
            row.setCrtPaybLmtAmt(decimal(item, "crt_payb_lmt_amt"));
            row.setFundPayamt(decimal(item, "fund_payamt"));
            row.setSetlProcInfo(item.getString("setl_proc_info"));
            row.setTenantId(resolvedTenantId);
            row.setCreatedAt(now);
            fundDetailRepository.save(row);
        }
    }

    /**
     * 从 JSON 对象安全解析金额字段。
     *
     * @param json setldetail 单项
     * @param key  字段名
     * @return 金额；缺失时 {@code null}
     */
    private static BigDecimal decimal(JSONObject json, String key) {
        if (json == null || !json.containsKey(key) || json.get(key) == null) {
            return null;
        }
        return new BigDecimal(String.valueOf(json.get(key)));
    }
}
