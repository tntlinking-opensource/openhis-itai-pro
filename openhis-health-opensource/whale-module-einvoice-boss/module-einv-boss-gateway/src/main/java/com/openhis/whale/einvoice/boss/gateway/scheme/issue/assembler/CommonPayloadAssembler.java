/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.gateway.scheme.issue.assembler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.whale.einvoice.boss.gateway.config.EinvoiceBossGatewayProperties;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossIssueSnapshot;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossLine;
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
        body.put("payChannelDetail", buildPayChannel(document.getTotalAmt()));
        body.put("chargeDetail", buildChargeDetail(lines));
        return JSON.toJSONString(body);
    }

    /**
     * 构建通用票支付渠道（COM 线不共用 {@link BossPayloadSupport#buildPayChannel} 以避免 unit 默认值差异）。
     *
     * @param totalAmt 支付总额
     * @return payChannelDetail 数组
     */
    private JSONArray buildPayChannel(BigDecimal totalAmt) {
        JSONArray channels = new JSONArray();
        JSONObject channel = new JSONObject();
        channel.put("payChannelCode", "01");
        channel.put("payChannelValue", scale2(totalAmt));
        channels.add(channel);
        return channels;
    }

    /**
     * 构建通用票 chargeDetail（无 selfAmt 列）。
     *
     * @param lines 明细行
     * @return chargeDetail 数组
     */
    private JSONArray buildChargeDetail(List<EinvBossLine> lines) {
        JSONArray array = new JSONArray();
        for (EinvBossLine line : lines) {
            JSONObject item = new JSONObject();
            item.put("sortNo", line.getLineNo());
            item.put("chargeCode", line.getChargeCode());
            item.put("chargeName", line.getChargeName());
            item.put("unit", line.getUnit());
            item.put("std", scale2(line.getStdAmount()));
            item.put("number", line.getQuantity() != null ? line.getQuantity() : BigDecimal.ONE);
            item.put("amt", scale2(line.getAmount()));
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
