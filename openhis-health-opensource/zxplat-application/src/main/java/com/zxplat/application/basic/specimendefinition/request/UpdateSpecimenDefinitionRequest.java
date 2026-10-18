/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.specimendefinition.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 维护样本定义请求体。
 */
@Data
public class UpdateSpecimenDefinitionRequest {

    /** 样本定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 业务编码（可空，维护时不改已有编码）。 */
    private String busNo;

    /** 样本名称。 */
    private String name;

    /** 标本种类，存 {@code SpecimenType.getCode()}。 */
    private String typeEnum;

    /** 拼音码（可空；名称变更且未传时由领域重算）。 */
    private String pyStr;

    /** 五笔码（可空；名称变更且未传时由领域重算）。 */
    private String wbStr;
}
