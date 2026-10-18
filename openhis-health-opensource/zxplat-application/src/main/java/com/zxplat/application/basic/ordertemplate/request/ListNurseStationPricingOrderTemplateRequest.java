/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.ordertemplate.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-BAS-OTL-11：护士站划价组套列表请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "护士站划价组套列表请求")
public class ListNurseStationPricingOrderTemplateRequest {

    @Schema(description = "名称关键字")
    /** 名称关键字。 */
    private String searchKey;
}
