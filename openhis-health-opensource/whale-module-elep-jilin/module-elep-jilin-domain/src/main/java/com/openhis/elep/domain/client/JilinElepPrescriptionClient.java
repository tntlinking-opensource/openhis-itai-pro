/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.client;

import com.openhis.elep.domain.prescription.command.ElepClonePrescriptionCommand;
import com.openhis.elep.domain.prescription.command.ElepMedResultQuery;
import com.openhis.elep.domain.prescription.result.ElepMedResultResult;
import com.openhis.elep.domain.prescription.command.ElepPatientHistoryQuery;
import com.openhis.elep.domain.prescription.result.ElepPatientHistoryResult;
import com.openhis.elep.domain.prescription.result.ElepPrescriptionDraft;
import com.openhis.elep.domain.prescription.command.ElepQueryCommand;
import com.openhis.elep.domain.prescription.result.ElepQueryResult;
import com.openhis.elep.domain.prescription.command.ElepRevokeCommand;
import com.openhis.elep.domain.prescription.result.ElepRevokeResult;
import com.openhis.elep.domain.snapshot.ElepRxNoSnapshot;
import com.openhis.elep.domain.prescription.command.ElepUploadCommand;
import com.openhis.elep.domain.prescription.result.ElepUploadResult;

import java.util.Optional;

/**
 * 电子处方 ↔ 吉林省平台 Client（由 elep-jilin-bridge 实现）。
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

    ElepPrescriptionDraft cloneToNewEncounter(ElepClonePrescriptionCommand command);
}
