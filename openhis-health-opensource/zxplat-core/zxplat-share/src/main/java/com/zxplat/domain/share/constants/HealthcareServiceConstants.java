/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 挂号号源类型（{@code adm_healthcare_service}）产品级共享常量。
 */
public interface HealthcareServiceConstants {

    /**
     * 「复诊0元号」种子默认 {@code bus_no}（院点可改）；<strong>Gate 不按本字段识别</strong>。
     */
    String BUS_NO_HISTORICAL_FOLLOW_UP = "HS00000004";

    /**
     * 「复诊0元号」展示名称；写侧 Gate / 资格识别真源（trim 精确匹配 {@code name}）。
     */
    String NAME_HISTORICAL_FOLLOW_UP = "复诊0元号";

    /**
     * 复诊0元号资格窗口：自前次接诊时刻起向后有效的小时数（BR-REG-020）。
     */
    int HISTORICAL_FOLLOW_UP_ELIGIBILITY_HOURS = 72;
}
