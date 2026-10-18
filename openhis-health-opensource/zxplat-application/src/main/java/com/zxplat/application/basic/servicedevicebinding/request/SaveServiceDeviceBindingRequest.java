/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.servicedevicebinding.request;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 保存诊疗耗材绑定请求：无 {@code id} 为新建，有 {@code id} 为更新。
 */
@Data
public class SaveServiceDeviceBindingRequest {

    /** 绑定记录主键；新建时为 {@code null}。 */
    private Long id;

    /** 绑定范围业务码。 */
    private String rangeCode;

    /** 绑定类型码：USAGE / ACTIVITY。 */
    private String bindType;

    /** 来源项标识（与绑定类型对应的 item 表主键字符串）。 */
    private String sourceId;

    /** 目标主键字符串，解析为 Long 写入领域。 */
    private String targetId;

    /** 目标表名，须为诊疗定义表或耗材定义表常量之一。 */
    private String targetTable;

    /** 绑定数量，须大于 0。 */
    private BigDecimal quantity;

    /** 单位码（术语 unit）。 */
    private String unitCode;

    /** 发布状态码：ACTIVE / INACTIVE。 */
    private String statusEnum;
}
