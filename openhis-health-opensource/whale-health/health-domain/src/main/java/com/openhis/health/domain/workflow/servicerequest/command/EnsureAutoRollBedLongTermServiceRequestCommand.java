/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.servicerequest.command;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 自动计费配置 ensure 长期诊疗申请命令（AS-IP-NUR-013 BR-IP-ARG-019；DM-WKF-004）。
 *
 * <p>配置保存时创建或复用 {@code generate_source=auto-roll-fees} 的长期 VERIFIED 医嘱，供滚费任务按 IF-IP-EXE-02 执行。
 * 类目取自选品 ActivityDefinition（不写死床位 51）。</p>
 */
@Getter
@Setter
public class EnsureAutoRollBedLongTermServiceRequestCommand {

    /** 自动计费配置主键（{@code cus_charge_item_auto_gen.id}，写入 based_on_id）。 */
    private Long ruleId;

    /** 住院就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 诊疗活动定义主键（自动计费选品）。 */
    private Long activityId;

    /**
     * 诊疗类目 code（写入 {@code ServiceRequest.category_enum}；与选品 ActivityDefinition.category_code 一致）。
     */
    private String categoryEnum;

    /** 医嘱数量（通常与配置 quantityValue 一致）。 */
    private BigDecimal quantity;

    /** 数量单位持久化码。 */
    private String unitCode;

    /** 开立人主键（通常为配置保存护士）。 */
    private Long requesterId;

    /** 操作人主键（写入校对人与事件 operatorId）。 */
    private Long operatorId;
}
