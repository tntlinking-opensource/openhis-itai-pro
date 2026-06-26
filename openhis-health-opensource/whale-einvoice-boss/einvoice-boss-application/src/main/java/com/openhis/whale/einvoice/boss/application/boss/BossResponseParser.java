package com.openhis.whale.einvoice.boss.application.boss;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.einvoice.boss.contracts.constants.EinvBossErrorCodes;
import com.openhis.whale.einvoice.boss.contracts.dto.BossInvoiceIssueResult;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * 博思 HTTP 响应解析（result/message base64 解码）。
 */
@Component
public class BossResponseParser {

    private static final DateTimeFormatter BOSS_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    /**
     * 解析博思外层响应 JSON。
     *
     * @param responseBody HTTP 响应体
     * @return 解析结果
     */
    public BossInvokeResult parse(String responseBody) {
        BossInvokeResult result = new BossInvokeResult();
        if (StringUtils.isBlank(responseBody)) {
            result.setResultCode("EMPTY");
            return result;
        }
        JSONObject outer = JSON.parseObject(responseBody);
        String resultCode = outer.getString("result");
        result.setResultCode(resultCode);
        String messageNode = outer.getString("message");
        result.setRawMessage(messageNode);
        if (StringUtils.isBlank(messageNode)) {
            return result;
        }
        JSONObject business = decodeMessageJson(messageNode);
        result.setBusinessJson(business);
        if (business != null) {
            result.setBillBatchCode(business.getString("billBatchCode"));
            result.setBillNo(business.getString("billNo"));
            result.setRandom(business.getString("random"));
            result.setBillQrCode(business.getString("billQRCode"));
            result.setPictureUrl(business.getString("pictureUrl"));
            result.setPictureNetUrl(business.getString("pictureNetUrl"));
            result.setBillCreateTime(parseBossTime(business.getString("createTime")));
        }
        return result;
    }

    /**
     * 将解析结果转为开具结果 DTO。
     *
     * @param invokeResult 博思响应
     * @param busNo 业务流水号
     * @param totalAmt 开票总额
     * @return 开具结果
     */
    public BossInvoiceIssueResult toIssueResult(BossInvokeResult invokeResult, String busNo, BigDecimal totalAmt) {
        if (!invokeResult.isSuccess()) {
            String msg = invokeResult.getRawMessage();
            throw new BusinessException(EinvBossErrorCodes.EINV_BOSS_REJECT, "博思拒收: " + invokeResult.getResultCode() + " " + msg);
        }
        BossInvoiceIssueResult dto = new BossInvoiceIssueResult();
        dto.setBusNo(busNo);
        dto.setTotalAmt(totalAmt);
        dto.setBillNo(invokeResult.getBillNo());
        dto.setBatchCode(invokeResult.getBillBatchCode());
        dto.setRandom(invokeResult.getRandom());
        dto.setBillQrCode(invokeResult.getBillQrCode());
        dto.setPictureUrl(invokeResult.getPictureUrl());
        dto.setPictureNetUrl(invokeResult.getPictureNetUrl());
        dto.setBillCreateTime(invokeResult.getBillCreateTime());
        return dto;
    }

    private JSONObject decodeMessageJson(String messageNode) {
        try {
            return JSON.parseObject(messageNode);
        } catch (Exception ignore) {
            // message 常为 base64 包裹的业务 JSON
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(messageNode);
            String json = new String(decoded, StandardCharsets.UTF_8);
            return JSON.parseObject(json);
        } catch (Exception ex) {
            return null;
        }
    }

    private LocalDateTime parseBossTime(String text) {
        if (StringUtils.isBlank(text)) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(text, BOSS_TIME);
        } catch (Exception ex) {
            return LocalDateTime.now();
        }
    }
}
