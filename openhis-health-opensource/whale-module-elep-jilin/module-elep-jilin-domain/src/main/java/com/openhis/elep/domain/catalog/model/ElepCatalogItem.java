/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.catalog.model;

import lombok.Data;
import lombok.experimental.Accessors;

/** 目录项（Picker / 校验最小字段集）。 */
@Data
@Accessors(chain = true)
public class ElepCatalogItem {

    private String hilistCode;
    private String hilistName;
    private String spec;
    private String dosform;
    private String aprvno;
    private String prodentpName;
    private String pinyin;
    private String valiFlag;
    private String sourceInfno;
}
