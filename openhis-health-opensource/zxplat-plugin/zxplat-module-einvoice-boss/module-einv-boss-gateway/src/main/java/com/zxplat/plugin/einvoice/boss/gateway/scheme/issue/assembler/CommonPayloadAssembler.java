/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.gateway.scheme.issue.assembler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.einvoice.boss.gateway.config.EinvoiceBossGatewayProperties;
import com.zxplat.plugin.einvoice.boss.domain.issue.result.BossIssueSnapshot;
import com.zxplat.plugin.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.zxplat.plugin.einvoice.boss.domain.document.entity.EinvBossLine;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 医疗通用票（{@code invoiceEBillCommon}）业务 JSON 组装。
 *
 * <p>付款人信息取自 {@link EinvBossDocument} 单据字段而非 health 快照；
 * 不含 listDetail/otherInfo 等医保扩展结构。</p>
 */
@Component
@RequiredArgsConstructor
public class CommonPayloadAssembler {

    private static final DateTimeFormatter BOSS_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final EinvoiceBossGatewayProperties properties;

    /**
     * 组装通用票开具 JSON。
     *
     * @param document boss 单据
     * @param lines 明细行
     * @param snapshot 快照（COM 由单据字段构造）
     * @return 业务 JSON 字符串
     */
    public String assemble(EinvBossDocument document, List<EinvBossLine> lines, BossIssueSnapshot snapshot) {
        JSONObject body = new JSONObject();
        body.put("busNo", document.getBusNo());
        body.put("busType", document.getBusType());
        body.put("payer", document.getPayerName());
        body.put("busDateTime", formatTime(snapshot.getBillDateTime()));
        body.put("placeCode", properties.getPlatform().getPlaceCode());
        String operator = StringUtils.defaultIfBlank(document.getOperatorName(), "system");
        body.put("payee", operator);
        body.put("author", operator);
        body.put("checker", operator);
        body.put("totalAmt", scale2(document.getTotalAmt()));
        if (StringUtils.isNotBlank(document.getRemark())) {
            body.put("remark", document.getRemark());
        }
        body.put("tel", document.getPayerPhone());
        body.put("payerType", StringUtils.defaultIfBlank(document.getPayerType(), "1"));
        if (StringUtils.isNotBlank(document.getPayerIdCard())) {
            body.put("idCardNo", document.getPayerIdCard());
        }
        body.put("sex", StringUtils.defaultIfBlank(document.getPayerSex(), "男"));
        body.put("age", StringUtils.defaultIfBlank(document.getPayerAge(), "0"));
        body.put("payChannelDetail", BossPayloadSupport.buildPayChannelDetail(null, document.getTotalAmt()));
        body.put("chargeDetail", buildChargeDetail(lines));
        return JSON.toJSONString(body);
    }

    /**
     * 构建通用票 chargeDetail（无 selfAmt 列；按 chargeCode 聚合，与门诊/挂号/住院一致）。
     *
     * @param lines 明细行
     * @return chargeDetail 数组
     */
    private JSONArray buildChargeDetail(List<EinvBossLine> lines) {
        JSONArray array = new JSONArray();
        int sortNo = 1;
        for (BossPayloadSupport.AggregatedChargeLine agg : BossPayloadSupport.aggregateByChargeCode(lines)) {
            JSONObject item = new JSONObject();
            item.put("sortNo", sortNo++);
            item.put("chargeCode", agg.chargeCode);
            item.put("chargeName", agg.chargeName);
            item.put("unit", agg.unit);
            item.put("std", agg.amt);
            item.put("number", BigDecimal.ONE);
            item.put("amt", agg.amt);
            array.add(item);
        }
        return array;
    }

    /**
     * 金额两位小数；{@code null} 视为 0（COM 线本地实现，与 {@link BossPayloadSupport#scale2} 语义一致）。
     */
    private BigDecimal scale2(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 格式化为博思业务时间戳；空值取当前时间。
     */
    private String formatTime(LocalDateTime time) {
        LocalDateTime actual = time != null ? time : LocalDateTime.now();
        return BOSS_TIME.format(actual);
    }
}
