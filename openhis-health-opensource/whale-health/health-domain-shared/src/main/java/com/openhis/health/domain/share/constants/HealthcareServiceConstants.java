/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 挂号号源类型（{@code adm_healthcare_service}）产品级共享常量。
 */
public interface HealthcareServiceConstants {

    /**
     * 「历史号复诊」业务编号（{@code bus_no}）；迁移种子与写侧 Gate 识别真源。
     */
    String BUS_NO_HISTORICAL_FOLLOW_UP = "HS_HIST_FOLLOW_UP";

    /** 「历史号复诊」展示名称（与主数据种子一致）。 */
    String NAME_HISTORICAL_FOLLOW_UP = "历史号复诊";

    /**
     * 历史号复诊资格窗口：自前次接诊时刻起向后有效的小时数（BR-REG-020）。
     */
    int HISTORICAL_FOLLOW_UP_ELIGIBILITY_HOURS = 72;
}
