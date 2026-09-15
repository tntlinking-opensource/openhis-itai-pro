/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.returntowarehouse.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 药房退库单同意请求（触发库存双侧调整与退库入库壳单生成）。
 */
@Getter
@Setter
public class AgreeReturnToWarehouseRequest {

    /** 药房退库业务单号。 */
    private String busNo;
}
