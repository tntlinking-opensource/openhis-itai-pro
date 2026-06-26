/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.elep;

import com.openhis.health.integration.elep.command.CloneElepPrescriptionCommand;
import com.openhis.health.integration.elep.command.ElepMedResultQueryCommand;
import com.openhis.health.integration.elep.command.ElepQueryCommand;
import com.openhis.health.integration.elep.command.ElepRevokeCommand;
import com.openhis.health.integration.elep.command.ElepUploadCommand;
import com.openhis.health.integration.elep.command.PatientElepHistoryQuery;
import com.openhis.health.integration.elep.result.ElepMedResult;
import com.openhis.health.integration.elep.result.ElepPageResult;
import com.openhis.health.integration.elep.result.ElepPrescriptionDraftDto;
import com.openhis.health.integration.elep.result.ElepQueryResult;
import com.openhis.health.integration.elep.result.ElepRevokeResult;
import com.openhis.health.integration.elep.result.ElepRxNoSnapshot;
import com.openhis.health.integration.elep.result.ElepUploadResult;
import com.openhis.health.integration.elep.result.PatientElepPrescriptionSummaryDto;

import java.util.Collections;
import java.util.Optional;

/**
 * 医生站 ↔ 第三方电子处方扩展点（省份/厂商无关）。
 */
public interface ElepPrescriptionExtension {

    default boolean isEnabled() {
        return false;
    }

    default boolean supportsPrescription(Long prescriptionGroupId) {
        return false;
    }

    default ElepUploadResult upload(ElepUploadCommand command) {
        throw new UnsupportedOperationException("电子处方未装配");
    }

    default ElepRevokeResult revoke(ElepRevokeCommand command) {
        throw new UnsupportedOperationException("电子处方未装配");
    }

    default ElepQueryResult query(ElepQueryCommand command) {
        throw new UnsupportedOperationException("电子处方未装配");
    }

    default ElepMedResult queryMedResult(ElepMedResultQueryCommand command) {
        throw new UnsupportedOperationException("电子处方未装配");
    }

    default Optional<ElepRxNoSnapshot> findRxNoByPrescription(Long prescriptionGroupId) {
        return Optional.empty();
    }

    /** 2204 挂接：按 HIS {@code prescription_no} 查已上传方的 {@code hi_rxno}。 */
    default Optional<ElepRxNoSnapshot> findRxNoByPrescriptionNo(String prescriptionNo) {
        return Optional.empty();
    }

    default ElepPageResult<PatientElepPrescriptionSummaryDto> listPatientHistory(PatientElepHistoryQuery query) {
        return ElepPageResult.empty();
    }

    default ElepPrescriptionDraftDto cloneToNewEncounter(CloneElepPrescriptionCommand command) {
        throw new UnsupportedOperationException("电子处方未装配");
    }
}
