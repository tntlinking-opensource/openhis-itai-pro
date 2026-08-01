/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.orchestration.result;

import lombok.Getter;
import lombok.Setter;

/**
 * 个检登记编排结果。
 */
@Getter
@Setter
public class RegisterPersonalCheckInResult {

    /** 就诊主键。 */
    private Long encounterId;

    /** 体检流水号。 */
    private String busNo;

    /** 展开的服务申请条数。 */
    private Integer serviceRequestCount;
}
