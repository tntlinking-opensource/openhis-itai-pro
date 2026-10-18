/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.specimendefinition.dto;

import com.zxplat.framework.core.application.dto.OptionDto;
import lombok.Data;

import java.util.List;

/**
 * 样本定义维护页初始化数据：标本种类与发布状态下拉选项。
 */
@Data
public class SpecimenDefinitionInitDto {

    /** 标本种类下拉，码值对齐 {@link com.zxplat.domain.share.enums.SpecimenType}。 */
    private List<OptionDto> typeEnumOptions;

    /** 发布状态下拉，码值对齐 {@link com.zxplat.domain.share.enums.PublicationStatus}。 */
    private List<OptionDto> statusEnumOptions;
}
