/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * PEIS 报告发放状态 code（peis_report_grant_ext.grant_status_enum，DM-PEIS-005）。
 */
public final class PeisGrantStatusConstants {

    private PeisGrantStatusConstants() {
    }

    /** 待发放/待签收。 */
    public static final String PENDING = "pending";

    /** 已打印。 */
    public static final String PRINTED = "printed";

    /** 已签收。 */
    public static final String SIGNED = "signed";

    /** 已邮寄。 */
    public static final String MAILED = "mailed";
}
