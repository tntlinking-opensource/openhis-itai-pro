/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.elep.domain.catalog.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/** 目录项 DTO（Picker / 校验最小字段集）。 */
@Data
@Accessors(chain = true)
public class ElepCatalogItemDto {

    private String hilistCode;
    private String hilistName;
    private String spec;
    private String dosform;
    private String aprvno;
    private String pinyin;
    private String valiFlag;
    private String sourceInfno;
}
