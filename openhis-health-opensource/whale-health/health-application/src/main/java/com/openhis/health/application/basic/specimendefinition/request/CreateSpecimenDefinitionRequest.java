/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.specimendefinition.request;

import lombok.Data;

/**
 * 新建样本定义请求体。
 */
@Data
public class CreateSpecimenDefinitionRequest {

    /** 业务编码（可空，由领域按 SPD 前缀自动生成）。 */
    private String busNo;

    /** 样本名称。 */
    private String name;

    /** 标本种类，存 {@code SpecimenType.getCode()}。 */
    private String typeEnum;

    /** 拼音码（可空，空则按名称自动生成）。 */
    private String pyStr;

    /** 五笔码（可空，空则按名称自动生成）。 */
    private String wbStr;
}
