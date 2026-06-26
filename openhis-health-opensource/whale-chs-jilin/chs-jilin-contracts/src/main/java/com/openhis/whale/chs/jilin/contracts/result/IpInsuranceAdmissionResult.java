/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.contracts.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 【2401】住院入院登记结果。 */
@Data
@Accessors(chain = true)
public class IpInsuranceAdmissionResult {

    /** 平台就诊 ID mdtrt_id。 */
    private String mdtrtId;
}
