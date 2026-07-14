/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.skintest.request;

import lombok.Getter;
import lombok.Setter;

/**
 * IF-IP-STK-04 删除皮试记录请求。
 */
@Getter
@Setter
public class DeleteNurseStationSkinTestRecordRequest {

    /** 就诊主键。 */
    private Long encounterId;

    /** 过敏记录主键。 */
    private Long allergyRecordId;
}
