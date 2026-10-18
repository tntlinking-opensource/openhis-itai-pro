/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.servicerequest.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 医技划价建单命令（AS-MT-CHG-001；{@code generate_source=medical-tech-pricing}）。
 *
 * <p>与护士划价 {@link CreateFromNursePricingCommand} 同构：创建即 {@code VERIFIED}，
 * 不经医生草稿/校对 Tab；来源码独立为医技划价。</p>
 */
@Getter
@Setter
public class CreateFromMedicalTechPricingCommand
    extends BatchCommand<CreateFromMedicalTechPricingCommand.CreateFromMedicalTechPricingItemCommand> {

    /** 操作人主键（写入校对人/校对时刻）。 */
    private Long operatorId;

    /**
     * 医技划价建单行。
     */
    @Getter
    @Setter
    public static class CreateFromMedicalTechPricingItemCommand {
        /** 就诊主键。 */
        private Long encounterId;
        /** 诊疗活动定义主键。 */
        private Long activityId;
        /** 数量（须 &gt; 0）。 */
        private BigDecimal quantity;
        /** 单位码。 */
        private String unitCode;
        /** 患者主键。 */
        private Long patientId;
        /** 开立人（计费人）主键。 */
        private Long requesterId;
    }
}
