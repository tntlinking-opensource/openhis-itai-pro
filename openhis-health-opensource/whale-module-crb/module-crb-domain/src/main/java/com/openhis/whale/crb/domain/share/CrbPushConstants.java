/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.share;

/**
 * CRB 推送队列与重试相关常量。
 */
public final class CrbPushConstants {

    private CrbPushConstants() {
    }

    /** 默认最大重试次数（与 openhis.crb.push.max-retry 默认一致）。 */
    public static final int DEFAULT_MAX_RETRY = 5;

    /** REALTIME 档告警阈值秒数（与 openhis.crb.push.realtime-max-delay-seconds 默认一致）。 */
    public static final int DEFAULT_REALTIME_MAX_DELAY_SECONDS = 180;

    /** request/response 摘要写入 log 的最大字符数。 */
    public static final int LOG_SUMMARY_MAX_LENGTH = 2000;
}
