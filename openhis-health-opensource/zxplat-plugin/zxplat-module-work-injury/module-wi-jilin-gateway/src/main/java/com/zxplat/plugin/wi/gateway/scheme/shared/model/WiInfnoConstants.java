/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.gateway.scheme.shared.model;

import java.util.Map;

/**
 * 吉林工伤 WORK_INJURY 交易编号常量（P0 infno）。
 *
 * <p>infno 链编排见 {@code WorkInjurySettleStepPlanner}；禁止 AppService 内 magic string。</p>
 */
public final class WiInfnoConstants {

    /** DLL 签到（Win 侧，ZXPlat 不缓存 sign_no）。 */
    public static final String SIGN_IN = "9001";
    /** DLL 签退。 */
    public static final String SIGN_OUT = "9002";
    /** 人员信息查询（REG_QUERY 首步；写 anchor.psn_no）。 */
    public static final String PERSON_INFO = "1101";
    /** 工伤资格/审批信息查询。 */
    public static final String QUALIFICATION_QUERY = "9102";
    /** 工伤审批信息上传。 */
    public static final String APPROVAL_UPLOAD = "2301";
    /** 门诊登记。 */
    public static final String OUTPATIENT_REG = "2201";
    /** 门诊登记撤销。 */
    public static final String OUTPATIENT_REG_REVOKE = "2202";
    /** 门诊就诊信息上传。 */
    public static final String OUTPATIENT_VISIT_UPLOAD = "2203";
    /** 门诊费用明细上传。 */
    public static final String OUTPATIENT_FEE_UPLOAD = "2204";
    /** 门诊费用明细撤销（条件步）。 */
    public static final String OUTPATIENT_FEE_REVOKE = "2205";
    /** 门诊预结算。 */
    public static final String OUTPATIENT_PRE_SETTLE = "2206";
    /** 门诊结算。 */
    public static final String OUTPATIENT_SETTLE = "2207";
    /** 门诊结算撤销。 */
    public static final String OUTPATIENT_SETTLE_REVOKE = "2208";
    /** 总额对账。 */
    public static final String RECON_SUMMARY = "1320";
    /** 明细对账。 */
    public static final String RECON_DETAIL = "1321";
    /** 对账确认。 */
    public static final String RECON_CONFIRM = "1322";

    private static final Map<String, String> DISPLAY_NAMES = Map.ofEntries(
            Map.entry(SIGN_IN, "签到"),
            Map.entry(SIGN_OUT, "签退"),
            Map.entry(PERSON_INFO, "人员信息查询"),
            Map.entry(QUALIFICATION_QUERY, "工伤资格查询"),
            Map.entry(APPROVAL_UPLOAD, "工伤审批上传"),
            Map.entry(OUTPATIENT_REG, "门诊登记"),
            Map.entry(OUTPATIENT_REG_REVOKE, "门诊登记撤销"),
            Map.entry(OUTPATIENT_VISIT_UPLOAD, "门诊就诊上传"),
            Map.entry(OUTPATIENT_FEE_UPLOAD, "门诊费用上传"),
            Map.entry(OUTPATIENT_FEE_REVOKE, "门诊费用撤销"),
            Map.entry(OUTPATIENT_PRE_SETTLE, "门诊预结算"),
            Map.entry(OUTPATIENT_SETTLE, "门诊结算"),
            Map.entry(OUTPATIENT_SETTLE_REVOKE, "门诊结算撤销"),
            Map.entry(RECON_SUMMARY, "对账汇总"),
            Map.entry(RECON_DETAIL, "对账明细"),
            Map.entry(RECON_CONFIRM, "对账确认")
    );

    private WiInfnoConstants() {
    }

    /**
     * 解析 infno 中文名（写 yb_invoke_log.infname）。
     *
     * @param infno 交易编号
     * @return 中文名；未知时返回 infno 本身
     */
    public static String resolveDisplayName(String infno) {
        if (infno == null || infno.isBlank()) {
            return infno;
        }
        String name = DISPLAY_NAMES.get(infno.trim());
        if (name != null) {
            return name;
        }
        return infno;
    }
}
