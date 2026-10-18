/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.healthcheckup.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 保存团检单位响应（9.3）。
 */
@Getter
@Setter
public class SaveGroupCheckupOrgResponse {

    /** 团检单位主键。 */
    private Long id;
}
