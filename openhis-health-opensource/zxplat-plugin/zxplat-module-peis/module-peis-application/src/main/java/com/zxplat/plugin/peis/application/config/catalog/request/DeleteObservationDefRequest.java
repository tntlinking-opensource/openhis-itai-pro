/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.config.catalog.request;

import lombok.Data;

/**
 * 软删体检小项请求（CFG-03）。
 */
@Data
public class DeleteObservationDefRequest {

    /** 小项主键。 */
    private Long definitionId;
}
