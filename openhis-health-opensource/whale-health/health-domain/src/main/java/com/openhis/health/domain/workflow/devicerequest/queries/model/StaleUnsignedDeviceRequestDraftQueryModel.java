/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.devicerequest.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 未签发耗材医嘱草稿读模型（调价在途互斥配套；仅本聚合字段）。
 */
@Getter
@Setter
public class StaleUnsignedDeviceRequestDraftQueryModel {

    /** 耗材申请主键。 */
    private Long id;

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 开立医生主键（requester_id）。 */
    private Long requesterId;

    /** 耗材定义主键。 */
    private Long deviceDefId;

    /**
     * 有效开立时刻：优先 {@code req_opened_time}，空则回落 {@code updated_at}。
     */
    private LocalDateTime openedAt;

    /** 最近更新时间。 */
    private LocalDateTime updatedAt;
}
