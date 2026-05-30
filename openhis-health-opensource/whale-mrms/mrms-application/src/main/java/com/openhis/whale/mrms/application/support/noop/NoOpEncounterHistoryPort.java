package com.openhis.whale.mrms.application.support.noop;

import com.openhis.whale.mrms.domain.port.EncounterHistoryPort;
import com.openhis.whale.mrms.domain.port.model.TransferLineSnapshot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
@ConditionalOnMissingBean(EncounterHistoryPort.class)
public class NoOpEncounterHistoryPort implements EncounterHistoryPort {

    @Override
    public List<TransferLineSnapshot> listTransfers(long encounterId) {
        return Collections.emptyList();
    }

    @Override
    public Optional<String> resolveDischargeWayCode(long encounterId) {
        return Optional.empty();
    }
}
