/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.catalog.nationalcatalog.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 1312 实时刷新国家目录结果（UI 出参）。
 * <p>对应 {@code InsuranceNationalCatalogAppService#refreshFromPlatform1312}，
 * 返回本次从平台拉取并 upsert 至本地镜像的行数。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "1312 实时刷新国家目录结果")
public class Refresh1312Response {

    /** 本次 upsert 行数 */
    @Schema(description = "本次 upsert 行数")
    private int upsertCount;
}
