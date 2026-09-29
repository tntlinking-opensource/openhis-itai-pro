/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * MRMS 模块共享常量（跨 domain / application 只读引用）。
 */
public final class MrmsConstants {

    /** 定时任务/内部 Job 无登录态时使用的系统操作人占位（质控执行审计）。 */
    public static final long SYSTEM_OPERATOR_ID = 0L;

    /** EMR 入院记录文书类型 code（入院病情解析候选）。 */
    public static final String EMR_DOC_TYPE_ADMISSION_RECORD = "ADMISSION_RECORD";

    /** EMR 出院小结文书类型 code（入院病情解析候选）。 */
    public static final String EMR_DOC_TYPE_DISCHARGE_SUMMARY = "DISCHARGE_SUMMARY";

    /**
     * 费用快照键：医疗费总额（4401 medfee_sumamt）。
     *
     * <p>落库 {@code mrms_case_record_homepage.medfee_sumamt}；DTO.fees / 4401 / 卫统 / HQMS
     * 读快照。写入时机：首次空快照补种或「重新统计」。</p>
     */
    public static final String EXT_MEDFEE_SUMAMT = "medfee_sumamt";

    /**
     * 费用快照键：自付金额（4401 selfpay_amt）。
     *
     * <p>落库 {@code mrms_case_record_homepage.selfpay_amt}；口径同 {@link #EXT_MEDFEE_SUMAMT}。</p>
     */
    public static final String EXT_SELFPAY_AMT = "selfpay_amt";

    /** 病案结算类型术语码 → 4401 费用分项键（与 ter_concept 900010 序号 1～28 对齐；落库 fee_items_json）。 */
    public static final String[] MR_SETTLEMENT_TO_4401_FEE_KEY = {
        null,
        "ordn_med_servfee",
        "ordn_trt_oprt_fee",
        "nurs_fee",
        "com_med_serv_oth_fee",
        "palg_diag_fee",
        "lab_diag_fee",
        "rdhy_diag_fee",
        "clnc_dise_fee",
        "nsrgtrt_item_fee",
        "clnc_phys_trt_fee",
        "rgtrt_trt_fee",
        "anst_fee",
        "rgtrt_fee",
        "rhab_fee",
        "tcm_trt_fee",
        "wm_fee",
        "abtl_medn_fee",
        "tcmpat_fee",
        "tcmherb_fee",
        "blo_fee",
        "albu_fee",
        "glon_fee",
        "clotfac_fee",
        "cyki_fee",
        "exam_dspo_matl_fee",
        "trt_dspo_matl_fee",
        "oprn_dspo_matl_fee",
        "oth_fee"
    };

    /** 未映射病案结算类型时归入其他费（快照键，落库 fee_items_json）。 */
    public static final String FEE_KEY_OTH = "oth_fee";

    /**
     * 费用快照键全集（总额 + 自付 + 1～28 分项）；DTO.fees / 上报读快照。
     */
    public static final String[] FEE_LIVE_QUERY_KEYS = {
        EXT_MEDFEE_SUMAMT,
        EXT_SELFPAY_AMT,
        "ordn_med_servfee",
        "ordn_trt_oprt_fee",
        "nurs_fee",
        "com_med_serv_oth_fee",
        "palg_diag_fee",
        "lab_diag_fee",
        "rdhy_diag_fee",
        "clnc_dise_fee",
        "nsrgtrt_item_fee",
        "clnc_phys_trt_fee",
        "rgtrt_trt_fee",
        "anst_fee",
        "rgtrt_fee",
        "rhab_fee",
        "tcm_trt_fee",
        "wm_fee",
        "abtl_medn_fee",
        "tcmpat_fee",
        "tcmherb_fee",
        "blo_fee",
        "albu_fee",
        "glon_fee",
        "clotfac_fee",
        "cyki_fee",
        "exam_dspo_matl_fee",
        "trt_dspo_matl_fee",
        "oprn_dspo_matl_fee",
        FEE_KEY_OTH
    };

