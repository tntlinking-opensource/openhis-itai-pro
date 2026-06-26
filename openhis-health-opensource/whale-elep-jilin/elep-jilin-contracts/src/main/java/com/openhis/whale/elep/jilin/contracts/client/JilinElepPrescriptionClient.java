/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.elep.jilin.contracts.client;

import com.openhis.whale.elep.jilin.contracts.command.ElepClonePrescriptionCommand;
import com.openhis.whale.elep.jilin.contracts.command.ElepMedResultQuery;
import com.openhis.whale.elep.jilin.contracts.result.ElepMedResultResult;
import com.openhis.whale.elep.jilin.contracts.command.ElepPatientHistoryQuery;
import com.openhis.whale.elep.jilin.contracts.result.ElepPatientHistoryResult;
import com.openhis.whale.elep.jilin.contracts.dto.ElepPrescriptionDraftDto;
import com.openhis.whale.elep.jilin.contracts.command.ElepQueryCommand;
import com.openhis.whale.elep.jilin.contracts.result.ElepQueryResult;
import com.openhis.whale.elep.jilin.contracts.command.ElepRevokeCommand;
import com.openhis.whale.elep.jilin.contracts.result.ElepRevokeResult;
import com.openhis.whale.elep.jilin.contracts.snapshot.ElepRxNoSnapshot;
import com.openhis.whale.elep.jilin.contracts.command.ElepUploadCommand;
import com.openhis.whale.elep.jilin.contracts.result.ElepUploadResult;

import java.util.Optional;

/**
 * 电子处方 ↔ 吉林省平台 Port（由 elep-jilin-adapter 实现）。
 */
public interface JilinElepPrescriptionClient {

    /** 合同是否走电子处方。 */
    boolean supportsContract(Long contractId);

    ElepUploadResult upload(ElepUploadCommand command);

    ElepRevokeResult revoke(ElepRevokeCommand command);

    ElepQueryResult query(ElepQueryCommand command);

    ElepMedResultResult queryMedResult(ElepMedResultQuery query);

    Optional<ElepRxNoSnapshot> findRxNoByPrescription(Long prescriptionGroupId);

    Optional<ElepRxNoSnapshot> findRxNoByPrescriptionNo(String prescriptionNo);

    ElepPatientHistoryResult listPatientHistory(ElepPatientHistoryQuery query);

    ElepPrescriptionDraftDto cloneToNewEncounter(ElepClonePrescriptionCommand command);
}
