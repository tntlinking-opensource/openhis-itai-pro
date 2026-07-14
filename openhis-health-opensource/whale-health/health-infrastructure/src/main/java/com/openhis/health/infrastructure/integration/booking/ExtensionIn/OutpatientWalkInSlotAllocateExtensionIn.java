/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.booking.ExtensionIn;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 现场挂号占用当日号源的命令（模式 C）。
 *
 * <p>职责：无预约时由 health 挂号台请求 booking 占 slot，返回 slotId 供后续挂号单关联。</p>
 */
@Getter
@Setter
public class OutpatientWalkInSlotAllocateExtensionIn {

    /** 挂号科室主键。 */
    private Long organizationId;

    /** 挂号医师主键。 */
    private Long practitionerId;

    /** 号源类型主键。 */
    private Long healthcareServiceId;

    /** 挂号日期（当日或指定日期）。 */
    private LocalDate registrationDate;

    /** 患者主键。 */
    private Long patientId;

    /** 操作员主键（占号审计）。 */
    private Long operatorId;

    /** 指定 slot 主键；为空时由 booking 自动分配可用 slot。 */
    private Long slotId;

    /**
     * 已开立就诊主键。
     *
     * <p>现场占号须在创建 encounter 之后传入，booking 才会落预约行并绑定就诊，退号才能释号。</p>
     */
    private Long encounterId;
}
