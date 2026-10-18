/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.catalog;

import lombok.Getter;
import lombok.Setter;

/**
 * 三大目录编辑前/写侧共用的可编辑性查询模型。
 */
@Getter
@Setter
public class CatalogEditabilityQueryModel {

    /** 业务码：{@link com.zxplat.domain.share.constants.CatalogEditabilityConstants}。 */
    private String editabilityCode;

    /** 面向维护页的可读说明。 */
    private String editabilityMessage;
}
