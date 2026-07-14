/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.gateway.scheme.issue.assembler;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.whale.einvoice.boss.gateway.config.EinvoiceBossGatewayProperties;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossIssueSnapshot;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossLine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 博思医疗票报文公共字段工具（挂号联调验证过的院点必填项集中于此，避免各 Assembler 遗漏）。
 *
 * <p>REG/AMB/IMP 三条业务线共用；COM 通用票字段差异较大，见 {@link CommonPayloadAssembler}。</p>
 */
public final class BossPayloadSupport {

    /** 博思业务时间戳格式（精确到毫秒）。 */
    public static final DateTimeFormatter BOSS_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    /** 博思业务日期格式（就诊日等）。 */
    public static final DateTimeFormatter BOSS_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private BossPayloadSupport() {
    }

    /**
     * 写入医保/就诊类别等院点必填医疗字段。
     *
     * @param body 待组装的业务 JSON 根对象（就地修改）
     * @param snapshot health 开票快照
     * @param properties 网关院点配置（医疗机构等级等）
     */
    public static void putMedicalEncounterFields(JSONObject body, BossIssueSnapshot snapshot, EinvoiceBossGatewayProperties properties) {
        body.put("medCareTypeCode", defaultIfBlank(snapshot.getMedCareTypeCode(), "07"));
        body.put("medicalCareType", defaultIfBlank(snapshot.getMedicalCareType(), "全自费"));
        body.put("medicalInstitution", defaultIfBlank(properties.getPlatform().getMedicalInstitution(), "二级甲等"));
        body.put("medCareInstitution", defaultIfBlank(properties.getPlatform().getMedCareInstitution(), ""));
        if (snapshot.getPatientCategory() != null && !snapshot.getPatientCategory().isBlank()) {
            body.put("patientCategory", snapshot.getPatientCategory());
        }
        if (snapshot.getPatientCategoryCode() != null && !snapshot.getPatientCategoryCode().isBlank()) {
            body.put("patientCategoryCode", snapshot.getPatientCategoryCode());
        }
    }

    /**
     * 填充全自费场景的基金/自付金额列（各基金项填 0，ownPay/selfCashPay 等于总额）。
     *
     * @param body 业务 JSON 根对象
     * @param totalAmt 开票总额
     */
    public static void fillSelfPayAmounts(JSONObject body, BigDecimal totalAmt) {
        BigDecimal zero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = scale2(totalAmt);
        body.put("accountPay", zero);
        body.put("fundPay", zero);
        body.put("otherfundPay", zero);
        body.put("ownPay", total);
        body.put("selfConceitedAmt", zero);
        body.put("selfPayAmt", zero);
        body.put("selfCashPay", total);
        body.put("reimbursementAmt", zero);
        body.put("otherInfo", buildStandardOtherInfo());
        body.put("otherMedicalList", new JSONArray());
    }

    /**
     * 对齐旧 OpenHis 门诊 otherInfo 结构；自费场景各扩展项填 0，满足院点博思非空校验。
     */
    public static JSONArray buildStandardOtherInfo() {
        BigDecimal zero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        JSONArray array = new JSONArray();
        array.add(otherInfoItem("1", "公务员医疗补助资金支出", zero));
        array.add(otherInfoItem("2", "企业补充医疗保险基金支出", zero));
        array.add(otherInfoItem("3", "居民大病保险资金支出", zero));
        array.add(otherInfoItem("4", "职工大额医疗费用补助基金支出", zero));
        array.add(otherInfoItem("5", "医疗救助基金支出", zero));
        array.add(otherInfoItem("6", "医院负担金额", zero));
        array.add(otherInfoItem("7", "其他支出", zero));
        return array;
    }

