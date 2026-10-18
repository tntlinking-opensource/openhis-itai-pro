/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.gateway.scheme.issue.assembler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.einvoice.boss.gateway.config.EinvoiceBossGatewayProperties;
import com.zxplat.plugin.einvoice.boss.domain.issue.result.BossIssueSnapshot;
import com.zxplat.plugin.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.zxplat.plugin.einvoice.boss.domain.document.entity.EinvBossLine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.zxplat.plugin.einvoice.boss.gateway.scheme.issue.assembler.BossPayloadSupport.*;

/**
 * 挂号线（{@code invEBillRegistration}）业务 JSON 组装。
 *
 * <p>字段结构与门诊线相近，差异在 busType 与博思 serviceId；明细经 {@link BossPayloadSupport} 统一构建。</p>
 */
@Component
@RequiredArgsConstructor
public class RegistrationPayloadAssembler {

    private final EinvoiceBossGatewayProperties properties;

    /**
     * 组装挂号电子票据开具请求体。
     *
     * @param document boss 单据（含 busNo、busType、totalAmt）
     * @param lines 明细行
     * @param snapshot health 患者/就诊快照
     * @return 业务 JSON 明文字符串
     */
    public String assemble(EinvBossDocument document, List<EinvBossLine> lines, BossIssueSnapshot snapshot) {
        JSONObject body = new JSONObject();
        body.put("busNo", document.getBusNo());
        body.put("busType", document.getBusType());
        body.put("payer", snapshot.getPatientName());
        body.put("busDateTime", formatTime(snapshot.getBillDateTime()));
        body.put("placeCode", properties.getPlatform().getPlaceCode());
        String operator = resolveOperatorDisplay(snapshot);
        body.put("payee", operator);
        body.put("author", operator);
        body.put("checker", operator);
        body.put("totalAmt", scale2(document.getTotalAmt()));
        body.put("payerType", "1");
        body.put("cardType", "1101");
        body.put("cardNo", snapshot.getIdCard() != null ? snapshot.getIdCard() : snapshot.getPatientId().toString());
        body.put("sex", snapshot.getGenderDisplay());
        body.put("age", snapshot.getAge());
        body.put("tel", snapshot.getPhone());
        body.put("consultationDate", formatDay(snapshot.getBillDateTime()));
        body.put("patientNo", snapshot.getEncounterBusNo());
        body.put("patientId", snapshot.getPatientId().toString());
        putMedicalEncounterFields(body, snapshot, properties);
        fillInsuranceOrSelfPayAmounts(body, snapshot, document.getTotalAmt());
        body.put("payChannelDetail", buildPayChannelDetail(snapshot, document.getTotalAmt()));
        body.put("chargeDetail", buildChargeDetail(lines));
        body.put("listDetail", buildListDetail(lines));
        return JSON.toJSONString(body);
    }
}
