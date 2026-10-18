/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.slot.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 批量删除号源请求（3.3 扩展：撤销误放号源）。
 */
@Getter
@Setter
public class DeleteSlotsRequest {

    /** 号源主键列表。 */
    @NotEmpty
    private List<Long> slotIds;
}
