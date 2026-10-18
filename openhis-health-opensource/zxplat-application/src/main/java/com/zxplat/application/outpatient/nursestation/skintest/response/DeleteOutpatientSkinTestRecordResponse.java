/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.nursestation.skintest.response;

import lombok.Getter;
import lombok.Setter;

/**
 * IF-OP-STK-04 删除皮试记录响应。
 */
@Getter
@Setter
public class DeleteOutpatientSkinTestRecordResponse {

    /** 删除是否成功。 */
    private Boolean success;
}
