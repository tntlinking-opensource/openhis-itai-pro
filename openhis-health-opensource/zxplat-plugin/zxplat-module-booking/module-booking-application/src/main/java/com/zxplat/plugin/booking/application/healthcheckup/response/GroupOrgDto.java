/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.healthcheckup.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 团检单位 DTO（6.x 旧版 API 别名，对应 GroupCheckupOrgDto）。
 */
@Getter
@Setter
public class GroupOrgDto {

    /** 团检单位主键。 */
    private Long id;

    /** 单位编码。 */
    private String orgCode;

    /** 单位名称。 */
    private String orgName;

    /** 联系人姓名。 */
    private String contactName;

    /** 联系电话。 */
    private String contactPhone;

    /** 结算方式。 */
    private String settlementMode;
}
