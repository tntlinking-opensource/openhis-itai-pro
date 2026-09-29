/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.pacs.device.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 检查设备台账读模型。
 */
@Getter
@Setter
public class PacsDeviceQueryModel {

    private Long id;

    private String deviceCode;

    private String name;

    private String modalityCode;

    private String aeTitle;

    private String stationName;

    private Boolean enabledFlag;

    private Integer sortOrder;
}
