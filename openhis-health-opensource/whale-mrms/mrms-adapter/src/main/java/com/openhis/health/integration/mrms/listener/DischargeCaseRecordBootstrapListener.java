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

package com.openhis.health.integration.mrms.listener;

import com.openhis.health.domain.share.events.custom.encounterhistory.DischargeMilestoneRecordedEvent;
import com.openhis.whale.mrms.port.CaseRecordBootstrapPort;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 离院里程碑提交后幂等创建 MRMS 病案主档。
 */
@Component
@ConditionalOnBean(CaseRecordBootstrapPort.class)
@RequiredArgsConstructor
public class DischargeCaseRecordBootstrapListener {

    private final CaseRecordBootstrapPort caseRecordBootstrapPort;

    /**
     * 监听离院里程碑事件，在事务提交后为就诊创建病案主档（已存在则跳过）。
     *
     * @param event 离院里程碑领域事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDischarge(DischargeMilestoneRecordedEvent event) {
        if (event.getEncounterId() == null) {
            // 事件载荷缺少就诊主键：无法关联病案，静默跳过
            return;
        }
        // 幂等创建病案主档，供 MRMS 后续编目
        caseRecordBootstrapPort.createIfAbsent(event.getEncounterId());
    }
}
