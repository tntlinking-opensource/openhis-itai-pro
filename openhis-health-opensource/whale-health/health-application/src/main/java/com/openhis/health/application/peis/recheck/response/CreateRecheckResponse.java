/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.recheck.response;

import lombok.Data;

/**
 * 复查申请响应。
 */
@Data
public class CreateRecheckResponse {

    /** 任务主键。 */
    private Long taskId;

    /** 就诊主键。 */
    private Long encounterId;
}
