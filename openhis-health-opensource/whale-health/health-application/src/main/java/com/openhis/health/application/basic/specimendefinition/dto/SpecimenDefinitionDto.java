/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.specimendefinition.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 样本定义对外展示 DTO：维护页列表与详情共用。
 */
@Data
public class SpecimenDefinitionDto {

    /** 技术主键（JSON 序列化为字符串，避免前端 Long 精度丢失）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 院内唯一业务编码。 */
    private String busNo;

    /** 样本名称。 */
    private String name;

    /** 拼音检索码。 */
    private String pyStr;

    /** 五笔检索码。 */
    private String wbStr;

    /** 标本种类编码，存 {@code SpecimenType.getCode()}。 */
    private String typeEnum;

    /** 标本种类展示文案。 */
    private String typeEnumText;

    /** 发布状态编码，存 {@code PublicationStatus.getCode()}。 */
    private String statusEnum;

    /** 发布状态展示文案。 */
    private String statusEnumText;

    /** 最近更新时间（列表行展示）。 */
    private LocalDateTime updatedAt;
}
