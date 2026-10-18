/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.wardbed.dto;

import com.zxplat.framework.core.application.dto.OptionDto;
import lombok.Data;

import java.util.List;

/**
 * 病区病房维护页初始化：形态、状态、机构与病区/病房候选下拉。
 */
@Data
public class WardBedInitDto {
    /** 病区、库房、床位等形态固定选项。 */
    private List<OptionDto> formEnums;
    /** 位置状态术语选项。 */
    private List<OptionDto> locationStatusOptions;
    /** 机构下拉。 */
    private List<OptionDto> organizationOptions;
    /** 已存在的病区位置候选。 */
    private List<OptionDto> wardOptions;
    /** 已存在的病房（库房形态）位置候选。 */
    private List<OptionDto> houseOptions;
}
