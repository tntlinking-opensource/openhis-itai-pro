/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.benefit.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 人员身份认定行快照。 */
@Data
@Accessors(chain = true)
public class InsuranceIdentityLineSnapshot {

    private String psnIdetType;
    private String psnTypeLv;
    private String memo;
    private String begntime;
    private String endtime;
}
