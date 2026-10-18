/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.template.request;

import com.zxplat.plugin.booking.application.template.response.SlotProvisionTemplateItemDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 保存 T1 放号时段模板。
 */
@Getter
@Setter
public class SaveSlotProvisionTemplateRequest {

    /** 主键（更新时）。 */
    private Long id;

    /** 科室主键。 */
    private Long organizationId;

    /** 模板编码（可选；留空时由领域层按 {@link com.zxplat.plugin.booking.domain.template.constants.BookingTemplateCodeConstants} 自动生成）。 */
    private String templateCode;

    /** 模板名称。 */
    @NotBlank
    private String templateName;

    /** 时段列表。 */
    @NotEmpty
    private List<SlotProvisionTemplateItemDto> slotTemplates;

    /** 是否启用。 */
    @NotNull
    private Boolean enabled;
}
