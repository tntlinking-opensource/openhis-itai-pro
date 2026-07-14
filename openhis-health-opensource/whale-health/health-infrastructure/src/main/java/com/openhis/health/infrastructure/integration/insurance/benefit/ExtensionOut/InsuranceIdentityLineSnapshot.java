/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 人员身份认定行快照。
 */
@Data
@Accessors(chain = true)
public class InsuranceIdentityLineSnapshot {

    /** 人员身份类别 code。 */
    private String psnIdetType;

    /** 人员类别等级。 */
    private String psnTypeLv;

    /** 备注。 */
    private String memo;

    /** 开始时间。 */
    private String begntime;

    /** 结束时间。 */
    private String endtime;
}
