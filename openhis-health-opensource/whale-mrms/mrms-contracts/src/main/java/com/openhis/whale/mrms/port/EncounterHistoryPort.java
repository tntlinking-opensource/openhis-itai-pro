package com.openhis.whale.mrms.port;

import com.openhis.whale.mrms.port.model.TransferLineSnapshot;

import java.util.List;
import java.util.Optional;

public interface EncounterHistoryPort {

    List<TransferLineSnapshot> listTransfers(long encounterId);

    Optional<String> resolveDischargeWayCode(long encounterId);
}
