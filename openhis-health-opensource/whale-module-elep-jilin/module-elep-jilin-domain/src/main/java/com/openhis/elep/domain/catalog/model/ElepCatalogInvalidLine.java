/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.catalog.model;

import lombok.Data;
import lombok.experimental.Accessors;

/** 目录校验失效行。 */
@Data
@Accessors(chain = true)
public class ElepCatalogInvalidLine {

    private int lineNo;
    private String hilistCode;
    private String reason;
}
