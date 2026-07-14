/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.template.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 从岗位导入 T2 日程模板响应（2.24）。
 */
@Getter
@Setter
public class ImportFromPractitionerRoleResponse {

    /** 生成的 T2 日程模板主键。 */
    private Long scheduleTemplateId;

    /** 模板编码。 */
    private String templateCode;
}
