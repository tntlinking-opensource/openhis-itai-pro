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
 * 床位预留命令（booking 侧契约，adapter 映射 health LocationDomainService）。
 */
@Getter
@Setter
public class ReserveBedCommand {

    private Long operatorId;
    private List<ReserveBedItemCommand> items = new ArrayList<>();

    @Getter
    @Setter
    public static class ReserveBedItemCommand {
        private Long locationId;
        private String reason;
    }
}
