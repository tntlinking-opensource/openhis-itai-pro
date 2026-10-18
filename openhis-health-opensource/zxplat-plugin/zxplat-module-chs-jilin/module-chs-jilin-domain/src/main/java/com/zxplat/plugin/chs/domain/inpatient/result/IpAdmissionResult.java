/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inpatient.result;



import lombok.Data;

import lombok.experimental.Accessors;



/** 2401 入院登记结果 */
@Data

@Accessors(chain = true)

public class IpAdmissionResult {



    private String mdtrtId;

}
