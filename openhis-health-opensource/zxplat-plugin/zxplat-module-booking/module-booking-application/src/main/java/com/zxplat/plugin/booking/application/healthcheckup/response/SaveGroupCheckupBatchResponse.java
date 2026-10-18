/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.healthcheckup.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 保存团检批次响应（9.4）。
 */
@Getter
@Setter
public class SaveGroupCheckupBatchResponse {

    /** 团检批次主键。 */
    private Long id;
}
