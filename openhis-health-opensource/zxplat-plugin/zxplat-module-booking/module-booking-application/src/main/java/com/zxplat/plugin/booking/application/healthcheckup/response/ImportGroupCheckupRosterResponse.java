/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.healthcheckup.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 导入团检名单响应（9.5）。
 */
@Getter
@Setter
public class ImportGroupCheckupRosterResponse {

    /** 成功导入条数。 */
    private Integer importedCount;
}
