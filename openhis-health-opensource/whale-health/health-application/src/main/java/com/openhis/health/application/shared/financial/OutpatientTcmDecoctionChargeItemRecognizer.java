/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.financial;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.share.constants.DoctorStationTcmConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 识别门诊中药「代煎中药」收费明细。
 *
 * <p>供转医保/转自费跳过账单行、预结算历史同户防御拆分；代煎正常挂个人现金自费户，不改 {@code Account}。</p>
 */
@Component
@RequiredArgsConstructor
public class OutpatientTcmDecoctionChargeItemRecognizer {

    /** 代煎诊疗项目读侧（按名称精确匹配）。 */
    private final ActivityDefinitionQueryService activityDefinitionQueryService;

    /**
     * 判断收费明细是否为门诊代煎中药费用行。
     *
     * @param chargeItem 收费明细；可空
     * @return 命中代煎产品/服务锚点时 {@code true}
     */
    public boolean isDecoctionChargeItem(ChargeItem chargeItem) {
        if (chargeItem == null) {
            return false;
        }
        Long decoctionActivityId = resolveDecoctionActivityIdOrNull();
        if (decoctionActivityId == null) {
            return false;
        }
        // 现行口径：product 锚定 ActivityDefinition
        if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(chargeItem.getProductTable())
            && decoctionActivityId.equals(chargeItem.getProductId())) {
            return true;
        }
        // 兼容历史错误锚点：service 直接指向 ActivityDefinition
        if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(chargeItem.getServiceTable())
            && decoctionActivityId.equals(chargeItem.getServiceId())) {
            return true;
        }
        return false;
    }

    /**
     * 解析租户代煎诊疗项目主键；未配置时返回 {@code null}（识别器静默不命中）。
     *
     * @return 代煎 activity 主键；未配置时 {@code null}
     */
    private Long resolveDecoctionActivityIdOrNull() {
        try {
            return activityDefinitionQueryService.requireIdByExactName(
                DoctorStationTcmConstants.DECOCTION_ACTIVITY_NAME);
        } catch (RuntimeException ex) {
            // 租户未配置代煎诊疗项目时识别器静默不命中，避免阻断转账户/预结算
            return null;
        }
    }
}
