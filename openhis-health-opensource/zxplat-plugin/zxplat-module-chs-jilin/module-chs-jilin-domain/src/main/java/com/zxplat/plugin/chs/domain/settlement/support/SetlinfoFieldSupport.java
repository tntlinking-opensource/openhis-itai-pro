/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.settlement.support;

import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.chs.domain.inpatient.entity.YbInpatientPreSettle;
import com.zxplat.plugin.chs.domain.outpatient.entity.YbClinicPreSettle;
import com.zxplat.plugin.chs.domain.settlement.model.PlatformSetlinfoFields;
import com.zxplat.plugin.chs.domain.settlement.entity.YbSettlementRecord;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 2206/2207 {@code setlinfo} 与领域实体字段映射。 */
public final class SetlinfoFieldSupport {

    private static final DateTimeFormatter SETL_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private SetlinfoFieldSupport() {
    }

    /**
     * 将 2206 setlinfo 金额字段写入预结算快照（供 2207 透传）。
     *
     * @param row      预结算实体
     * @param setlinfo 2206 输出 setlinfo
     */
    public static void applyPreSettleAmountFields(YbClinicPreSettle row, PlatformSetlinfoFields setlinfo) {
        if (row == null || setlinfo == null) {
            return;
        }
        row.setMedfeeSumamt(decimalOr(setlinfo.getMedfeeSumamt(), row.getMedfeeSumamt()));
        row.setFundPaySumamt(decimalOr(setlinfo.getFundPaySumamt(), BigDecimal.ZERO));
        row.setAcctPay(decimalOr(setlinfo.getAcctPay(), BigDecimal.ZERO));
        row.setPsnCashPay(decimalOr(setlinfo.getPsnCashPay(), row.getMedfeeSumamt()));
        row.setFulamtOwnpayAmt(setlinfo.getFulamtOwnpayAmt());
        row.setOverlmtSelfpay(setlinfo.getOverlmtSelfpay());
        row.setPreselfpayAmt(setlinfo.getPreselfpayAmt());
        row.setInscpScpAmt(setlinfo.getInscpScpAmt());
    }

    /**
     * 将上下文中的零售/上传分差写入门诊预结算快照（平台金额字段之外）。
     *
     * @param row     预结算实体
     * @param retail  院内收费合计
     * @param upload  2204 上传合计
     * @param diffAmt 分差 retail−upload
     */
    public static void applyClinicRetailUploadDiff(
        YbClinicPreSettle row, BigDecimal retail, BigDecimal upload, BigDecimal diffAmt) {
        if (row == null) {
            return;
        }
        row.setRetailMedfeeSumamt(retail);
        row.setUploadMedfeeSumamt(upload);
        row.setRetailUploadDiffAmt(diffAmt);
    }

    /**
     * 将 2303 setlinfo 金额字段写入住院预结算快照。
     *
     * @param row      预结算实体
     * @param setlinfo 2303 输出 setlinfo
     */
    public static void applyInpatientPreSettleAmountFields(YbInpatientPreSettle row, PlatformSetlinfoFields setlinfo) {
        if (row == null || setlinfo == null) {
            return;
        }
        row.setMedfeeSumamt(decimalOr(setlinfo.getMedfeeSumamt(), row.getMedfeeSumamt()));
        row.setFundPaySumamt(decimalOr(setlinfo.getFundPaySumamt(), BigDecimal.ZERO));
        row.setAcctPay(decimalOr(setlinfo.getAcctPay(), BigDecimal.ZERO));
        row.setPsnCashPay(decimalOr(setlinfo.getPsnCashPay(), row.getMedfeeSumamt()));
        row.setFulamtOwnpayAmt(setlinfo.getFulamtOwnpayAmt());
        row.setOverlmtSelfpay(setlinfo.getOverlmtSelfpay());
        row.setPreselfpayAmt(setlinfo.getPreselfpayAmt());
        row.setInscpScpAmt(setlinfo.getInscpScpAmt());
    }

    /**
     * 将上下文中的零售/上传分差写入住院预结算快照。
     *
     * @param row     预结算实体
     * @param retail  院内收费合计
     * @param upload  2301 上传合计
     * @param diffAmt 分差 retail−upload
     */
    public static void applyInpatientRetailUploadDiff(
        YbInpatientPreSettle row, BigDecimal retail, BigDecimal upload, BigDecimal diffAmt) {
        if (row == null) {
            return;
        }
        row.setRetailMedfeeSumamt(retail);
        row.setUploadMedfeeSumamt(upload);
        row.setRetailUploadDiffAmt(diffAmt);
    }

