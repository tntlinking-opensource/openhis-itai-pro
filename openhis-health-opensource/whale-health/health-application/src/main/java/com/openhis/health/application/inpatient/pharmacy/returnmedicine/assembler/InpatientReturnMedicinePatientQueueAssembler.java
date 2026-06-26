/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.inpatient.pharmacy.returnmedicine.assembler;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;

import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.InpatientPharmacyReturnQueueTab;
import com.openhis.health.domain.share.enums.DispenseStatus;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 住院退药患者队列行装配：住院就诊上下文 + 退药聚合态 → 按 Tab 过滤后的上下文列表（BR-IPR-001）。
 */
@Component
public class InpatientReturnMedicinePatientQueueAssembler {

    /**
     * 按队列页签过滤住院就诊上下文，供 Mapper 逐行映射为队列 DTO。
     *
     * @param contexts              住院就诊上下文（顺序与 encounterIds 一致）
     * @param refundCodeByEncounter 就诊 → 聚合退药状态 code
     * @param queueTab              PENDING / COMPLETED；空则不过滤
     * @return 过滤后的上下文列表
     */
    public List<InpatientEncounterContextQueryModel> buildRows(
        List<InpatientEncounterContextQueryModel> contexts,
        Map<Long, String> refundCodeByEncounter,
        String queueTab) {
        List<InpatientEncounterContextQueryModel> rows = new ArrayList<>();
        if (contexts == null || contexts.isEmpty()) {
            return rows;
        }
        String normalizedTab = trimToEmpty(queueTab);
        if (normalizedTab.isEmpty()) {
            normalizedTab = InpatientPharmacyReturnQueueTab.PENDING;
        }
        for (InpatientEncounterContextQueryModel ctx : contexts) {
            if (ctx == null || ctx.getEncounterId() == null) {
                continue;
            }
            String agg = refundCodeByEncounter.get(ctx.getEncounterId());
            if (agg == null) {
                agg = GeneralSharedConstants.EMPTY_STRING;
            }
            // 待退 Tab：仅保留就诊下仍存在 PENDING_REFUND 聚合态的患者
            if (InpatientPharmacyReturnQueueTab.PENDING.equalsIgnoreCase(normalizedTab)) {
                if (!DispenseStatus.PENDING_REFUND.getCode().equals(agg)) {
                    continue;
                }
            }
            // 已退 Tab：仅保留仅有 REFUNDED 且无待退的就诊
            if (InpatientPharmacyReturnQueueTab.COMPLETED.equalsIgnoreCase(normalizedTab)) {
                if (!DispenseStatus.REFUNDED.getCode().equals(agg)) {
                    continue;
                }
            }
            rows.add(ctx);
        }
        return rows;
    }
}