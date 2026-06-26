/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.outpatient.assembler;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.application.config.ContractConfig;
import com.openhis.chs.domain.outpatient.dto.OpSettleChargeLine;
import com.openhis.chs.domain.outpatient.dto.OpSettleContext;
import cn.hutool.core.date.DateUtil;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** 门诊 2201/2203/2204/2206/2207 及 1101/2001/2205/2208/2601 平台报文 data 节点组装。 */
public final class ClinicOutpatientPlatformAssembler {

    private static final DateTimeFormatter FEE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String DEFAULT_MED_TYPE = "11";
    private static final String DEFAULT_PSN_SETLWAY = "01";
    private static final String DEFAULT_ACCT_USED = "1";

    private ClinicOutpatientPlatformAssembler() {
    }

    /** 2201 门诊挂号 input.data。 */
    public static JSONObject assemble2201(OpSettleContext ctx) {
        JSONObject data = new JSONObject();
        data.put("psn_no", ctx.getPsnNo());
        data.put("insutype", ctx.getInsutype());
        data.put("begntime", formatTime(ctx.getEncounterStartTime()));
        data.put("mdtrt_cert_type", ctx.getMdtrtCertType());
        data.put("mdtrt_cert_no", ctx.getMdtrtCertNo());
        data.put("ipt_otp_no", ctx.getEncounterBusNo());
        data.put("atddr_no", blankToDash(ctx.getAtddrNo()));
        data.put("dr_name", blankToDash(ctx.getDrName()));
        data.put("dept_code", blankToDash(ctx.getDeptCode()));
        data.put("dept_name", blankToDash(ctx.getDeptName()));
        data.put("caty", blankToDash(ctx.getCaty()));
        JSONObject input = new JSONObject();
        input.put("data", data);
        return input;
    }

    /** 2202 门诊挂号撤销 input.data。 */
    public static JSONObject assemble2202(String psnNo, String mdtrtId, String iptOtpNo) {
        JSONObject data = new JSONObject();
        data.put("psn_no", psnNo);
        data.put("mdtrt_id", mdtrtId);
        data.put("ipt_otp_no", iptOtpNo);
        JSONObject input = new JSONObject();
        input.put("data", data);
        return input;
    }

    /** 2203 input：mdtrtinfo + diseinfo。 */
    public static JSONObject assemble2203(OpSettleContext ctx, String mdtrtId) {
        JSONObject mdtrtinfo = new JSONObject();
        mdtrtinfo.put("mdtrt_id", mdtrtId);
        mdtrtinfo.put("psn_no", ctx.getPsnNo());
        mdtrtinfo.put("med_type", resolveMedType(ctx));
        mdtrtinfo.put("begntime", formatTime(ctx.getEncounterStartTime()));
        mdtrtinfo.put("main_cond_dscr", blankToDash(ctx.getMainDiagName()));

        JSONObject dise = new JSONObject();
        dise.put("diag_type", "1");
        dise.put("diag_srt_no", "1");
        dise.put("diag_code", blankToDash(ctx.getMainDiagCode()));
        dise.put("diag_name", blankToDash(ctx.getMainDiagName()));
        dise.put("diag_dept", blankToDash(ctx.getDeptCode()));
        dise.put("dise_dor_no", blankToDash(ctx.getAtddrNo()));
        dise.put("dise_dor_name", blankToDash(ctx.getDrName()));
        dise.put("diag_time", formatTime(ctx.getEncounterStartTime()));

        JSONArray diseinfo = new JSONArray();
        diseinfo.add(dise);

        JSONObject input = new JSONObject();
        input.put("mdtrtinfo", mdtrtinfo);
        input.put("diseinfo", diseinfo);
        return input;
    }

    /** 2204 input.feedetail。 */
    public static JSONObject assemble2204(OpSettleContext ctx, String mdtrtId, String chrgBchno) {
        JSONArray feedetail = new JSONArray();
        for (OpSettleChargeLine line : ctx.getChargeLines()) {
            JSONObject row = new JSONObject();
            row.put("feedetl_sn", line.getFeedetlSn());
            row.put("mdtrt_id", mdtrtId);
            row.put("psn_no", ctx.getPsnNo());
            row.put("chrg_bchno", chrgBchno);
            if (StringUtils.isNotBlank(line.getDiseCodg())) {
                row.put("dise_codg", line.getDiseCodg());
            }
            if (StringUtils.isNotBlank(line.getRxno())) {
                row.put("rxno", line.getRxno());
            }
            row.put("rx_circ_flag", "0");
            row.put("fee_ocur_time", line.getFeeOcurTime().format(FEE_TIME));
            row.put("med_list_codg", line.getMedListCodg());
            row.put("medins_list_codg", line.getMedinsListCodg());
            row.put("det_item_fee_sumamt", line.getDetItemFeeSumamt());
            row.put("cnt", line.getCnt());
            row.put("pric", line.getPric());
            row.put("bilg_dept_codg", blankToDash(line.getBilgDeptCodg()));
            row.put("bilg_dept_name", blankToDash(line.getBilgDeptName()));
            row.put("bilg_dr_codg", blankToDash(line.getBilgDrCodg()));
            row.put("bilg_dr_name", blankToDash(line.getBilgDrName()));
            row.put("hosp_appr_flag", "1");
            feedetail.add(row);
        }
        JSONObject input = new JSONObject();
        input.put("feedetail", feedetail);
        input.put("insuplc_admdvs", ctx.getInsuplcAdmdvs());
        return input;
    }

