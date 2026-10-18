/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.bed.response;

import com.zxplat.framework.core.application.dto.OptionDto;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 床位预约台初始化响应（8.1）。
 */
@Getter
@Setter
public class LoadBedBookingFormInitResponse {

    /** 病区选项列表。 */
    private List<OptionDto> wards = new ArrayList<>();
}
