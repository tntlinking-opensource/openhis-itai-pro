/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.devicedispense.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 护士耗材使用量明细分页行（AS-RPT-002 IF-RPT-NWL-05）。
 *
 * <p>GenerateSource 展示名由应用层经 {@code deviceReqId} 加载 DeviceRequest 后 enrich，不在本读模型内。</p>
 */
@Getter
@Setter
public class NurseConsumableWorkloadDetailQueryModel {

    /** 耗材发放主键 {@code wkf_device_dispense.id}。 */
    private Long dispenseId;

    /** 耗材请求主键（供应用层加载 {@code generate_source_enum}）。 */
    private Long deviceReqId;

    /** 耗材定义主键。 */
    private Long deviceDefId;

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 归属护士：开关关={@code preparer_id}，开关开={@code performer_id}。 */
    private Long practitionerId;

    /** 高值标志（来自 Support 预解析 map）。 */
    private Boolean hvcmFlag;

    /** 业务时间：开关关={@code prepare_time}，开关开={@code dispense_time}（空回落 prepare_time）。 */
    private LocalDateTime businessTime;

    /** 发放数量（实发量优先）。 */
    private BigDecimal quantity;

    /** 计量单位编码。 */
    private String unitCode;
}
