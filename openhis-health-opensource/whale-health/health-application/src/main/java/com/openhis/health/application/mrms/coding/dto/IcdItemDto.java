/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.coding.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * ICD 编码候选项 DTO。
 */
@Getter
@Setter
public class IcdItemDto {

    /** 编码。 */
    private String code;

    /** 名称。 */
    private String name;

    /** 拼音助记码。 */
    private String pyStr;
}
