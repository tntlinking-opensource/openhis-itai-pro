/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.domain.catalog.model;

import lombok.Data;
import lombok.experimental.Accessors;

/** 国家目录镜像只读行（轻量 POJO，不映射 JPA）。 */
@Data
@Accessors(chain = true)
public class ElepNationalCatalogRow {

    private String hilistCode;
    private String hilistName;
    private String spec;
    private String dosform;
    private String aprvno;
    private String prodentpName;
    private String pinyin;
    private String valiFlag;
    private String sourceInfno;
    private String listType;
}
