/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * {@code adm_location.type_json} 扩展键约定（病区多科室挂靠等）。
 */
public final class LocationTypeJsonConstants {

    private LocationTypeJsonConstants() {
    }

    /**
     * 病区额外服务的住院科室主键列表（不含或含主挂靠均可；读侧与 {@code organization_id} 并集判定）。
     */
    public static final String SERVED_ORGANIZATION_IDS = "servedOrganizationIds";

    /**
     * 空扩展快照。
     */
    public static final String EMPTY_TYPE_JSON = "{}";
}
