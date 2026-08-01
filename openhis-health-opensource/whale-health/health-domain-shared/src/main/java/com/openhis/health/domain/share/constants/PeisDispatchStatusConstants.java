/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * PEIS 外发状态 code（lis/pacs_dispatch_enum）及模块标识。
 */
public final class PeisDispatchStatusConstants {

    private PeisDispatchStatusConstants() {
    }

    /** 未发送。 */
    public static final String NOT_SENT = "notSent";

    /** 已发送。 */
    public static final String SENT = "sent";

    /** 已回传结果。 */
    public static final String RESULT_RECEIVED = "resultReceived";

    /** LIS 模块。 */
    public static final String MODULE_LIS = "LIS";

    /** PACS 模块。 */
    public static final String MODULE_PACS = "PACS";
}
