/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.specimendefinition.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 新建样本定义命令。
 */
@Getter
@Setter
public class CreateSpecimenDefinitionCommand {
    /** 业务编码。 */
    private String busNo;
    /** 样本名称。 */
    private String name;
    /** 标本种类，存 {@code SpecimenType.getCode()}。 */
    private String typeEnum;
    /** 拼音码（可空，空则按名称生成）。 */
    private String pyStr;
    /** 五笔码（可空，空则按名称生成）。 */
    private String wbStr;
}