    /**
     * 从住院预结算快照重建 2304 所需的 2303 setlinfo 子集。
     *
     * @param pre 预结算快照
     * @return setlinfo 字段
     */
    public static PlatformSetlinfoFields build2303SetlinfoFor2304(YbInpatientPreSettle pre) {
        PlatformSetlinfoFields setlinfo = new PlatformSetlinfoFields();
        if (pre == null) {
            return setlinfo;
        }
        setlinfo.setFulamtOwnpayAmt(pre.getFulamtOwnpayAmt());
        setlinfo.setOverlmtSelfpay(pre.getOverlmtSelfpay());
        setlinfo.setPreselfpayAmt(pre.getPreselfpayAmt());
        setlinfo.setInscpScpAmt(pre.getInscpScpAmt());
        return setlinfo;
    }

    /**
     * 从预结算快照重建 2207 所需的 2206 setlinfo 子集。
     *
     * @param pre 预结算快照
     * @return setlinfo 字段
     */
    public static PlatformSetlinfoFields build2206SetlinfoFor2207(YbClinicPreSettle pre) {
        PlatformSetlinfoFields setlinfo = new PlatformSetlinfoFields();
        if (pre == null) {
            return setlinfo;
        }
        setlinfo.setFulamtOwnpayAmt(pre.getFulamtOwnpayAmt());
        setlinfo.setOverlmtSelfpay(pre.getOverlmtSelfpay());
        setlinfo.setPreselfpayAmt(pre.getPreselfpayAmt());
        setlinfo.setInscpScpAmt(pre.getInscpScpAmt());
        return setlinfo;
    }

    /**
     * 将 2207 setlinfo 写入正式结算回执实体。
     *
     * @param row            结算回执
     * @param pre            关联预结算
     * @param setlinfo       2207 输出 setlinfo
     * @param setldetailJson setldetail 数组 JSON 字符串
     */
    public static void applySettlementFields(
        YbSettlementRecord row,
        YbClinicPreSettle pre,
        PlatformSetlinfoFields setlinfo,
        String setldetailJson) {
        if (row == null || setlinfo == null) {
            return;
        }
        row.setSetlId(setlinfo.getSetlId());
        row.setMedfeeSumamt(decimalOr(setlinfo.getMedfeeSumamt(), fallback(pre, YbClinicPreSettle::getMedfeeSumamt)));
        row.setFundPaySumamt(decimalOr(setlinfo.getFundPaySumamt(), fallback(pre, YbClinicPreSettle::getFundPaySumamt)));
        row.setAcctPay(decimalOr(setlinfo.getAcctPay(), fallback(pre, YbClinicPreSettle::getAcctPay)));
        row.setPsnCashPay(decimalOr(setlinfo.getPsnCashPay(), fallback(pre, YbClinicPreSettle::getPsnCashPay)));
        // 2207 setlinfo 常不回传 insutype：回落预结算快照（读卡/预结算已存）
        row.setInsutype(firstNonEmpty(setlinfo.getInsutype(), pre == null ? null : pre.getInsutype()));
        row.setMedType(setlinfo.getMedType());
        row.setPsnName(setlinfo.getPsnName());
        row.setCvlservFlag(setlinfo.getCvlservFlag());
        row.setSetlTime(parseSetlTime(setlinfo.getSetlTime()));
        row.setFulamtOwnpayAmt(setlinfo.getFulamtOwnpayAmt());
        row.setOverlmtSelfpay(setlinfo.getOverlmtSelfpay());
        row.setPreselfpayAmt(setlinfo.getPreselfpayAmt());
        row.setInscpScpAmt(setlinfo.getInscpScpAmt());
        row.setHifpPay(setlinfo.getHifpPay());
        row.setCvlservPay(setlinfo.getCvlservPay());
        row.setHifmiPay(setlinfo.getHifmiPay());
        row.setHifobPay(setlinfo.getHifobPay());
        row.setHifesPay(setlinfo.getHifesPay());
        row.setPoolPropSelfpay(setlinfo.getPoolPropSelfpay());
        row.setMafPay(setlinfo.getMafPay());
        row.setOthPay(setlinfo.getOthPay());
        row.setPsnPartAmt(setlinfo.getPsnPartAmt());
        row.setHospPartAmt(setlinfo.getHospPartAmt());
        row.setBalc(setlinfo.getBalc());
        row.setAcctMulaidPay(setlinfo.getAcctMulaidPay());
        row.setPsnType(setlinfo.getPsnType());
        row.setActPayDedc(setlinfo.getActPayDedc());
        row.setHifdmPay(setlinfo.getHifdmPay());
        row.setExpContent(setlinfo.getExpContent());
        row.setMedinsSetlId(setlinfo.getMedinsSetlId());
        row.setClrOptins(setlinfo.getClrOptins());
        row.setClrWay(setlinfo.getClrWay());
        row.setClrType(setlinfo.getClrType());
        if (isNotEmpty(setldetailJson)) {
            row.setFundBreakdownJson(setldetailJson);
        }
        if (pre != null && pre.getAccountId() != null) {
            row.setAccountId(pre.getAccountId());
        }
        // Pre：零售/上传分差自预结算快照透传（非平台 setlinfo 字段）
        if (pre != null) {
            row.setRetailMedfeeSumamt(pre.getRetailMedfeeSumamt());
            row.setUploadMedfeeSumamt(pre.getUploadMedfeeSumamt());
            row.setRetailUploadDiffAmt(pre.getRetailUploadDiffAmt());
        }
    }

