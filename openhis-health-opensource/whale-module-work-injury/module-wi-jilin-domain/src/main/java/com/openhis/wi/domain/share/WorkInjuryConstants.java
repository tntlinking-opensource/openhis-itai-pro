/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.domain.share;

/** 吉林工伤 domain 层常量（与 {@code WiBusConstants} 值一致）。 */
public final class WorkInjuryConstants {

    /** fin_contract.bus_no 样例。 */
    public static final String CONTRACT_BUS_NO = "H22010402403-GS";
    /** yb_settlement_record.settle_mode。 */
    public static final String SCHEME_CODE = "WORK_INJURY";
    /** 默认医疗类别 med_type（门诊工伤）。 */
    public static final String DEFAULT_MED_TYPE = "12";

    /** {@code yb_wi_encounter_anchor.qualification_status}：未审批。 */
    public static final String QUALIFICATION_NONE = "NONE";
    /** 2301 后待再 9102（WI-004）。 */
    public static final String QUALIFICATION_PENDING_9102 = "PENDING_9102";
    /** 9102 已确认，可 OP_SETTLE。 */
    public static final String QUALIFICATION_VALID = "VALID";

    private WorkInjuryConstants() {
    }
}