    /** 2206 input.data。 */
    public static JSONObject assemble2206(OpSettleContext ctx, String mdtrtId, String chrgBchno, BigDecimal medfeeSumamt) {
        JSONObject data = new JSONObject();
        data.put("psn_no", ctx.getPsnNo());
        data.put("mdtrt_cert_type", ctx.getMdtrtCertType());
        data.put("mdtrt_cert_no", ctx.getMdtrtCertNo());
        data.put("med_type", resolveMedType(ctx));
        data.put("medfee_sumamt", medfeeSumamt);
        data.put("psn_setlway", resolvePsnSetlway(ctx));
        data.put("mdtrt_id", mdtrtId);
        data.put("chrg_bchno", chrgBchno);
        data.put("insutype", ctx.getInsutype());
        data.put("acct_used_flag", resolveAcctUsedFlag(ctx));
        data.put("insuplc_admdvs", ctx.getInsuplcAdmdvs());
        return data;
    }

    /** 2207 input.data（2206 setlinfo 金额字段透传）。 */
    public static JSONObject assemble2207(
        OpSettleContext ctx,
        String mdtrtId,
        String chrgBchno,
        BigDecimal medfeeSumamt,
        JSONObject setlinfo) {
        JSONObject data = assemble2206(ctx, mdtrtId, chrgBchno, medfeeSumamt);
        if (setlinfo != null) {
            copyDecimal(setlinfo, data, "fulamt_ownpay_amt");
            copyDecimal(setlinfo, data, "overlmt_selfpay");
            copyDecimal(setlinfo, data, "preselfpay_amt");
            copyDecimal(setlinfo, data, "inscp_scp_amt");
        }
        if (StringUtils.isNotBlank(ctx.getInvono())) {
            data.put("invono", ctx.getInvono().trim());
        }
        data.put("minpacunt_drug_trac_cnt", resolveTraceCount(ctx.getMinpacuntDrugTracCnt()));
        data.put("mcs_trac_cnt", resolveTraceCount(ctx.getMcsTracCnt()));
        return data;
    }

    /** 2207 追溯码计数：平台要求非负整数，null/0 传 0。 */
    private static int resolveTraceCount(Integer count) {
        if (count != null && count > 0) {
            return count;
        }
        return 0;
    }

    /** 1101 input.data。 */
    public static JSONObject assemble1101(String mdtrtCertType, String mdtrtCertNo) {
        return assemble1101(mdtrtCertType, mdtrtCertNo, null, null, null);
    }

    /** 1101 input.data（社保卡需 card_sn / certno；电子凭证 01 需 certno / psn_name）。 */
    public static JSONObject assemble1101(
        String mdtrtCertType,
        String mdtrtCertNo,
        String cardSn,
        String psnCertType,
        String certno) {
        return assemble1101(mdtrtCertType, mdtrtCertNo, cardSn, psnCertType, certno, null);
    }

    /** 1101 input.data（电子凭证 01 时 psnName 在吉林平台必填）。 */
    public static JSONObject assemble1101(
        String mdtrtCertType,
        String mdtrtCertNo,
        String cardSn,
        String psnCertType,
        String certno,
        String psnName) {
        JSONObject data = new JSONObject();
        data.put("mdtrt_cert_type", mdtrtCertType);
        data.put("mdtrt_cert_no", mdtrtCertNo);
        if ("03".equals(mdtrtCertType)) {
            data.put("card_sn", cardSn != null ? cardSn.trim() : "");
            if (StringUtils.isNotBlank(certno)) {
                data.put("certno", certno.trim());
            }
            String resolvedPsnCertType = StringUtils.isNotBlank(psnCertType) ? psnCertType.trim() : "01";
            data.put("psn_cert_type", resolvedPsnCertType);
        } else if ("02".equals(mdtrtCertType)) {
            if (StringUtils.isNotBlank(certno)) {
                data.put("certno", certno.trim());
            }
            String resolvedPsnCertType = StringUtils.isNotBlank(psnCertType) ? psnCertType.trim() : "01";
            data.put("psn_cert_type", resolvedPsnCertType);
        } else if ("01".equals(mdtrtCertType)) {
            String resolvedPsnCertType = StringUtils.isNotBlank(psnCertType) ? psnCertType.trim() : "01";
            data.put("psn_cert_type", resolvedPsnCertType);
            if (StringUtils.isNotBlank(certno)) {
                data.put("certno", certno.trim());
            }
            if (StringUtils.isNotBlank(psnName)) {
                data.put("psn_name", psnName.trim());
            }
        }
        JSONObject input = new JSONObject();
        input.put("data", data);
        return input;
    }

