/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.template.queries.model;

import com.openhis.whale.booking.domain.template.entity.ScheduleTemplate;
import com.openhis.whale.booking.domain.template.entity.SlotProvisionTemplate;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 某日解析出的日模板计划（周 fallback / 虚拟可约）。
 */
@Getter
@Setter
public class ResolvedDaySchedulePlan {

    private LocalDate date;

    private Long weekScheduleTemplateId;

    private Long dayScheduleTemplateId;

    private ScheduleTemplate dayTemplate;

    private SlotProvisionTemplate slotTemplate;

    private boolean fromTemplate;

    private boolean skip;

    private boolean bookable;

    /** T1 虚拟时段（bookable 时填充）。 */
    private List<VirtualSlotPeriod> virtualPeriods = new ArrayList<>();

    @Getter
    @Setter
    public static class VirtualSlotPeriod {
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Integer capacity;
        private String virtualSlotKey;
    }
}
