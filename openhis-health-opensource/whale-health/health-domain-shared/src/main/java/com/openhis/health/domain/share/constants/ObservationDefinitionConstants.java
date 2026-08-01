/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

import java.util.Set;

/**
 * 临床观察定义（DM-CLN-005）目录常量：分类码与发布态（与 {@code cln_observation_definition} 落库 code 一致）。
 */
public final class ObservationDefinitionConstants {

    private ObservationDefinitionConstants() {
    }

    /** 体检小项（结果录入单位）。 */
    public static final String CATEGORY_EXAM_ITEM = "exam_item";

    /** 生命体征类观察项。 */
    public static final String CATEGORY_VITALS = "vitals";

    /** 分科结论项（DES-04；禁止另建 peis_dept_conclusion 表）。 */
    public static final String CATEGORY_DEPT_CONCLUSION = "dept_conclusion";

    /** 其他观察分类。 */
    public static final String CATEGORY_OTHER = "other";

    /** 目录允许的 category_code 全集（BR-CLN-OD-03）。 */
    public static final Set<String> ALLOWED_CATEGORY_CODES = Set.of(
        CATEGORY_EXAM_ITEM,
        CATEGORY_VITALS,
        CATEGORY_DEPT_CONCLUSION,
        CATEGORY_OTHER
    );

    /** 启用（与 Flyway 默认 status_enum 一致，小写）。 */
    public static final String STATUS_ACTIVE = "active";

    /** 停用。 */
    public static final String STATUS_INACTIVE = "inactive";
}
