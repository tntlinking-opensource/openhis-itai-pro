/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.policy.response;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 放号策略 DTO。
 */
@Getter
@Setter
public class ReleasePolicyDto {

    /** 主键。 */
    private Long id;

    /** 科室/机构主键。 */
    private Long organizationId;

    /** 策略编码。 */
    private String policyCode;

    /** 策略名称。 */
    private String policyName;

    /** 提前放号天数。 */
    private Integer advanceDays;

    /** 号段时长（分钟）。 */
    private Integer slotMinutes;

    /** 每号段容量。 */
    private Integer capacityPerSlot;

    /** 渠道配额（渠道编码 → 百分比，如 WINDOW→60）；结构化出参，禁止 JSON 字符串。 */
    private Map<String, Integer> channelQuotas;
}
