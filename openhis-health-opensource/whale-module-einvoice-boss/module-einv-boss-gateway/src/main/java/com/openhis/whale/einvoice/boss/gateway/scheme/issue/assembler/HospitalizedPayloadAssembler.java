/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.gateway.scheme.issue.assembler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.whale.einvoice.boss.gateway.config.EinvoiceBossGatewayProperties;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossIssueSnapshot;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossLine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

import static com.openhis.whale.einvoice.boss.gateway.scheme.issue.assembler.BossPayloadSupport.*;

/**
 * 住院线（{@code invEBillHospitalized}）业务 JSON 组装。
 *
 * <p>较门诊/挂号额外包含 {@code hospitalNo}、{@code visitNo}、{@code isArrears}、{@code category} 等住院专用字段。</p>
 */
@Component
@RequiredArgsConstructor
public class HospitalizedPayloadAssembler {

    private final EinvoiceBossGatewayProperties properties;

    /**
     * 组装住院电子票据开具请求体。
     *
     * @param document boss 单据
     * @param lines 明细行
     * @param snapshot health 患者/住院快照
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
        body.put("hospitalNo", resolveHospitalNo(snapshot));
        body.put("visitNo", resolveVisitNo(snapshot));
        body.put("consultationDate", formatDay(snapshot.getBillDateTime()));
        body.put("patientId", snapshot.getPatientId().toString());
        body.put("patientNo", snapshot.getEncounterBusNo());
        putMedicalEncounterFields(body, snapshot, properties);
        // 博思住院票必填 category=入院科室名称（与 patientCategory 同值；缺省时院点校验 EYD-ESA-0109）
        String categoryName = snapshot.getPatientCategory();
        if (!StringUtils.hasText(categoryName)) {
            categoryName = defaultIfBlank(snapshot.getCaseNumber(),
                defaultIfBlank(snapshot.getEncounterBusNo(), "住院"));
        }
        body.put("category", categoryName);
        body.put("patientCategory", categoryName);
        if (StringUtils.hasText(snapshot.getPatientCategoryCode())) {
            body.put("categoryCode", snapshot.getPatientCategoryCode());
            body.put("patientCategoryCode", snapshot.getPatientCategoryCode());
        }
        // 博思住院票必填 inHospitalDate/outHospitalDate（yyyyMMdd）
        LocalDateTime inAt = snapshot.getInHospitalDateTime();
        if (inAt == null) {
            inAt = snapshot.getBillDateTime();
        }
        body.put("inHospitalDate", formatDay(inAt));
        LocalDateTime outAt = snapshot.getOutHospitalDateTime();
        if (outAt == null) {
            outAt = snapshot.getBillDateTime();
        }
        body.put("outHospitalDate", formatDay(outAt));
        fillInsuranceOrSelfPayAmounts(body, snapshot, document.getTotalAmt());
        body.put("payChannelDetail", buildPayChannelDetail(snapshot, document.getTotalAmt()));
        body.put("chargeDetail", buildChargeDetail(lines));
        body.put("listDetail", buildListDetail(lines));
        body.put("isArrears", "1");
        return JSON.toJSONString(body);
    }

    /**
     * 解析住院号：快照有值用之，否则回退就诊流水号。
     *
     * @param snapshot 住院快照
     * @return hospitalNo
     */
    private String resolveHospitalNo(BossIssueSnapshot snapshot) {
        if (StringUtils.hasText(snapshot.getHospitalNo())) {
            return snapshot.getHospitalNo();
        }
        return snapshot.getEncounterBusNo();
    }

    /**
     * 解析住院就诊次：快照有值用之，否则与 {@link #resolveHospitalNo} 一致。
     *
     * @param snapshot 住院快照
     * @return visitNo
     */
    private String resolveVisitNo(BossIssueSnapshot snapshot) {
        if (StringUtils.hasText(snapshot.getVisitNo())) {
            return snapshot.getVisitNo();
        }
        return resolveHospitalNo(snapshot);
    }
}