    /**
     * 病案列表筛选/行标记：否（是否出院、是否结算等 0/1 口径的「否」）。
     */
    public static final String LIST_FLAG_NO = "0";

    /**
     * 病案列表筛选/行标记：是（是否出院、是否结算等 0/1 口径的「是」）。
     */
    public static final String LIST_FLAG_YES = "1";

    /**
     * 列表行病案状态占位：未建档（前端可按「无档」展示）。
     */
    public static final String LIST_STATUS_CODE_NONE = "none";

    /** 4401 入院途径：急诊。 */
    public static final String ADM_WAY_EMERGENCY = "1";

    /** 4401 入院途径：门诊。 */
    public static final String ADM_WAY_OUTPATIENT = "2";

    /** 4401 入院途径：其他医疗机构转入。 */
    public static final String ADM_WAY_TRANSFER = "3";

    /** 4401 入院途径：其他。 */
    public static final String ADM_WAY_OTHER = "9";

    /** 病案首页付费方式：城镇职工基本医疗保险（国标 1～9，与前端 PAY_WAY_OPTIONS 同码）。 */
    public static final String PAY_WAY_EMPLOYEE = "1";

    /** 病案首页付费方式：城镇居民基本医疗保险。 */
    public static final String PAY_WAY_RESIDENT = "2";

    /** 病案首页付费方式：新型农村合作医疗。 */
    public static final String PAY_WAY_NRCM = "3";

    /** 病案首页付费方式：全自费。 */
    public static final String PAY_WAY_SELF_PAY = "7";

    /** 病案首页付费方式：其他社会保险。 */
    public static final String PAY_WAY_OTHER_SOCIAL = "8";

    /** 病案首页付费方式：其他。 */
    public static final String PAY_WAY_OTHER = "9";

    /** 平台险种：职工基本医疗。 */
    public static final String INSUTYPE_EMPLOYEE_BASIC = "310";

    /** 平台险种：公务员医疗补助（首页仍归职工）。 */
    public static final String INSUTYPE_EMPLOYEE_CIVIL = "320";

    /** 平台险种：大额医疗（首页仍归职工）。 */
    public static final String INSUTYPE_EMPLOYEE_LARGE = "330";

    /** 平台险种：离休人员医疗保障。 */
    public static final String INSUTYPE_RETIRED = "340";

    /** 平台险种：城乡居民基本医疗。 */
    public static final String INSUTYPE_RESIDENT = "390";

    /** 平台险种：新型农村合作医疗（遗留码）。 */
    public static final String INSUTYPE_NRCM = "392";

    private MrmsConstants() {
    }

    /**
     * 是否为合法 4401 入院途径码（1/2/3/9）。
     *
     * @param admWayCode 首页 adm_way_code
     * @return true 表示可用于上报与下拉回显
     */
    public static boolean isValidAdmWayCode(String admWayCode) {
        if (admWayCode == null || admWayCode.isBlank()) {
            return false;
        }
        String code = admWayCode.trim();
        if (ADM_WAY_EMERGENCY.equals(code)) {
            return true;
        }
        if (ADM_WAY_OUTPATIENT.equals(code)) {
            return true;
        }
        if (ADM_WAY_TRANSFER.equals(code)) {
            return true;
        }
        if (ADM_WAY_OTHER.equals(code)) {
            return true;
        }
        return false;
    }

