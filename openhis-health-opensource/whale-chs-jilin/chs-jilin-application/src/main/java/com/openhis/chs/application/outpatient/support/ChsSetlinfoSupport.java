/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.outpatient.support;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.domain.inpatient.entity.YbInpatientPreSettle;
import com.openhis.chs.domain.outpatient.entity.YbClinicPreSettle;
import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 2206/2207 {@code setlinfo} 与领域实体字段映射。 */
public final class ChsSetlinfoSupport {

    private static final DateTimeFormatter SETL_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ChsSetlinfoSupport() {
    }

    /**
     * 将 2206 setlinfo 金额字段写入预结算快照（供 2207 透传）。
     *
     * @param row      预结算实体
     * @param setlinfo 2206 输出 setlinfo
     */
    public static void applyPreSettleAmountFields(YbClinicPreSettle row, JSONObject setlinfo) {
        if (row == null || setlinfo == null) {
            return;
        }
        row.setMedfeeSumamt(decimal(setlinfo, "medfee_sumamt", row.getMedfeeSumamt()));
        row.setFundPaySumamt(decimal(setlinfo, "fund_pay_sumamt", BigDecimal.ZERO));
        row.setAcctPay(decimal(setlinfo, "acct_pay", BigDecimal.ZERO));
        row.setPsnCashPay(decimal(setlinfo, "psn_cash_pay", row.getMedfeeSumamt()));
        row.setFulamtOwnpayAmt(decimal(setlinfo, "fulamt_ownpay_amt", null));
        row.setOverlmtSelfpay(decimal(setlinfo, "overlmt_selfpay", null));
        row.setPreselfpayAmt(decimal(setlinfo, "preselfpay_amt", null));
        row.setInscpScpAmt(decimal(setlinfo, "inscp_scp_amt", null));
    }

    /**
     * 将 2303 setlinfo 金额字段写入住院预结算快照。
     *
     * @param row      预结算实体
     * @param setlinfo 2303 输出 setlinfo
     */
    public static void applyInpatientPreSettleAmountFields(YbInpatientPreSettle row, JSONObject setlinfo) {
        if (row == null || setlinfo == null) {
            return;
        }
        row.setMedfeeSumamt(decimal(setlinfo, "medfee_sumamt", row.getMedfeeSumamt()));
        row.setFundPaySumamt(decimal(setlinfo, "fund_pay_sumamt", BigDecimal.ZERO));
        row.setAcctPay(decimal(setlinfo, "acct_pay", BigDecimal.ZERO));
        row.setPsnCashPay(decimal(setlinfo, "psn_cash_pay", row.getMedfeeSumamt()));
        row.setFulamtOwnpayAmt(decimal(setlinfo, "fulamt_ownpay_amt", null));
        row.setOverlmtSelfpay(decimal(setlinfo, "overlmt_selfpay", null));
        row.setPreselfpayAmt(decimal(setlinfo, "preselfpay_amt", null));
        row.setInscpScpAmt(decimal(setlinfo, "inscp_scp_amt", null));
    }

    /**
     * 从住院预结算快照重建 2304 所需的 2303 setlinfo 子集。
     *
     * @param pre 预结算快照
     * @return setlinfo JSON
     */
    public static JSONObject build2303SetlinfoFor2304(YbInpatientPreSettle pre) {
        JSONObject setlinfo = new JSONObject();
        if (pre == null) {
            return setlinfo;
        }
        putDecimal(setlinfo, "fulamt_ownpay_amt", pre.getFulamtOwnpayAmt());
        putDecimal(setlinfo, "overlmt_selfpay", pre.getOverlmtSelfpay());
        putDecimal(setlinfo, "preselfpay_amt", pre.getPreselfpayAmt());
        putDecimal(setlinfo, "inscp_scp_amt", pre.getInscpScpAmt());
        return setlinfo;
    }

    /**
     * 从预结算快照重建 2207 所需的 2206 setlinfo 子集。
     *
     * @param pre 预结算快照
     * @return setlinfo JSON
     */
    public static JSONObject build2206SetlinfoFor2207(YbClinicPreSettle pre) {
        JSONObject setlinfo = new JSONObject();
        if (pre == null) {
            return setlinfo;
        }
        putDecimal(setlinfo, "fulamt_ownpay_amt", pre.getFulamtOwnpayAmt());
        putDecimal(setlinfo, "overlmt_selfpay", pre.getOverlmtSelfpay());
        putDecimal(setlinfo, "preselfpay_amt", pre.getPreselfpayAmt());
        putDecimal(setlinfo, "inscp_scp_amt", pre.getInscpScpAmt());
        return setlinfo;
    }

