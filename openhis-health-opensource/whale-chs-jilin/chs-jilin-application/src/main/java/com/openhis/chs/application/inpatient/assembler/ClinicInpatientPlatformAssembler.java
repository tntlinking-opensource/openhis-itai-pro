/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.inpatient.assembler;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.application.config.ContractConfig;
import com.openhis.chs.domain.inpatient.dto.IpSettleChargeLine;
import com.openhis.chs.domain.inpatient.dto.IpSettleContext;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 住院 2401/2301/2402/2303/2304/2305 及 2404/2405 平台报文组装。 */
public final class ClinicInpatientPlatformAssembler {

    private static final DateTimeFormatter FEE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DSCG_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String DEFAULT_MED_TYPE = "21";
    private static final String DEFAULT_PSN_SETLWAY = "01";
    private static final String DEFAULT_ACCT_USED = "1";
    private static final String MID_SETL_YES = "1";
    private static final String MID_SETL_NO = "0";
    private static final String DEFAULT_DSCG_WAY = "1";

    private ClinicInpatientPlatformAssembler() {
    }

    /** 2401：mdtrtinfo + diseinfo。 */
    public static JSONObject assemble2401(IpSettleContext ctx) {
        JSONObject mdtrtinfo = new JSONObject();
        mdtrtinfo.put("psn_no", ctx.getPsnNo());
        mdtrtinfo.put("insutype", ctx.getInsutype());
        mdtrtinfo.put("begntime", formatTime(ctx.getAdmissionTime()));
        mdtrtinfo.put("mdtrt_cert_type", ctx.getMdtrtCertType());
        mdtrtinfo.put("mdtrt_cert_no", ctx.getMdtrtCertNo());
        mdtrtinfo.put("med_type", resolveMedType(ctx));
        mdtrtinfo.put("ipt_no", ctx.getEncounterBusNo());
        mdtrtinfo.put("atddr_no", blankToDash(ctx.getAtddrNo()));
        mdtrtinfo.put("chfpdr_name", blankToDash(ctx.getChfpdrName()));
        mdtrtinfo.put("adm_diag_dscr", blankToDash(ctx.getAdmDiagDscr()));
        mdtrtinfo.put("adm_dept_codg", blankToDash(ctx.getAdmDeptCodg()));
        mdtrtinfo.put("adm_dept_name", blankToDash(ctx.getAdmDeptName()));
        mdtrtinfo.put("adm_bed", blankToDash(ctx.getAdmBed()));
        mdtrtinfo.put("dscg_maindiag_code", blankToDash(ctx.getMainDiagCode()));
        mdtrtinfo.put("dscg_maindiag_name", blankToDash(ctx.getMainDiagName()));
        mdtrtinfo.put("insuplc_admdvs", ctx.getInsuplcAdmdvs());
        mdtrtinfo.put("mdtrtarea_admvs", ctx.getMdtrtareaAdmvs());

        JSONArray diseinfo = new JSONArray();
        diseinfo.add(buildDiseRow(ctx, "1", "1"));
        JSONObject input = new JSONObject();
        input.put("mdtrtinfo", mdtrtinfo);
        input.put("diseinfo", diseinfo);
        return input;
    }

    /** 2301：feedetail。 */
    public static JSONObject assemble2301(IpSettleContext ctx, String mdtrtId, List<IpSettleChargeLine> lines) {
        JSONArray feedetail = new JSONArray();
        for (IpSettleChargeLine line : lines) {
            JSONObject row = new JSONObject();
            row.put("feedetl_sn", line.getFeedetlSn());
            row.put("mdtrt_id", mdtrtId);
            row.put("psn_no", ctx.getPsnNo());
            row.put("med_type", resolveMedType(ctx));
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
            row.put("dscg_tkdrug_flag", "0");
            if (StringUtils.isNotBlank(line.getDrordNo())) {
                row.put("drord_no", line.getDrordNo());
            }
            feedetail.add(row);
        }
        JSONObject input = new JSONObject();
        input.put("feedetail", feedetail);
        return input;
    }

    /** 2402：dscginfo + diseinfo。 */
    public static JSONObject assemble2402(IpSettleContext ctx, String mdtrtId) {
        JSONObject dscginfo = new JSONObject();
        dscginfo.put("mdtrt_id", mdtrtId);
        dscginfo.put("psn_no", ctx.getPsnNo());
        dscginfo.put("insutype", ctx.getInsutype());
        dscginfo.put("endtime", formatTime(resolveDischargeTime(ctx)));
        dscginfo.put("dscg_dept_codg", blankToDash(ctx.getDscgDeptCodg()));
        dscginfo.put("dscg_dept_name", blankToDash(ctx.getDscgDeptName()));
        dscginfo.put("dscg_bed", blankToDash(ctx.getDscgBed()));
        dscginfo.put("dscg_way", resolveDscgWay(ctx));

        JSONArray diseinfo = new JSONArray();
        diseinfo.add(buildDiseRow(ctx, "2", "1"));

        JSONObject input = new JSONObject();
        input.put("dscginfo", dscginfo);
        input.put("diseinfo", diseinfo);
        return input;
    }

