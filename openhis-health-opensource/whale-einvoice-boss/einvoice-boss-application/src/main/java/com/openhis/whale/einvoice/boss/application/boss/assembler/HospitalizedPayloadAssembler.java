package com.openhis.whale.einvoice.boss.application.boss.assembler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.whale.einvoice.boss.application.config.EinvoiceBossProperties;
import com.openhis.whale.einvoice.boss.contracts.dto.BossIssueSnapshot;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossLine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 住院线（invEBillHospitalized）报文组装。
 */
@Component
@RequiredArgsConstructor
public class HospitalizedPayloadAssembler {

    private static final DateTimeFormatter BOSS_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final DateTimeFormatter BOSS_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final EinvoiceBossProperties properties;

    /**
     * 组装住院开具 JSON。
     *
     * @param document boss 单据
     * @param lines 明细行
     * @param snapshot health 快照
     * @return 业务 JSON 字符串
     */
    public String assemble(EinvBossDocument document, List<EinvBossLine> lines, BossIssueSnapshot snapshot) {
        JSONObject body = new JSONObject();
        body.put("busNo", document.getBusNo());
        body.put("busType", document.getBusType());
        body.put("payer", snapshot.getPatientName());
        body.put("busDateTime", formatTime(snapshot.getBillDateTime()));
        body.put("placeCode", properties.getPlatform().getPlaceCode());
        body.put("payee", String.valueOf(snapshot.getOperatorId()));
        body.put("author", String.valueOf(snapshot.getOperatorId()));
        body.put("checker", String.valueOf(snapshot.getOperatorId()));
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
        fillSelfPayAmounts(body, document.getTotalAmt());
        body.put("payChannelDetail", buildPayChannel(document.getTotalAmt()));
        body.put("chargeDetail", buildChargeDetail(lines));
        body.put("listDetail", buildListDetail(lines));
        body.put("isArrears", "1");
        return JSON.toJSONString(body);
    }

    private String resolveHospitalNo(BossIssueSnapshot snapshot) {
        if (StringUtils.hasText(snapshot.getHospitalNo())) {
            return snapshot.getHospitalNo();
        }
        return snapshot.getEncounterBusNo();
    }

    private String resolveVisitNo(BossIssueSnapshot snapshot) {
        if (StringUtils.hasText(snapshot.getVisitNo())) {
            return snapshot.getVisitNo();
        }
        return resolveHospitalNo(snapshot);
    }

    private void fillSelfPayAmounts(JSONObject body, BigDecimal totalAmt) {
        BigDecimal zero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = scale2(totalAmt);
        body.put("accountPay", zero);
        body.put("fundPay", zero);
        body.put("otherfundPay", zero);
        body.put("ownPay", total);
        body.put("selfConceitedAmt", zero);
        body.put("selfPayAmt", zero);
        body.put("selfCashPay", total);
        body.put("otherInfo", new JSONArray());
        body.put("otherMedicalList", new JSONArray());
    }

    private JSONArray buildPayChannel(BigDecimal totalAmt) {
        JSONArray channels = new JSONArray();
        JSONObject channel = new JSONObject();
        channel.put("payChannelCode", "01");
        channel.put("payChannelValue", scale2(totalAmt));
        channels.add(channel);
        return channels;
    }

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
            item.put("selfAmt", scale2(line.getAmount()));
            array.add(item);
        }
        return array;
    }

    private JSONArray buildListDetail(List<EinvBossLine> lines) {
        JSONArray array = new JSONArray();
        for (EinvBossLine line : lines) {
            JSONObject item = new JSONObject();
            item.put("name", line.getChargeName());
            item.put("chargeCode", line.getChargeCode());
            item.put("chargeName", line.getChargeName());
            item.put("std", scale2(line.getStdAmount()));
            item.put("number", line.getQuantity() != null ? line.getQuantity() : BigDecimal.ONE);
            item.put("amt", scale2(line.getAmount()));
            item.put("selfAmt", scale2(line.getAmount()));
            array.add(item);
        }
        return array;
    }

    private BigDecimal scale2(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String formatTime(LocalDateTime time) {
        LocalDateTime actual = time != null ? time : LocalDateTime.now();
        return BOSS_TIME.format(actual);
    }

    private String formatDay(LocalDateTime time) {
        LocalDateTime actual = time != null ? time : LocalDateTime.now();
        return BOSS_DAY.format(actual);
    }
}
