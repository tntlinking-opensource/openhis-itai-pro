/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 名册可选/选定套餐选项 DTO。
 */
@Getter
@Setter
public class PackageOptionDto {

    /** 套餐 ActivityDefinition 主键。 */
    private Long packageActivityId;

    /** 套餐名称。 */
    private String packageName;
}
