/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.contracts.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 【2202】门诊挂号撤销结果。 */
@Data
@Accessors(chain = true)
public class OpInsuranceRevokeRegistrationResult {

    private String mdtrtId;
    private Boolean revoked;
}
