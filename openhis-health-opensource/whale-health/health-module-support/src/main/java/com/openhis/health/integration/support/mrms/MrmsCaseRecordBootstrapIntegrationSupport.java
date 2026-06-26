/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.support.mrms;

import com.openhis.health.integration.mrms.MrmsCaseRecordBootstrapExtension;
import com.openhis.health.integration.mrms.command.DischargeCaseRecordBootstrapCommand;
import com.openhis.health.integration.support.IntegrationAfterCommitSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 离院里程碑后触发病案建档 Extension（afterCommit）。 */
@Component
@RequiredArgsConstructor
public class MrmsCaseRecordBootstrapIntegrationSupport {

    private final MrmsCaseRecordBootstrapExtension mrmsCaseRecordBootstrapExtension;

    public void afterDischargeRecorded(Long encounterId, Long operatorId, String outWayCode) {
        if (encounterId == null || !mrmsCaseRecordBootstrapExtension.isEnabled()) {
            return;
        }
        DischargeCaseRecordBootstrapCommand command = new DischargeCaseRecordBootstrapCommand();
        command.setEncounterId(encounterId);
        command.setOperatorId(operatorId);
        command.setOutWayCode(outWayCode);
        IntegrationAfterCommitSupport.runAfterCommit(
                () -> mrmsCaseRecordBootstrapExtension.onDischargeRecorded(command));
    }
}
