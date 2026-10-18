/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.healthcheckup.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 保存团检单位响应（6.x 旧版 API 别名，对应 SaveGroupCheckupOrgResponse）。
 */
@Getter
@Setter
public class SaveGroupOrgResponse {

    /** 团检单位主键。 */
    private Long id;
}