    /**
     * 将 2207 setlinfo 写入正式结算回执实体。
     *
     * @param row      结算回执
     * @param pre      关联预结算
     * @param setlinfo 2207 输出 setlinfo
     * @param setldetailJson setldetail 数组 JSON 字符串
     */
    public static void applySettlementFields(
        YbSettlementRecord row,
        YbClinicPreSettle pre,
        JSONObject setlinfo,
        String setldetailJson) {
        if (row == null || setlinfo == null) {
            return;
        }
        row.setSetlId(text(setlinfo, "setl_id"));
        row.setMedfeeSumamt(decimal(setlinfo, "medfee_sumamt", fallback(pre, YbClinicPreSettle::getMedfeeSumamt)));
        row.setFundPaySumamt(decimal(setlinfo, "fund_pay_sumamt", fallback(pre, YbClinicPreSettle::getFundPaySumamt)));
        row.setAcctPay(decimal(setlinfo, "acct_pay", fallback(pre, YbClinicPreSettle::getAcctPay)));
        row.setPsnCashPay(decimal(setlinfo, "psn_cash_pay", fallback(pre, YbClinicPreSettle::getPsnCashPay)));
        row.setInsutype(text(setlinfo, "insutype"));
        row.setMedType(text(setlinfo, "med_type"));
        row.setPsnName(text(setlinfo, "psn_name"));
        row.setCvlservFlag(text(setlinfo, "cvlserv_flag"));
        row.setSetlTime(parseSetlTime(text(setlinfo, "setl_time")));
        row.setFulamtOwnpayAmt(decimal(setlinfo, "fulamt_ownpay_amt", null));
        row.setOverlmtSelfpay(decimal(setlinfo, "overlmt_selfpay", null));
        row.setPreselfpayAmt(decimal(setlinfo, "preselfpay_amt", null));
        row.setInscpScpAmt(decimal(setlinfo, "inscp_scp_amt", null));
        row.setHifpPay(decimal(setlinfo, "hifp_pay", null));
        row.setCvlservPay(decimal(setlinfo, "cvlserv_pay", null));
        row.setHifmiPay(decimal(setlinfo, "hifmi_pay", null));
        row.setHifobPay(decimal(setlinfo, "hifob_pay", null));
        row.setHifesPay(decimal(setlinfo, "hifes_pay", null));
        row.setPoolPropSelfpay(decimal(setlinfo, "pool_prop_selfpay", null));
        row.setMafPay(decimal(setlinfo, "maf_pay", null));
        row.setOthPay(decimal(setlinfo, "oth_pay", null));
        row.setPsnPartAmt(decimal(setlinfo, "psn_part_amt", null));
        row.setHospPartAmt(decimal(setlinfo, "hosp_part_amt", null));
        row.setBalc(decimal(setlinfo, "balc", null));
        row.setAcctMulaidPay(decimal(setlinfo, "acct_mulaid_pay", null));
        row.setPsnType(text(setlinfo, "psn_type"));
        row.setActPayDedc(decimal(setlinfo, "act_pay_dedc", null));
        row.setHifdmPay(decimal(setlinfo, "hifdm_pay", null));
        row.setExpContent(text(setlinfo, "exp_content"));
        row.setMedinsSetlId(text(setlinfo, "medins_setl_id"));
        row.setClrOptins(text(setlinfo, "clr_optins"));
        row.setClrWay(text(setlinfo, "clr_way"));
        row.setClrType(text(setlinfo, "clr_type"));
        if (StringUtils.isNotBlank(setldetailJson)) {
            row.setFundBreakdownJson(setldetailJson);
        }
        if (pre != null && pre.getAccountId() != null) {
            row.setAccountId(pre.getAccountId());
        }
    }

