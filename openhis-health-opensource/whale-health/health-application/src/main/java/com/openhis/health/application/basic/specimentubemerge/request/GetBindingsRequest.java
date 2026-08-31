/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.specimentubemerge.request;

import lombok.Data;

/**
 * 查询设备合管绑定请求。
 */
@Data
public class GetBindingsRequest {

    /** 检验设备主键（字符串，兼容前端 Long）。 */
    private String deviceId;
}
