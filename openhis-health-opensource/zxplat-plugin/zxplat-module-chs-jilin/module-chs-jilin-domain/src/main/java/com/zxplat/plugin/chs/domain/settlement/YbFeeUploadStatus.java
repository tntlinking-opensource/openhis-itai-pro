/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.settlement;

/** 2204/2301 费用上传行状态 */
public final class YbFeeUploadStatus {

    public static final String UPLOADED = "UPLOADED";
    public static final String REVOKED = "REVOKED";
    public static final String SETTLED = "SETTLED";

    private YbFeeUploadStatus() {
    }
}
