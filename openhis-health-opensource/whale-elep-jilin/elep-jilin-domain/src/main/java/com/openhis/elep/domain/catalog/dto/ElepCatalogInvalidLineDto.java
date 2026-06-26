/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.elep.domain.catalog.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/** 目录校验失效行。 */
@Data
@Accessors(chain = true)
public class ElepCatalogInvalidLineDto {

    private int lineNo;
    private String hilistCode;
    private String reason;
}
