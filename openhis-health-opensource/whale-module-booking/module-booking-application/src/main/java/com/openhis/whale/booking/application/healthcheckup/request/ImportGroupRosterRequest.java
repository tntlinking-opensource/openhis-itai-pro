/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 导入团检名单请求（6.x 旧版 API 别名，对应 ImportGroupCheckupRosterRequest）。
 */
@Getter
@Setter
public class ImportGroupRosterRequest {

    /** 团检批次主键。 */
    @NotNull
    private Long batchId;

    /** 导入文件令牌。 */
    private String fileToken;
}
