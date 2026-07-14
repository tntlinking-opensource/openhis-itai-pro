/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.bed.command;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 取消床位预留命令（booking 侧契约，adapter 映射 health LocationDomainService.releaseBed）。
 */
@Getter
@Setter
public class ReleaseBedReservationCommand {

    private Long operatorId;
    private List<ReleaseBedReservationItemCommand> items = new ArrayList<>();

    @Getter
    @Setter
    public static class ReleaseBedReservationItemCommand {
        private Long locationId;
    }
}
