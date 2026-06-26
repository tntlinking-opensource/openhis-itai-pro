/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.application.support.noop;

import com.openhis.whale.mrms.contracts.reader.HealthEncounterHistoryReader;
import com.openhis.whale.mrms.contracts.snapshot.TransferLineSnapshot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 无 HIS 适配器时的 {@link HealthEncounterHistoryReader} 空实现。
 */
@Component
@ConditionalOnMissingBean(HealthEncounterHistoryReader.class)
public class NoOpHealthEncounterHistoryReader implements HealthEncounterHistoryReader {

    /**
     * {@inheritDoc}
     */
    @Override
    public String resolveDischargeWayCode(Long encounterId) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<TransferLineSnapshot> listTransfers(Long encounterId) {
        return Collections.emptyList();
    }
}
