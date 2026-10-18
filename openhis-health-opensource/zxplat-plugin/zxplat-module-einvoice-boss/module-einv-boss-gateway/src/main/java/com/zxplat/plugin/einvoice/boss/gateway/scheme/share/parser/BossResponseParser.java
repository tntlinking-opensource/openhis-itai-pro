/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.gateway.scheme.share.parser;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.einvoice.boss.domain.invoke.result.BossInvokeResult;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

import static com.zxplat.plugin.einvoice.boss.gateway.scheme.share.support.BossPictureUrlSupport.normalizePictureUrl;

/**
 * 博思 HTTP 响应解析器（gateway scheme.share 层）。
 *
 * <p>职责：将 {@link EinvoiceBossGatewayClient} 返回的原始 HTTP 体解码为
 * {@link BossInvokeResult}。不负责业务拒收抛异常（由 {@code IssueOutputParser} 或
 * 各 cap OutboundSupport 处理）。</p>
 *
 * <p>兼容博思多种信封格式：外层 {@code data} base64、{@code result} 为字符串或嵌套对象、
 * {@code message} base64 业务 JSON。pictureUrl 二进制令牌按 ISO-8859-1 保留。</p>
 */
@Component
public class BossResponseParser {

    /** 博思 {@code createTime} 格式：{@code yyyyMMddHHmmssSSS}（17 位毫秒）。 */
    private static final DateTimeFormatter BOSS_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    /**
     * 解析博思 HTTP 响应体为结构化调用结果。
     *
     * @param responseBody 原始 HTTP 响应字符串；空串时 {@code resultCode=EMPTY}
     * @return 含 resultCode、rawMessage、票面字段、红票 eScarlet 字段的解析结果
     */
    public BossInvokeResult parse(String responseBody) {
        BossInvokeResult result = new BossInvokeResult();
        if (StringUtils.isBlank(responseBody)) {
            result.setResultCode("EMPTY");
            return result;
        }
        JSONObject outer = JSON.parseObject(responseBody);
        JSONObject businessRoot = unwrapEnvelope(outer);

        Object resultField = businessRoot.get("result");
        String resultCode;
        String messageNode;
        if (resultField instanceof JSONObject resultNode) {
            resultCode = resultNode.getString("result");
            messageNode = resultNode.getString("message");
        } else if (resultField instanceof String resultText) {
            resultCode = resultText;
            messageNode = businessRoot.getString("message");
        } else {
            resultCode = businessRoot.getString("result");
            messageNode = businessRoot.getString("message");
        }
        result.setResultCode(resultCode);
        String decodedMessage = decodeMessageText(messageNode);
        if (decodedMessage != null) {
            result.setRawMessage(decodedMessage);
        } else {
            result.setRawMessage(messageNode);
        }
        if (StringUtils.isBlank(messageNode)) {
            return result;
        }
        JSONObject business = decodeMessageJson(messageNode);
        result.setBusinessJson(business);
        if (business == null && StringUtils.isNotBlank(decodedMessage)) {
            String extracted = extractErrorCode(decodedMessage);
            if (StringUtils.isBlank(resultCode) || "E9999".equals(resultCode) || "BOSS_ERROR".equals(resultCode)) {
                result.setResultCode(extracted);
            }
        }
        if (business != null) {
            result.setBillBatchCode(business.getString("billBatchCode"));
            result.setBillNo(business.getString("billNo"));
            result.setRandom(business.getString("random"));
            result.setBillQrCode(business.getString("billQRCode"));
            result.setPictureUrl(normalizePictureUrl(business.getString("pictureUrl")));
            result.setPictureNetUrl(normalizePictureUrl(business.getString("pictureNetUrl")));
            result.setEScarletBillNo(business.getString("eScarletBillNo"));
            result.setEScarletBillBatchCode(business.getString("eScarletBillBatchCode"));
            result.setEScarletRandom(business.getString("eScarletRandom"));
            result.setBillCreateTime(parseBossTime(business.getString("createTime")));
        }
        return result;
    }

