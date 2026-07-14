/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.documentdetail.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 单据详情报表 init 入参（IF-INV-DDC-{域}-01）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "单据详情报表初始化 IF-INV-DDC-01", requiredProperties = {"domainScope"})
public class LoadDocumentDetailFormInitRequest {

    /**
     * 业务域：{@code WAREHOUSE} / {@code PHARMACY} / {@code DEPARTMENT}。
     */
    @Schema(description = "业务域 domainScope", requiredMode = Schema.RequiredMode.REQUIRED)
    private String domainScope;
}
