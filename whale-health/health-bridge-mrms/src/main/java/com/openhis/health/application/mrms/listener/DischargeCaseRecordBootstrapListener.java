package com.openhis.health.application.mrms.listener;

import com.openhis.health.domain.share.events.custom.encounterhistory.DischargeMilestoneRecordedEvent;
import com.openhis.whale.mrms.domain.service.CaseRecordManager;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 离院里程碑后幂等创建病案主档。
 */
@Component
@ConditionalOnBean(CaseRecordManager.class)
@RequiredArgsConstructor
public class DischargeCaseRecordBootstrapListener {

    private final CaseRecordManager caseRecordManager;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDischarge(DischargeMilestoneRecordedEvent event) {
        if (event.getEncounterId() == null) {
            return;
        }
        caseRecordManager.createIfAbsent(event.getEncounterId());
    }
}
