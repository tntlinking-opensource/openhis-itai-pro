/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.ordertemplate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 组套模板按绑定维度分段列表（IF-BAS-OTL-07/11）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "组套模板绑定维度分段列表")
public class OrderTemplateBindingGroupDto {

    @Schema(description = "个人组套列表")
    /** 科室组套列表。 */
    private List<OrderTemplateHeaderRowDto> personalList;

    @Schema(description = "科室组套列表")
    private List<OrderTemplateHeaderRowDto> departmentList;

    /** 全院组套列表。 */
    @Schema(description = "全院组套列表")
    private List<OrderTemplateHeaderRowDto> hospitalList;

    /** 协定处方组套列表。 */
    @Schema(description = "协定处方组套列表")
    private List<OrderTemplateHeaderRowDto> agreementList;
}