    /**
     * 构建单一现金支付渠道明细（payChannelCode=01）。
     *
     * @param totalAmt 渠道金额
     * @return payChannelDetail 数组
     */
    public static JSONArray buildPayChannel(BigDecimal totalAmt) {
        JSONArray channels = new JSONArray();
        JSONObject channel = new JSONObject();
        channel.put("payChannelCode", "01");
        channel.put("payChannelValue", scale2(totalAmt));
        channels.add(channel);
        return channels;
    }

    /**
     * 构建 chargeDetail 收费汇总明细（含 selfAmt=0）。
     *
     * @param lines boss 明细行（按 lineNo 已排序）
     * @return chargeDetail 数组
     */
    public static JSONArray buildChargeDetail(List<EinvBossLine> lines) {
        BigDecimal zero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        JSONArray array = new JSONArray();
        for (EinvBossLine line : lines) {
            JSONObject item = new JSONObject();
            item.put("sortNo", line.getLineNo());
            item.put("chargeCode", line.getChargeCode());
            item.put("chargeName", line.getChargeName());
            item.put("unit", defaultIfBlank(line.getUnit(), "项"));
            item.put("std", scale2(line.getStdAmount()));
            item.put("number", line.getQuantity() != null ? line.getQuantity() : BigDecimal.ONE);
            item.put("amt", scale2(line.getAmount()));
            item.put("selfAmt", zero);
            array.add(item);
        }
        return array;
    }

    /**
     * 构建 listDetail 清单明细（挂号/门诊/住院博思均要求非空）。
     *
     * @param lines boss 明细行
     * @return listDetail 数组
     */
    public static JSONArray buildListDetail(List<EinvBossLine> lines) {
        BigDecimal zero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        JSONArray array = new JSONArray();
        for (EinvBossLine line : lines) {
            JSONObject item = new JSONObject();
            item.put("listDetailNo", String.valueOf(line.getLineNo()));
            item.put("chargeCode", line.getChargeCode());
            item.put("chargeName", line.getChargeName());
            item.put("code", line.getChargeCode());
            item.put("name", line.getChargeName());
            item.put("unit", defaultIfBlank(line.getUnit(), "次"));
            item.put("std", scale2(line.getStdAmount()));
            item.put("number", line.getQuantity() != null ? line.getQuantity() : BigDecimal.ONE);
            item.put("amt", scale2(line.getAmount()));
            item.put("selfAmt", zero);
            array.add(item);
        }
        return array;
    }

    /**
     * 金额保留两位小数（HALF_UP）；{@code null} 视为 0。
     *
     * @param value 原始金额
     * @return 两位小数
     */
    public static BigDecimal scale2(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 格式化为博思业务时间戳；空值取当前时间。
     *
     * @param time 业务时间
     * @return {@link #BOSS_TIME} 格式字符串
     */
    public static String formatTime(LocalDateTime time) {
        LocalDateTime actual = time != null ? time : LocalDateTime.now();
        return BOSS_TIME.format(actual);
    }

    /**
     * 格式化为博思业务日期；空值取当前日期。
     *
     * @param time 业务时间
     * @return {@link #BOSS_DAY} 格式字符串
     */
    public static String formatDay(LocalDateTime time) {
        LocalDateTime actual = time != null ? time : LocalDateTime.now();
        return BOSS_DAY.format(actual);
    }

    /**
     * 字符串空安全：{@code null} 或空白时返回 fallback。
     *
     * @param value 原值
     * @param fallback 默认值
     * @return 非空字符串
     */
    public static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * 构造 otherInfo 数组中单条扩展项（infoNo/infoName/infoValue）。
     *
     * @param infoNo 扩展项序号
     * @param infoName 扩展项名称
     * @param infoValue 扩展项金额
     * @return JSON 对象
     */
    private static JSONObject otherInfoItem(String infoNo, String infoName, BigDecimal infoValue) {
        JSONObject item = new JSONObject();
        item.put("infoNo", infoNo);
        item.put("infoName", infoName);
        item.put("infoValue", infoValue);
        return item;
    }
}
