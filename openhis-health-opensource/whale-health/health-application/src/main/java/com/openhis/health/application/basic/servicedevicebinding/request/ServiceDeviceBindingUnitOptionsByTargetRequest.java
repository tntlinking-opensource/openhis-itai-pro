/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.servicedevicebinding.request;

import lombok.Data;

/**
 * 按绑定目标解析可选单位列表：目标为诊疗定义或耗材定义之一。
 */
@Data
public class ServiceDeviceBindingUnitOptionsByTargetRequest {

    /** 目标逻辑表名（与初始化 targetTableOptions 的 code 一致）。 */
    private String targetTable;

    /** 目标主键字符串。 */
    private String targetId;
}
