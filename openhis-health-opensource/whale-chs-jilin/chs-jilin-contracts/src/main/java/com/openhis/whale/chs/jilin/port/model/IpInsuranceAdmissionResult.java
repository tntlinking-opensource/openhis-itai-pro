/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.port.model;

import lombok.Data;
import lombok.experimental.Accessors;

/** 住院医保入院登记（2401）结果。 */
@Data
@Accessors(chain = true)
public class IpInsuranceAdmissionResult {

    private String mdtrtId;
}
