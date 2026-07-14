/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.nursestation.skintest.response;

import lombok.Getter;
import lombok.Setter;

/**
 * IF-OP-STK-03 保存皮试结果响应。
 */
@Getter
@Setter
public class SaveOutpatientSkinTestRecordResponse {

    /** 保存后的过敏/皮试记录主键。 */
    private Long allergyRecordId;

    /** 关联皮试检查诊疗申请主键。 */
    private Long skinTestServiceRequestId;
}
