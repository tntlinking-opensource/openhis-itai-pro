/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.template.response;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * T1 放号时段模板 DTO。
 */
@Getter
@Setter
public class SlotProvisionTemplateDto {

    /** 主键。 */
    private Long id;

    /** 科室主键。 */
    private Long organizationId;

    /** 模板编码。 */
    private String templateCode;

    /** 模板名称。 */
    private String templateName;

    /** 时段列表。 */
    private List<SlotProvisionTemplateItemDto> slotTemplates = new ArrayList<>();

    /** 是否启用。 */
    private Boolean enabled;
}
