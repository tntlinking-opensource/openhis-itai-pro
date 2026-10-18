/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 临床观察实例状态 code（cln_observation.status_enum，DM-CLN-007）。
 */
public final class ObservationStatusConstants {

    private ObservationStatusConstants() {
    }

    /** 初步结果（录入中）。 */
    public static final String STATUS_PRELIMINARY = "preliminary";

    /** 最终结果（{@code final} 为 Java 关键字，常量名加前缀）。 */
    public static final String STATUS_FINAL = "final";

    /** 已修订（final 后改值）。 */
    public static final String STATUS_AMENDED = "amended";

    /** 已作废。 */
    public static final String STATUS_CANCELLED = "cancelled";
}