    /** 2001 input.data：人员待遇享受检查。 */
    public static JSONObject assemble2001(
        String psnNo,
        String insutype,
        String fixmedinsCode,
        String medType,
        String begntime) {
        JSONObject data = new JSONObject();
        data.put("psn_no", psnNo);
        data.put("insutype", insutype);
        data.put("fixmedins_code", fixmedinsCode);
        String resolvedMedType = DEFAULT_MED_TYPE;
        if (StringUtils.isNotBlank(medType)) {
            resolvedMedType = medType;
        }
        data.put("med_type", resolvedMedType);
        data.put("begntime", begntime);
        JSONObject input = new JSONObject();
        input.put("data", data);
        return input;
    }

    /** 2205 input.data：撤销未结算费用明细。 */
    public static JSONObject assemble2205(String mdtrtId, String chrgBchno, String psnNo) {
        JSONObject data = new JSONObject();
        data.put("mdtrt_id", mdtrtId);
        data.put("chrg_bchno", chrgBchno);
        data.put("psn_no", psnNo);
        JSONObject input = new JSONObject();
        input.put("data", data);
        return input;
    }

    /** 2208 input.data：撤销门诊正式结算。 */
    public static JSONObject assemble2208(String mdtrtId, String setlId, String psnNo) {
        JSONObject data = new JSONObject();
        data.put("mdtrt_id", mdtrtId);
        data.put("setl_id", setlId);
        data.put("psn_no", psnNo);
        JSONObject input = new JSONObject();
        input.put("data", data);
        return input;
    }

    /** 2601 input.data：冲正原交易（omsgid + oinfno）。 */
    public static JSONObject assemble2601(String psnNo, String omsgid, String oinfno) {
        JSONObject data = new JSONObject();
        data.put("psn_no", psnNo);
        data.put("omsgid", omsgid);
        data.put("oinfno", oinfno);
        JSONObject input = new JSONObject();
        input.put("data", data);
        return input;
    }

    /** 生成收费批次号（≤30 字符）。 */
    public static String newChrgBchno(ContractConfig config, long seed) {
        String prefix = "OP";
        if (config != null && StringUtils.isNotBlank(config.getFixmedinsCode())) {
            String code = config.getFixmedinsCode().trim();
            if (code.length() > 8) {
                prefix = code.substring(0, 8);
            } else {
                prefix = code;
            }
        }
        String day = DateUtil.format(DateUtil.date(), "yyyyMMdd");
        String suffix = String.valueOf(Math.abs(seed) % 1_000_000L);
        while (suffix.length() < 6) {
            suffix = "0" + suffix;
        }
        return prefix + day + suffix;
    }

    private static void copyDecimal(JSONObject from, JSONObject to, String key) {
        if (from.containsKey(key)) {
            to.put(key, from.get(key));
        }
    }

    private static String resolveMedType(OpSettleContext ctx) {
        if (StringUtils.isNotBlank(ctx.getMedType())) {
            return ctx.getMedType().trim();
        }
        return DEFAULT_MED_TYPE;
    }

    /** 解析门诊 med_type（默认 11 普通门诊）。 */
    public static String resolveMedTypeForContext(OpSettleContext ctx) {
        return resolveMedType(ctx);
    }

    /** 缺省 med_type=11（普通门诊）。 */
    public static String resolveMedTypeForCommand(String medType) {
        if (StringUtils.isNotBlank(medType)) {
            return medType.trim();
        }
        return DEFAULT_MED_TYPE;
    }

    private static String resolvePsnSetlway(OpSettleContext ctx) {
        if (StringUtils.isNotBlank(ctx.getPsnSetlway())) {
            return ctx.getPsnSetlway().trim();
        }
        return DEFAULT_PSN_SETLWAY;
    }

    private static String resolveAcctUsedFlag(OpSettleContext ctx) {
        if (StringUtils.isNotBlank(ctx.getAcctUsedFlag())) {
            return ctx.getAcctUsedFlag().trim();
        }
        return DEFAULT_ACCT_USED;
    }

    private static String formatTime(java.time.LocalDateTime time) {
        if (time == null) {
            return DateUtil.now();
        }
        return time.format(FEE_TIME);
    }

    private static String blankToDash(String value) {
        if (StringUtils.isBlank(value)) {
            return "-";
        }
        return value.trim();
    }
}