    /**
     * 解码博思 HTTP 外层信封：若存在 {@code data} 字段则 base64 解码为内层业务 JSON。
     *
     * <p>与旧系统 PreInvoicePost 一致；解码失败时回退使用原始 outer，避免整包解析失败。</p>
     *
     * @param outer HTTP 响应最外层 JSON
     * @return 内层业务根节点；无 data 时返回 outer 本身
     */
    private JSONObject unwrapEnvelope(JSONObject outer) {
        if (outer == null) {
            return new JSONObject();
        }
        String dataField = outer.getString("data");
        if (StringUtils.isBlank(dataField)) {
            return outer;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(dataField);
            String json = new String(decoded, StandardCharsets.UTF_8);
            return JSON.parseObject(json);
        } catch (Exception ex) {
            return outer;
        }
    }

    /**
     * 将 {@code message} 节点解码为业务 JSON 对象。
     *
     * <p>支持明文 JSON（以 {@code \{} 开头）或 base64 编码。base64 字节按 ISO-8859-1 解析，
     * 以保留 pictureUrl {@code t=} 参数中的非 UTF-8 八位组。</p>
     *
     * @param messageNode 博思响应中的 message 字段原文
     * @return 业务 JSON；无法解析时 {@code null}
     */
    private JSONObject decodeMessageJson(String messageNode) {
        if (StringUtils.isBlank(messageNode)) {
            return null;
        }
        String trimmed = messageNode.trim();
        if (trimmed.startsWith("{")) {
            try {
                return JSON.parseObject(trimmed);
            } catch (Exception ex) {
                return null;
            }
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(trimmed);
            return JSON.parseObject(new String(decoded, StandardCharsets.ISO_8859_1));
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 将 {@code message} 解码为可读文本（用于 {@code rawMessage} 日志与拒收详情）。
     *
     * <p>明文 JSON 时规范化为 JSON 字符串；base64 文本优先按 UTF-8 严格解码（拒收中文），
     * 非法 UTF-8 再回退 ISO-8859-1（票面二进制令牌）。失败时返回原文。</p>
     *
     * @param messageNode message 字段原文
     * @return 解码后的文本；空输入原样返回
     */
    private String decodeMessageText(String messageNode) {
        if (StringUtils.isBlank(messageNode)) {
            return messageNode;
        }
        String trimmed = messageNode.trim();
        if (trimmed.startsWith("{")) {
            try {
                return JSON.parseObject(trimmed).toJSONString();
            } catch (Exception ignore) {
                return trimmed;
            }
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(trimmed);
            return decodePayloadBytes(decoded);
        } catch (Exception ex) {
            return messageNode;
        }
    }

    /**
     * 将博思 message 的 base64 载荷转为可读文本。
     *
     * <p>拒收说明是 UTF-8 中文；若一律按 ISO-8859-1 解码，日志会把中文打成 Latin-1 乱码。
     * 票面 JSON 中的 pictureUrl 二进制令牌不是合法 UTF-8，回退 ISO-8859-1 以保持与
     * {@link #decodeMessageJson} 一致。</p>
     *
     * @param decoded base64 解码后的原始字节
     * @return 可读文本
     */
    private String decodePayloadBytes(byte[] decoded) {
        CharsetDecoder utf8Decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return utf8Decoder.decode(ByteBuffer.wrap(decoded)).toString();
        } catch (CharacterCodingException ex) {
            return new String(decoded, StandardCharsets.ISO_8859_1);
        }
    }

    /**
     * 从博思明文错误信息中提取错误码前缀（形如 {@code E1001:描述}）。
     *
     * <p>当 HTTP 层 resultCode 为 E9999/BOSS_ERROR 等泛化码时，用本方法提升可读性。</p>
     *
     * @param decodedMessage 已解码的 message 文本
     * @return 冒号前符合 {@code [A-Z0-9_-]+} 的前缀；否则 {@code BOSS_ERROR}
     */
    private String extractErrorCode(String decodedMessage) {
        if (StringUtils.isBlank(decodedMessage)) {
            return "BOSS_ERROR";
        }
        int colon = decodedMessage.indexOf(':');
        if (colon > 0) {
            String prefix = decodedMessage.substring(0, colon).trim();
            if (prefix.matches("[A-Z0-9_-]+")) {
                return prefix;
            }
        }
        return "BOSS_ERROR";
    }

    /**
     * 解析博思业务 JSON 中的 {@code createTime} 为 {@link LocalDateTime}。
     *
     * @param text {@code yyyyMMddHHmmssSSS} 格式时间串
     * @return 解析成功的时间；空或格式错误时返回 {@link LocalDateTime#now()}
     */
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
