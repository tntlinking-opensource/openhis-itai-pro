/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.ordertemplate.response;

import com.zxplat.application.basic.ordertemplate.dto.OrderTemplateBindingGroupDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-BAS-OTL-11：护士站划价组套列表响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "护士站划价组套列表响应")
public class ListNurseStationPricingOrderTemplateResponse {

    @Schema(description = "绑定维度分段列表")
    /** 绑定维度分段列表。 */
    private OrderTemplateBindingGroupDto groups;
}