    /**
     * 将 2304 setlinfo 写入正式结算回执实体。
     *
     * @param row            结算回执
     * @param pre            关联预结算
     * @param setlinfo       2304 输出 setlinfo
     * @param setldetailJson setldetail 数组 JSON 字符串
     */
    public static void applyInpatientSettlementFields(
        YbSettlementRecord row,
        YbInpatientPreSettle pre,
        PlatformSetlinfoFields setlinfo,
        String setldetailJson) {
        if (row == null || setlinfo == null) {
            return;
        }
        row.setSetlId(setlinfo.getSetlId());
        row.setMedfeeSumamt(decimalOr(setlinfo.getMedfeeSumamt(), fallbackIp(pre, YbInpatientPreSettle::getMedfeeSumamt)));
        row.setFundPaySumamt(decimalOr(setlinfo.getFundPaySumamt(), fallbackIp(pre, YbInpatientPreSettle::getFundPaySumamt)));
        row.setAcctPay(decimalOr(setlinfo.getAcctPay(), fallbackIp(pre, YbInpatientPreSettle::getAcctPay)));
        row.setPsnCashPay(decimalOr(setlinfo.getPsnCashPay(), fallbackIp(pre, YbInpatientPreSettle::getPsnCashPay)));
        // 2304 setlinfo 常不回传 insutype：回落预结算快照
        row.setInsutype(firstNonEmpty(setlinfo.getInsutype(), pre == null ? null : pre.getInsutype()));
        row.setMedType(setlinfo.getMedType());
        row.setPsnName(setlinfo.getPsnName());
        row.setCvlservFlag(setlinfo.getCvlservFlag());
        row.setSetlTime(parseSetlTime(setlinfo.getSetlTime()));
        row.setFulamtOwnpayAmt(setlinfo.getFulamtOwnpayAmt());
        row.setOverlmtSelfpay(setlinfo.getOverlmtSelfpay());
        row.setPreselfpayAmt(setlinfo.getPreselfpayAmt());
        row.setInscpScpAmt(setlinfo.getInscpScpAmt());
        // 2304 常缺 hifp_pay：用基金总额 − 其它基金分项回落，保证博思统筹开票非空
        row.setHifpPay(resolveHifpPayOrFallback(setlinfo, pre));
        row.setCvlservPay(setlinfo.getCvlservPay());
        row.setHifmiPay(setlinfo.getHifmiPay());
        row.setHifobPay(setlinfo.getHifobPay());
        row.setHifesPay(setlinfo.getHifesPay());
        row.setPoolPropSelfpay(setlinfo.getPoolPropSelfpay());
        row.setMafPay(setlinfo.getMafPay());
        row.setOthPay(setlinfo.getOthPay());
        row.setPsnPartAmt(setlinfo.getPsnPartAmt());
        row.setHospPartAmt(setlinfo.getHospPartAmt());
        row.setBalc(setlinfo.getBalc());
        row.setAcctMulaidPay(setlinfo.getAcctMulaidPay());
        row.setPsnType(setlinfo.getPsnType());
        row.setActPayDedc(setlinfo.getActPayDedc());
        row.setHifdmPay(setlinfo.getHifdmPay());
        row.setExpContent(setlinfo.getExpContent());
        row.setMedinsSetlId(setlinfo.getMedinsSetlId());
        row.setClrOptins(setlinfo.getClrOptins());
        row.setClrWay(setlinfo.getClrWay());
        row.setClrType(setlinfo.getClrType());
        if (isNotEmpty(setldetailJson)) {
            row.setFundBreakdownJson(setldetailJson);
        }
        if (pre != null && pre.getAccountId() != null) {
            row.setAccountId(pre.getAccountId());
        }
        // Pre：零售/上传分差自预结算快照透传（非平台 setlinfo 字段）
        if (pre != null) {
            row.setRetailMedfeeSumamt(pre.getRetailMedfeeSumamt());
            row.setUploadMedfeeSumamt(pre.getUploadMedfeeSumamt());
            row.setRetailUploadDiffAmt(pre.getRetailUploadDiffAmt());
        }
    }

