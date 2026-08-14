/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 结算清单院内上传标志（对齐老 uploadSettFlag）。
 */
public final class MrmsSettleUploadFlags {

    /** 未上传。 */
    public static final String NONE = "0";

    /** 已上传未提交。 */
    public static final String UPLOADED = "1";

    /** 已正式提交。 */
    public static final String COMMITTED = "2";

    private MrmsSettleUploadFlags() {
    }
}
