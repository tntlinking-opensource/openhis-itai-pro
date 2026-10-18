/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.healthcheckup.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 保存团检名单响应（9.5）。
 */
@Getter
@Setter
public class SaveGroupCheckupRosterResponse {

    /** 团检名单主键。 */
    private Long id;
}