    /** 2303 input.data。 */
    public static JSONObject assemble2303(IpSettleContext ctx, String mdtrtId, BigDecimal medfeeSumamt) {
        JSONObject data = baseSettleData(ctx, mdtrtId, medfeeSumamt);
        data.put("mid_setl_flag", resolveMidSetlFlag(ctx));
        if (IpSettleContext.SETTLE_MODE_DISCHARGE_FULL.equals(ctx.getSettleMode())) {
            data.put("dscgTime", formatDscgDate(resolveDischargeTime(ctx)));
        } else {
            data.put("dscgTime", formatDscgDate(resolveBatchEndDate(ctx)));
        }
        JSONObject input = new JSONObject();
        input.put("data", data);
        return input;
    }

    /** 2304 input.data（2303 setlinfo 透传）。 */
    public static JSONObject assemble2304(
        IpSettleContext ctx,
        String mdtrtId,
        BigDecimal medfeeSumamt,
        JSONObject setlinfo) {
        JSONObject data = baseSettleData(ctx, mdtrtId, medfeeSumamt);
        data.put("mid_setl_flag", resolveMidSetlFlag(ctx));
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
        JSONObject input = new JSONObject();
        input.put("data", data);
        return input;
    }

    /** 2302：data 多行费用明细撤销。 */
    public static JSONObject assemble2302(String mdtrtId, String psnNo, List<String> feedetlSnList) {
        JSONArray data = new JSONArray();
        for (String feedetlSn : feedetlSnList) {
            JSONObject row = new JSONObject();
            row.put("feedetl_sn", feedetlSn);
            row.put("mdtrt_id", mdtrtId);
            row.put("psn_no", psnNo);
            data.add(row);
        }
        JSONObject input = new JSONObject();
        input.put("data", data);
        return input;
    }

    /** 2305：data 单行结算撤销。 */
    public static JSONObject assemble2305(String mdtrtId, String setlId, String psnNo) {
        JSONObject data = new JSONObject();
        data.put("mdtrt_id", mdtrtId);
        data.put("setl_id", setlId);
        data.put("psn_no", psnNo);
        JSONObject input = new JSONObject();
        input.put("data", data);
        return input;
    }

    /** 2403：adminfo + diseinfo（住院信息变更）。 */
    public static JSONObject assemble2403(IpSettleContext ctx, String mdtrtId) {
        JSONObject adminfo = new JSONObject();
        adminfo.put("mdtrt_id", mdtrtId);
        adminfo.put("psn_no", ctx.getPsnNo());
        adminfo.put("begntime", formatTime(ctx.getAdmissionTime()));
        adminfo.put("mdtrt_cert_type", ctx.getMdtrtCertType());
        adminfo.put("med_type", resolveMedType(ctx));
        adminfo.put("ipt_otp_no", ctx.getEncounterBusNo());
        adminfo.put("atddr_no", blankToDash(ctx.getAtddrNo()));
        adminfo.put("chfpdr_name", blankToDash(ctx.getChfpdrName()));
        adminfo.put("adm_diag_dscr", blankToDash(ctx.getAdmDiagDscr()));
        adminfo.put("adm_dept_codg", blankToDash(ctx.getAdmDeptCodg()));
        adminfo.put("adm_dept_name", blankToDash(ctx.getAdmDeptName()));
        adminfo.put("adm_bed", blankToDash(ctx.getAdmBed()));
        adminfo.put("dscg_maindiag_code", blankToDash(ctx.getMainDiagCode()));
        adminfo.put("dscg_maindiag_name", blankToDash(ctx.getMainDiagName()));

        JSONArray diseinfo = new JSONArray();
        JSONObject dise = buildDiseRow(ctx, "1", "1");
        dise.put("mdtrt_id", mdtrtId);
        diseinfo.add(dise);

        JSONObject input = new JSONObject();
        input.put("adminfo", adminfo);
        input.put("diseinfo", diseinfo);
        return input;
    }

    /** 2404/2405：data 单行入出院撤销。 */
    public static JSONObject assemble2404Or2405(String mdtrtId, String psnNo) {
        JSONObject data = new JSONObject();
        data.put("mdtrt_id", mdtrtId);
        data.put("psn_no", psnNo);
        JSONObject input = new JSONObject();
        input.put("data", data);
        return input;
    }

    public static String newSettlementRef(ContractConfig config, long seed) {
        String prefix = "IP";
        if (config != null && StringUtils.isNotBlank(config.getFixmedinsCode())) {
            String code = config.getFixmedinsCode().trim();
            if (code.length() > 6) {
                prefix = code.substring(0, 6);
            } else {
                prefix = code;
            }
        }
        String suffix = String.valueOf(Math.abs(seed) % 1_000_000L);
        while (suffix.length() < 6) {
            suffix = "0" + suffix;
        }
        return prefix + suffix;
    }

