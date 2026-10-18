/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.template.response;

import com.zxplat.framework.core.application.dto.OptionDto;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * T1 下拉选项响应。
 */
@Getter
@Setter
public class ListSlotProvisionTemplateOptionsResponse {

    /** 选项列表。 */
    private List<OptionDto> options = new ArrayList<>();
}
