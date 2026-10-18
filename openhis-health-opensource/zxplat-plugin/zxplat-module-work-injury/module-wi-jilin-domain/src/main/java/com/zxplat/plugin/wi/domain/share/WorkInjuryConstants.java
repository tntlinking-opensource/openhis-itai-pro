/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.domain.share;

/** 吉林工伤 domain 层常量（与 {@code WiBusConstants} 值一致）。 */
public final class WorkInjuryConstants {

    /** fin_contract.bus_no 样例。 */
    public static final String CONTRACT_BUS_NO = "H22010402403-GS";
    /** yb_settlement_record.settle_mode。 */
    public static final String SCHEME_CODE = "WORK_INJURY";
    /** 默认医疗类别 med_type（门诊工伤；院点确认联网结算为 12）。 */
    public static final String DEFAULT_MED_TYPE = "12";

    /**
     * 规范化 med_type：空则默认 12。
     * <p>就医类型须与所绑 9102 审批类型（approveType）一致；勿把类型 10 强改成 12。</p>
     */
    public static String normalizeMedType(String medType) {
        if (medType == null || medType.isBlank()) {
            return DEFAULT_MED_TYPE;
        }
        return medType.trim();
    }

    /** 是否为院点联网结算可用的门诊审批/就医类型（12）。 */
    public static boolean isOnlineSettleMedType(String medTypeOrApproveType) {
        return DEFAULT_MED_TYPE.equals(normalizeMedType(medTypeOrApproveType));
    }

    /** {@code yb_wi_encounter_anchor.qualification_status}：未审批。 */
    public static final String QUALIFICATION_NONE = "NONE";
    /** 2301 后待再 9102（WI-004）。 */
    public static final String QUALIFICATION_PENDING_9102 = "PENDING_9102";
    /** 9102 已确认，可 OP_SETTLE。 */
    public static final String QUALIFICATION_VALID = "VALID";

    private WorkInjuryConstants() {
    }
}
