/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.recheck.request;

import lombok.Data;

/**
 * 复查申请请求（IF-PEIS-REC-01）。
 */
@Data
public class CreateRecheckRequest {

    /** 原体检就诊标识。 */
    private Long encounterId;

    /** 复查原因。 */
    private String reason;

    /** 操作人标识。 */
    private Long operatorId;
}
