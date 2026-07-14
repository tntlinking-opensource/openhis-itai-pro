/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.ordertemplate.response;

import com.openhis.health.application.basic.ordertemplate.dto.OrderTemplateBindingGroupDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-BAS-OTL-07：医生站可套用组套/医嘱模板列表响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站组套/医嘱模板列表响应")
public class ListDoctorStationOrderTemplateResponse {

    @Schema(description = "绑定维度分段列表")
    /** 绑定维度分段列表。 */
    private OrderTemplateBindingGroupDto groups;
}
