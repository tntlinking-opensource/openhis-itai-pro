package com.openhis.health.application.mrms.adapter;

import com.openhis.health.domain.custom.encounterhistory.entity.EncounterHistory;
import com.openhis.health.domain.custom.encounterhistory.repository.EncounterHistoryRepository;
import com.openhis.whale.mrms.domain.port.EncounterHistoryPort;
import com.openhis.whale.mrms.domain.port.model.TransferLineSnapshot;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * health 对 MRMS {@link EncounterHistoryPort} 的实现。
 */
@Component
public class HealthMrmsEncounterHistoryAdapter implements EncounterHistoryPort {

    private static final String MILESTONE_TRANSFER = "transfer";
    private static final String MILESTONE_DISCHARGE = "discharge";

    private final EncounterHistoryRepository encounterHistoryRepository;

    public HealthMrmsEncounterHistoryAdapter(EncounterHistoryRepository encounterHistoryRepository) {
        this.encounterHistoryRepository = encounterHistoryRepository;
    }

    @Override
    public List<TransferLineSnapshot> listTransfers(long encounterId) {
        List<EncounterHistory> histories = encounterHistoryRepository.findAllByEncounterIdOrderByCreatedAtAsc(encounterId);
        List<TransferLineSnapshot> result = new ArrayList<>();
        for (EncounterHistory history : histories) {
            if (!MILESTONE_TRANSFER.equalsIgnoreCase(history.getName())) {
                continue;
            }
            TransferLineSnapshot snapshot = new TransferLineSnapshot();
            snapshot.setTransferAt(history.getCreatedAt());
            if (history.getOriginalOrganizationId() != null) {
                snapshot.setFromDeptCode(String.valueOf(history.getOriginalOrganizationId()));
                snapshot.setFromDeptName(snapshot.getFromDeptCode());
            }
            if (history.getTargetOrganizationId() != null) {
                snapshot.setToDeptCode(String.valueOf(history.getTargetOrganizationId()));
                snapshot.setToDeptName(snapshot.getToDeptCode());
            }
            result.add(snapshot);
        }
        return result;
    }

    @Override
    public Optional<String> resolveDischargeWayCode(long encounterId) {
        return encounterHistoryRepository
                .findFirstByEncounterIdAndNameOrderByCreatedAtDesc(encounterId, MILESTONE_DISCHARGE)
                .map(EncounterHistory::getOutWayCode);
    }
}
