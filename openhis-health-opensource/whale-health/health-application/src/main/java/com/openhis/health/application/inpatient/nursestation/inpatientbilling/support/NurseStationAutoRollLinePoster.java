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

package com.openhis.health.application.inpatient.nursestation.inpatientbilling.support;

import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.AutoRollLineContext;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.mapper.NurseStationAutoRollMapper;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * IF-IP-ARG-08：单条滚费落账（失败 catch 后不抛，不阻断同批其它规则行）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NurseStationAutoRollLinePoster {

    private final ServiceRequestDomainService serviceRequestDomainService;
    private final ChargeItemDomainService chargeItemDomainService;
    private final NurseStationAutoRollMapper nurseStationAutoRollMapper;

    /**
     * 单条滚费 Request + Charge 成对落库。
     *
     * @param line       落账行
     * @param operatorId 操作人
     * @return 成功 true
     */
    public boolean postLine(AutoRollLineContext line, Long operatorId) {
        try {
            return doPostLine(line, operatorId);
        } catch (RuntimeException ex) {
            // 单条失败仅记日志，由上层 failedCount 汇总，不抛出阻断其它 rule
            log.warn("住院滚费单条落账失败 encounterId={} chargeDefinitionId={}: {}",
                line.getEncounterId(), line.getChargeDefinitionId(), ex.getMessage());
            return false;
        }
    }

    private boolean doPostLine(AutoRollLineContext line, Long operatorId) {
        // Pre：滚费行 → 单条 CreateFromAutoRollCommand（category/occurrenceTime 已在行上下文）
        var createCmd = nurseStationAutoRollMapper.toCreateFromAutoRollCommand(line, operatorId);
        // Cmd：先落 VERIFIED 诊疗 Request（generate_source=auto-roll-fees）
        List<Long> serviceRequestIds = serviceRequestDomainService.createFromAutoRoll(createCmd);
        if (serviceRequestIds == null || serviceRequestIds.isEmpty()) {
            return false;
        }
        // Cmd：同事务内落 Charge（锚定刚建的 serviceRequestId）
        var chargeCmd = nurseStationAutoRollMapper.toCreateChargeItemCommandForAutoRoll(
            line, serviceRequestIds.get(0), operatorId);
        chargeItemDomainService.priceByAutoRoll(chargeCmd);
        return true;
    }
}
