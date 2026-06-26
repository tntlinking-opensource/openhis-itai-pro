package com.openhis.whale.einvoice.boss.application.boss.assembler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.whale.einvoice.boss.application.config.EinvoiceBossProperties;
import com.openhis.whale.einvoice.boss.contracts.dto.BossIssueSnapshot;
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
 * 医疗通用票（invoiceEBillCommon）报文组装。
 */
@Component
@RequiredArgsConstructor
public class CommonPayloadAssembler {

    private static final DateTimeFormatter BOSS_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final EinvoiceBossProperties properties;

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
}
