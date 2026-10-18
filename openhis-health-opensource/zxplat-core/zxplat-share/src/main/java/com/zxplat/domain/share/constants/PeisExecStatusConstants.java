/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * PEIS 项目执行状态 code（peis_service_request_ext.exec_status_enum）。
 */
public final class PeisExecStatusConstants {

    private PeisExecStatusConstants() {
    }

    /** 待检。 */
    public static final String PENDING = "pending";

    /** 已检。 */
    public static final String CHECKED = "checked";

    /** 弃检。 */
    public static final String ABANDONED = "abandoned";

    /** 延检。 */
    public static final String DEFERRED = "deferred";
}
