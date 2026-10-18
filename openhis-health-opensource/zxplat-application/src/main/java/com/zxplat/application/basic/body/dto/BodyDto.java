/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.body.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 身体部位 DTO（部位管理菜单出参）。
 *
 *
 */
@Data
public class BodyDto {

    /** 身体部位主键（字符串序列化适配前端）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 业务编号 */
    private String busNo;

    /** 名称 */
    private String name;

    /** 上级部位ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    /** 状态枚举值 */
    private String statusEnum;

    /** 状态枚举文本 */
    private String statusEnumText;

    /** 拼音码 */
    private String pyStr;

    /** 五笔码 */
    private String wbStr;
}
