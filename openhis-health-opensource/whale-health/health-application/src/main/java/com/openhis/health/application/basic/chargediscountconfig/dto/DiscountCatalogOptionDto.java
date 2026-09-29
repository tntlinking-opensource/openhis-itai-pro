/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.chargediscountconfig.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Getter;
import lombok.Setter;

/**
 * 折扣配置选大类/选品下拉行（IF-CDC-07）。
 *
 * <p>大类模式：{@code value}=categoryCode；单项模式：{@code value}=productId 字符串，{@code id}=产品主键。</p>
 */
@Getter
@Setter
public class DiscountCatalogOptionDto {

    /**
     * 单项模式下的产品主键；大类模式为空。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 选项值：大类码或产品 id 字符串。
     */
    private String value;

    /**
     * 展示文案。
     */
    private String label;

    /**
     * 业务编码（单项时可带 busNo；大类可空）。
     */
    private String busNo;

    /**
     * 大类模式下：本院基础目录中该大类下的项目数；单项模式可空。
     */
    private Integer itemCount;
}
