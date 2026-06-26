/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.inpatient.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2401 入院登记结果。 */
@Data
@Accessors(chain = true)
public class IpAdmissionResult {

    private String mdtrtId;
}
