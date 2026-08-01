/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.peisconfig.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * PEIS 模块参数读模型。
 */
@Getter
@Setter
public class PeisConfigQueryModel {

    private Long id;
    private String configKey;
    private String configValue;
    private Long orgId;
}
