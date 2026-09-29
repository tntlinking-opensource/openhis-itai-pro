/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.pacs.device.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 保存检查设备台账命令。
 */
@Getter
@Setter
public class SavePacsDeviceCommand {

    private Long id;

    private String deviceCode;

    private String name;

    private String modalityCode;

    private String aeTitle;

    private String stationName;

    private Boolean enabledFlag;

    private Integer sortOrder;

    private String remark;

    private Long tenantId;

    private Long operatorId;
}