    /**
     * 解析基本统筹：优先 setlinfo.hifp_pay；缺省时用基金总额减去其它基金分项。
     *
     * @param setlinfo 2304 setlinfo
     * @param pre      预结算快照（基金总额回落）
     * @return 统筹金额；无法解析时 null
     */
    private static BigDecimal resolveHifpPayOrFallback(
        PlatformSetlinfoFields setlinfo,
        YbInpatientPreSettle pre) {
        if (setlinfo.getHifpPay() != null) {
            return setlinfo.getHifpPay();
        }
        BigDecimal fundSum = decimalOr(
            setlinfo.getFundPaySumamt(),
            fallbackIp(pre, YbInpatientPreSettle::getFundPaySumamt));
        if (fundSum == null) {
            return null;
        }
        BigDecimal other = nullToZeroBigDecimal(setlinfo.getCvlservPay())
            .add(nullToZeroBigDecimal(setlinfo.getHifesPay()))
            .add(nullToZeroBigDecimal(setlinfo.getHifmiPay()))
            .add(nullToZeroBigDecimal(setlinfo.getHifobPay()))
            .add(nullToZeroBigDecimal(setlinfo.getMafPay()))
            .add(nullToZeroBigDecimal(setlinfo.getHospPartAmt()))
            .add(nullToZeroBigDecimal(setlinfo.getOthPay()));
        BigDecimal hifp = fundSum.subtract(other);
        if (hifp.compareTo(BigDecimal.ZERO) < 0) {
            return fundSum;
        }
        return hifp;
    }

    private static BigDecimal nullToZeroBigDecimal(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value;
    }

    /**
     * 从 2207 平台响应提取 setldetail JSON 字符串。
     *
     * @param bodyOutput 平台 output 原文
     * @return setldetail JSON 数组字符串，或 null
     */
    public static String extractSetldetailJson(String bodyOutput) {
        if (isEmpty(bodyOutput)) {
            return null;
        }
        JSONObject output = JSON.parseObject(bodyOutput);
        if (output == null) {
            return null;
        }
        JSONArray setldetail = output.getJSONArray("setldetail");
        if (setldetail == null || setldetail.isEmpty()) {
            return null;
        }
        return setldetail.toJSONString();
    }

    /**
     * 从门诊预结算快照读取金额回落值。
     *
     * @param pre    预结算快照
     * @param getter 字段读取函数
     * @return 金额；pre 为空时 {@code null}
     */
    private static BigDecimal fallback(YbClinicPreSettle pre, java.util.function.Function<YbClinicPreSettle, BigDecimal> getter) {
        if (pre == null) {
            return null;
        }
        return getter.apply(pre);
    }

    /**
     * 从住院预结算快照读取金额回落值。
     *
     * @param pre    预结算快照
     * @param getter 字段读取函数
     * @return 金额；pre 为空时 {@code null}
     */
    private static BigDecimal fallbackIp(
        YbInpatientPreSettle pre,
        java.util.function.Function<YbInpatientPreSettle, BigDecimal> getter) {
        if (pre == null) {
            return null;
        }
        return getter.apply(pre);
    }

    /**
     * 取非空金额，否则回落 fallback。
     *
     * @param value    首选金额
     * @param fallback 回落金额
     * @return 金额
     */
    private static BigDecimal decimalOr(BigDecimal value, BigDecimal fallback) {
        if (value != null) {
            return value;
        }
        return fallback;
    }

    /**
     * 取首个非空字符串（trim 后）。
     */
    private static String firstNonEmpty(String primary, String fallback) {
        if (isNotEmpty(primary)) {
            return primary.trim();
        }
        if (isNotEmpty(fallback)) {
            return fallback.trim();
        }
        return null;
    }

    /**
     * 解析平台结算时间字符串。
     *
     * @param raw 平台原文字段（yyyy-MM-dd HH:mm:ss）
     * @return 解析结果；空或格式错误时 {@code null}
     */
    private static LocalDateTime parseSetlTime(String raw) {
        if (isEmpty(raw)) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw.trim(), SETL_TIME_FMT);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
