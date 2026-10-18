/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * OrderInfo 读模型查询共享常量（读模型 §3 Q-ORD-007、AS-IP-DOR-003 IF-IP-DOR-10）。
 *
 * <p>与 {@link ReadModelOrderTypeConstants} 配合，避免在 {@code *RMQueryService} 内散落 order_type 字面量。</p>
 */
public interface OrderInfoQueryConstants {

    /**
     * 住院医生站医嘱列表允许的 {@code order_type} 条目数（药品 + 诊疗，不含耗材）。
     */
    int INPATIENT_DOCTOR_STATION_ORDER_TYPE_SLOT_COUNT = 2;
}