    private static JSONObject baseSettleData(IpSettleContext ctx, String mdtrtId, BigDecimal medfeeSumamt) {
        JSONObject data = new JSONObject();
        data.put("psn_no", ctx.getPsnNo());
        data.put("mdtrt_cert_type", ctx.getMdtrtCertType());
        data.put("mdtrt_cert_no", ctx.getMdtrtCertNo());
        data.put("medfee_sumamt", medfeeSumamt);
        data.put("psn_setlway", resolvePsnSetlway(ctx));
        data.put("mdtrt_id", mdtrtId);
        data.put("insutype", ctx.getInsutype());
        data.put("acct_used_flag", resolveAcctUsedFlag(ctx));
        data.put("insuplc_admdvs", ctx.getInsuplcAdmdvs());
        data.put("mdtrtarea_admvs", ctx.getMdtrtareaAdmvs());
        return data;
    }

    private static JSONObject buildDiseRow(IpSettleContext ctx, String diagType, String mainFlag) {
        JSONObject dise = new JSONObject();
        dise.put("mdtrt_id", "");
        dise.put("psn_no", ctx.getPsnNo());
        dise.put("diag_type", diagType);
        dise.put("maindiag_flag", mainFlag);
        dise.put("diag_srt_no", "1");
        dise.put("diag_code", blankToDash(ctx.getMainDiagCode()));
        dise.put("diag_name", blankToDash(ctx.getMainDiagName()));
        dise.put("diag_dept", blankToDash(ctx.getAdmDeptName()));
        dise.put("dise_dor_no", blankToDash(ctx.getAtddrNo()));
        dise.put("dise_dor_name", blankToDash(ctx.getChfpdrName()));
        dise.put("diag_time", formatTime(ctx.getAdmissionTime()));
        return dise;
    }

    private static String resolveMidSetlFlag(IpSettleContext ctx) {
        if (IpSettleContext.SETTLE_MODE_MID.equals(ctx.getSettleMode())) {
            return MID_SETL_YES;
        }
        return MID_SETL_NO;
    }

    private static String resolveMedType(IpSettleContext ctx) {
        if (StringUtils.isNotBlank(ctx.getMedType())) {
            return ctx.getMedType().trim();
        }
        return DEFAULT_MED_TYPE;
    }

    /** 解析住院 med_type（默认 21 普通住院）。 */
    public static String resolveMedTypeForContext(IpSettleContext ctx) {
        return resolveMedType(ctx);
    }

    private static String resolvePsnSetlway(IpSettleContext ctx) {
        if (StringUtils.isNotBlank(ctx.getPsnSetlway())) {
            return ctx.getPsnSetlway().trim();
        }
        return DEFAULT_PSN_SETLWAY;
    }

    private static String resolveAcctUsedFlag(IpSettleContext ctx) {
        if (StringUtils.isNotBlank(ctx.getAcctUsedFlag())) {
            return ctx.getAcctUsedFlag().trim();
        }
        return DEFAULT_ACCT_USED;
    }

    private static String resolveDscgWay(IpSettleContext ctx) {
        if (StringUtils.isNotBlank(ctx.getDscgWay())) {
            return ctx.getDscgWay().trim();
        }
        return DEFAULT_DSCG_WAY;
    }

    private static java.time.LocalDateTime resolveDischargeTime(IpSettleContext ctx) {
        if (ctx.getDischargeTime() != null) {
            return ctx.getDischargeTime();
        }
        return java.time.LocalDateTime.now();
    }

    private static java.time.LocalDateTime resolveBatchEndDate(IpSettleContext ctx) {
        java.time.LocalDateTime max = null;
        if (ctx.getChargeLines() != null) {
            for (IpSettleChargeLine line : ctx.getChargeLines()) {
                if (line.getFeeOcurTime() == null) {
                    continue;
                }
                if (max == null || line.getFeeOcurTime().isAfter(max)) {
                    max = line.getFeeOcurTime();
                }
            }
        }
        if (max != null) {
            return max;
        }
        return java.time.LocalDateTime.now();
    }

    private static void copyDecimal(JSONObject from, JSONObject to, String key) {
        if (from.containsKey(key)) {
            to.put(key, from.get(key));
        }
    }

    private static String formatTime(java.time.LocalDateTime time) {
        if (time == null) {
            return java.time.LocalDateTime.now().format(FEE_TIME);
        }
        return time.format(FEE_TIME);
    }

    private static String formatDscgDate(java.time.LocalDateTime time) {
        LocalDate date;
        if (time == null) {
            date = LocalDate.now();
        } else {
            date = time.toLocalDate();
        }
        return date.format(DSCG_DATE);
    }

    /** 2304/2402 追溯码计数：平台要求非负整数，null/0 传 0。 */
    private static int resolveTraceCount(Integer count) {
        if (count != null && count > 0) {
            return count;
        }
        return 0;
    }

    private static String blankToDash(String value) {
        if (StringUtils.isBlank(value)) {
            return "-";
        }
        return value.trim();
    }
}