    /**
     * 将 2304 setlinfo 写入正式结算回执实体。
     *
     * @param row      结算回执
     * @param pre      关联预结算
     * @param setlinfo 2304 输出 setlinfo
     * @param setldetailJson setldetail 数组 JSON 字符串
     */
    public static void applyInpatientSettlementFields(
        YbSettlementRecord row,
        YbInpatientPreSettle pre,
        JSONObject setlinfo,
        String setldetailJson) {
        if (row == null || setlinfo == null) {
            return;
        }
        row.setSetlId(text(setlinfo, "setl_id"));
        row.setMedfeeSumamt(decimal(setlinfo, "medfee_sumamt", fallbackIp(pre, YbInpatientPreSettle::getMedfeeSumamt)));
        row.setFundPaySumamt(decimal(setlinfo, "fund_pay_sumamt", fallbackIp(pre, YbInpatientPreSettle::getFundPaySumamt)));
        row.setAcctPay(decimal(setlinfo, "acct_pay", fallbackIp(pre, YbInpatientPreSettle::getAcctPay)));
        row.setPsnCashPay(decimal(setlinfo, "psn_cash_pay", fallbackIp(pre, YbInpatientPreSettle::getPsnCashPay)));
        row.setInsutype(text(setlinfo, "insutype"));
        row.setMedType(text(setlinfo, "med_type"));
        row.setPsnName(text(setlinfo, "psn_name"));
        row.setCvlservFlag(text(setlinfo, "cvlserv_flag"));
        row.setSetlTime(parseSetlTime(text(setlinfo, "setl_time")));
        row.setFulamtOwnpayAmt(decimal(setlinfo, "fulamt_ownpay_amt", null));
        row.setOverlmtSelfpay(decimal(setlinfo, "overlmt_selfpay", null));
        row.setPreselfpayAmt(decimal(setlinfo, "preselfpay_amt", null));
        row.setInscpScpAmt(decimal(setlinfo, "inscp_scp_amt", null));
        row.setHifpPay(decimal(setlinfo, "hifp_pay", null));
        row.setCvlservPay(decimal(setlinfo, "cvlserv_pay", null));
        row.setHifmiPay(decimal(setlinfo, "hifmi_pay", null));
        row.setHifobPay(decimal(setlinfo, "hifob_pay", null));
        row.setHifesPay(decimal(setlinfo, "hifes_pay", null));
        row.setPoolPropSelfpay(decimal(setlinfo, "pool_prop_selfpay", null));
        row.setMafPay(decimal(setlinfo, "maf_pay", null));
        row.setOthPay(decimal(setlinfo, "oth_pay", null));
        row.setPsnPartAmt(decimal(setlinfo, "psn_part_amt", null));
        row.setHospPartAmt(decimal(setlinfo, "hosp_part_amt", null));
        row.setBalc(decimal(setlinfo, "balc", null));
        row.setAcctMulaidPay(decimal(setlinfo, "acct_mulaid_pay", null));
        row.setPsnType(text(setlinfo, "psn_type"));
        row.setActPayDedc(decimal(setlinfo, "act_pay_dedc", null));
        row.setHifdmPay(decimal(setlinfo, "hifdm_pay", null));
        row.setExpContent(text(setlinfo, "exp_content"));
        row.setMedinsSetlId(text(setlinfo, "medins_setl_id"));
        row.setClrOptins(text(setlinfo, "clr_optins"));
        row.setClrWay(text(setlinfo, "clr_way"));
        row.setClrType(text(setlinfo, "clr_type"));
        if (StringUtils.isNotBlank(setldetailJson)) {
            row.setFundBreakdownJson(setldetailJson);
        }
        if (pre != null && pre.getAccountId() != null) {
            row.setAccountId(pre.getAccountId());
        }
    }

    /**
     * 从 2207 平台响应提取 setldetail JSON 字符串。
     *
     * @param bodyOutput 平台 output 原文
     * @return setldetail JSON 数组字符串，无则 null
     */
    public static String extractSetldetailJson(String bodyOutput) {
        if (StringUtils.isBlank(bodyOutput)) {
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

    private static BigDecimal fallback(YbClinicPreSettle pre, java.util.function.Function<YbClinicPreSettle, BigDecimal> getter) {
        if (pre == null) {
            return null;
        }
        return getter.apply(pre);
    }

    private static BigDecimal fallbackIp(
        YbInpatientPreSettle pre,
        java.util.function.Function<YbInpatientPreSettle, BigDecimal> getter) {
        if (pre == null) {
            return null;
        }
        return getter.apply(pre);
    }

    private static void putDecimal(JSONObject json, String key, BigDecimal value) {
        if (value != null) {
            json.put(key, value);
        }
    }

    private static LocalDateTime parseSetlTime(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw.trim(), SETL_TIME_FMT);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public static BigDecimal decimal(JSONObject json, String key, BigDecimal fallback) {
        if (json == null || !json.containsKey(key) || json.get(key) == null) {
            return fallback;
        }
        return new BigDecimal(String.valueOf(json.get(key)));
    }

    public static String text(JSONObject json, String key) {
        if (json == null) {
            return null;
        }
        return json.getString(key);
    }
}
