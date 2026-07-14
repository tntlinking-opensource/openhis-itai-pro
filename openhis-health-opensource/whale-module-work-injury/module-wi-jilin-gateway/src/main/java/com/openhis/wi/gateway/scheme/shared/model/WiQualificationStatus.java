/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.gateway.scheme.shared.model;

/**
 * 工伤审批资格状态（{@code yb_wi_encounter_anchor.qualification_status}）。
 *
 * <p>domain 常量镜像见 {@link com.openhis.wi.domain.share.WorkInjuryConstants}。</p>
 */
public final class WiQualificationStatus {

    /** 未审批 / 未绑定 qualification_id。 */
    public static final String NONE = "NONE";
    /** 2301 后待再 9102（WI-004）。 */
    public static final String PENDING_9102 = "PENDING_9102";
    /** 9102 已确认，可 OP_SETTLE。 */
    public static final String VALID = "VALID";

    private WiQualificationStatus() {
    }
}
