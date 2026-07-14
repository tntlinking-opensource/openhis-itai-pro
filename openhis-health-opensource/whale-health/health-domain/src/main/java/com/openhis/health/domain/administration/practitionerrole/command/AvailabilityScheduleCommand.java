/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.practitionerrole.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 岗位可用时间（排班）命令项。
 *
 * <p>对应 DM-ADM-006 §3.3 与 SDD §5.1.1/§5.1.7 中的 {@code scheduleItems}。</p>
 */
@Getter
@Setter
public class AvailabilityScheduleCommand {
    /** 可用时间 JSON。 */
    private String availabilityJson;
}