    /**
     * 平台 {@code insutype} → 病案首页付费方式码（1～9）。
     *
     * <p>入院登记开医保户时已写入 {@code adm_account.insutype}；首页自动取值据此带入，
     * 避免病案室再手工点选。映射口径：职工系→1、居民→2、新农合→3、离休→8；未知非空→9。</p>
     *
     * @param insutype 平台险种码；可空
     * @return 首页 {@code pay_way_code}；空入参返回 null
     */
    public static String mapInsutypeToPayWayCode(String insutype) {
        if (insutype == null || insutype.isBlank()) {
            return null;
        }
        String code = insutype.trim();
        // 职工基本 / 公务员补助 / 大额：国标首页均归「城镇职工」
        if (INSUTYPE_EMPLOYEE_BASIC.equals(code)
                || INSUTYPE_EMPLOYEE_CIVIL.equals(code)
                || INSUTYPE_EMPLOYEE_LARGE.equals(code)) {
            return PAY_WAY_EMPLOYEE;
        }
        // 城乡居民基本医疗
        if (INSUTYPE_RESIDENT.equals(code)) {
            return PAY_WAY_RESIDENT;
        }
        // 新农合遗留码
        if (INSUTYPE_NRCM.equals(code)) {
            return PAY_WAY_NRCM;
        }
        // 离休等其他社保类
        if (INSUTYPE_RETIRED.equals(code)) {
            return PAY_WAY_OTHER_SOCIAL;
        }
        // 有险种但未列映射：归「其他」，避免空下拉
        return PAY_WAY_OTHER;
    }

    /**
     * 按就诊是否开立医保账户解析首页付费方式。
     *
     * <p>无医保户 → 全自费（7）；有医保户则按 {@link #mapInsutypeToPayWayCode}；
     * 有户但险种空 → null（待读卡补写或手工选）。</p>
     *
     * @param hasMedicalInsuranceAccount 是否存在有效医保账户
     * @param insutype                   医保户 {@code insutype}；可空
     * @return 首页付费方式码；无法判定时 null
     */
    public static String resolvePayWayCode(boolean hasMedicalInsuranceAccount, String insutype) {
        // 纯自费住院：登记仅开个人现金户
        if (!hasMedicalInsuranceAccount) {
            return PAY_WAY_SELF_PAY;
        }
        return mapInsutypeToPayWayCode(insutype);
    }

    /**
     * 住院登记 {@code in_way_code} → 4401 {@code adm_way}。
     *
     * <p>V28 起登记与病案首页同口径（1/2/3/9），合法码原样返回。
     * 遗留码兼容：4→3、5→9（迁移漏网）；登记「入院类型」{@code admit_source} 禁止直拷。</p>
     *
     * @param inWayCode 就诊 in_way_code；可空
     * @return 4401 adm_way code；无法映射时 null
     */
    public static String mapInWayCodeToAdmWayCode(String inWayCode) {
        if (inWayCode == null || inWayCode.isBlank()) {
            return null;
        }
        String code = inWayCode.trim();
        // 已与 adm_way 同码：原样回填
        if (isValidAdmWayCode(code)) {
            return code;
        }
        // 遗留：其它医疗机构转入
        if ("4".equals(code)) {
            return ADM_WAY_TRANSFER;
        }
        // 遗留：其它
        if ("5".equals(code)) {
            return ADM_WAY_OTHER;
        }
        return null;
    }

    /**
     * 病案结算类型术语码（1～28）→ 4401 费用分项快照键。
     *
     * @param mrSettlementTypeCode 术语 code
     * @return 快照键；无法识别时 {@link #FEE_KEY_OTH}
     */
    public static String to4401FeeExtensionKey(String mrSettlementTypeCode) {
        if (mrSettlementTypeCode == null || mrSettlementTypeCode.isBlank()) {
            return FEE_KEY_OTH;
        }
        String trimmed = mrSettlementTypeCode.trim();
        // 术语码为 1～28 的数字字符串；非法则归其他费
        for (int i = 1; i < MR_SETTLEMENT_TO_4401_FEE_KEY.length; i++) {
            if (String.valueOf(i).equals(trimmed)) {
                String key = MR_SETTLEMENT_TO_4401_FEE_KEY[i];
                if (key == null) {
                    return FEE_KEY_OTH;
                }
                return key;
            }
        }
        return FEE_KEY_OTH;
    }
}
