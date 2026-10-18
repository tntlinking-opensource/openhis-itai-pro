/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.chargeitem.command;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 医技费用清单一键确费：对本科相关收费行批量写入确费人/确费时刻（不改收费状态）。
 *
 * <p>对应 DM-ADM-011 §6.1.6b；已确费行跳过（幂等）。</p>
 */
@Getter
@Setter
public class StampMedicalTechFeeConfirmationCommand {

    /** 确费人（adm_practitioner.id）。 */
    private Long feeConfirmerId;

    /** 确费时刻。 */
    private LocalDateTime feeConfirmedAt;

    /** 待确费的收费明细主键；空则领域服务空操作。 */
    private List<Long> chargeItemIds;

    /**
     * 解析主键列表；{@code null} 时返回空列表。
     *
     * @return 非 null 列表
     */
    public List<Long> resolveChargeItemIds() {
        if (this.chargeItemIds == null) {
            return new ArrayList<>();
        }
        return this.chargeItemIds;
    }
}
