/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.config.catalog.request;

import lombok.Data;

import java.util.List;

/**
 * 保存体检套餐（ActivityDefinition 组合）请求（CFG-05）。
 */
@Data
public class SavePackageRequest {

    /** 套餐主键；更新时必填。 */
    private Long packageActivityId;

    /** 套餐编码（更新时可选，对应 busNo 展示）。 */
    private String code;

    /** 套餐名称。 */
    private String display;

    /** 状态：active / inactive。 */
    private String statusEnum;

    /** 子项目 AD 主键列表。 */
    private List<Long> childActivityIds;
}
