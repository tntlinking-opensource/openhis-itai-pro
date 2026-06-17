/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.nursestation.skintest.response;

import lombok.Getter;
import lombok.Setter;

/**
 * IF-IP-STK-04 删除皮试记录响应。
 */
@Getter
@Setter
public class DeleteNurseStationSkinTestRecordResponse {

    /** 是否删除成功。 */
    private Boolean success;
}
