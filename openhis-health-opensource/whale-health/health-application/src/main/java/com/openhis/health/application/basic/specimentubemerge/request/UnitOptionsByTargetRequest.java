/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.specimentubemerge.request;

import lombok.Data;

/**
 * 按绑定目标解析可选单位列表。
 */
@Data
public class UnitOptionsByTargetRequest {

    /** 目标表：{@code wkf_activity_definition} 或 {@code adm_device_definition}。 */
    private String targetTable;

    /** 目标主键（字符串，兼容前端 Long）。 */
    private String targetId;
}
