/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.skintest.response;

import lombok.Getter;
import lombok.Setter;

/**
 * IF-IP-STK-03 保存皮试结果响应。
 */
@Getter
@Setter
public class SaveNurseStationSkinTestRecordResponse {

    /** 过敏记录主键。 */
    private Long allergyRecordId;

    /** 皮试检查服务申请主键。 */
    private Long skinTestServiceRequestId;

    /** 本次是否新产生补费划价。 */
    private Boolean firstTimePriced;
}
