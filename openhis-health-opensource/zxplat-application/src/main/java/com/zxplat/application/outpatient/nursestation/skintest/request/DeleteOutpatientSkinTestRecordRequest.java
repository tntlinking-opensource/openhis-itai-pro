/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.nursestation.skintest.request;

import lombok.Getter;
import lombok.Setter;

/**
 * IF-OP-STK-04 删除皮试记录请求。
 */
@Getter
@Setter
public class DeleteOutpatientSkinTestRecordRequest {

    /** 就诊主键。 */
    private Long encounterId;

    /** 过敏记录主键。 */
    private Long allergyRecordId;
}
