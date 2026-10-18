/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.bridge.integration.elep.mapper;

import com.zxplat.domain.infrastructure.integration.elep.ExtensionIn.CloneElepPrescriptionExtensionIn;
import com.zxplat.domain.infrastructure.integration.elep.ExtensionIn.ElepMedResultQueryExtensionIn;
import com.zxplat.domain.infrastructure.integration.elep.ExtensionIn.ElepQueryExtensionIn;
import com.zxplat.domain.infrastructure.integration.elep.ExtensionIn.ElepRevokeExtensionIn;
import com.zxplat.domain.infrastructure.integration.elep.ExtensionIn.ElepUploadExtensionIn;
import com.zxplat.domain.infrastructure.integration.elep.ExtensionIn.PatientElepHistoryQueryExtensionIn;
import com.zxplat.domain.infrastructure.integration.elep.ExtensionOut.ElepMedExtensionOut;
import com.zxplat.domain.infrastructure.integration.elep.ExtensionOut.ElepPageExtensionOut;
import com.zxplat.domain.infrastructure.integration.elep.ExtensionOut.CloneElepPrescriptionExtensionOut;
import com.zxplat.domain.infrastructure.integration.elep.ExtensionOut.ElepQueryExtensionOut;
import com.zxplat.domain.infrastructure.integration.elep.ExtensionOut.ElepRevokeExtensionOut;
import com.zxplat.domain.infrastructure.integration.elep.ExtensionOut.ElepRxNoQueryExtensionOut;
import com.zxplat.domain.infrastructure.integration.elep.ExtensionOut.ElepUploadExtensionOut;
import com.zxplat.domain.infrastructure.integration.elep.ExtensionOut.PatientElepPrescriptionSummaryQueryExtensionOut;
import com.zxplat.plugin.elep.domain.prescription.command.ElepClonePrescriptionCommand;
import com.zxplat.plugin.elep.domain.prescription.command.ElepMedResultQuery;
import com.zxplat.plugin.elep.domain.prescription.command.ElepPatientHistoryQuery;
import com.zxplat.plugin.elep.domain.prescription.result.ElepPatientHistoryItem;
import com.zxplat.plugin.elep.domain.prescription.result.ElepMedResultResult;
import com.zxplat.plugin.elep.domain.prescription.result.ElepPatientHistoryResult;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;

/** health-integration-contracts ↔ elep-jilin-domain DTO 映射。 */
public final class ElepIntegrationMapper {

    private ElepIntegrationMapper() {
    }

    public static com.zxplat.plugin.elep.domain.prescription.command.ElepUploadCommand toContractsUploadCommand(
            ElepUploadExtensionIn command) {
        if (command == null) {
            return null;
        }
        return new com.zxplat.plugin.elep.domain.prescription.command.ElepUploadCommand()
                .setEncounterId(command.getEncounterId())
                .setContractId(command.getContractId())
                .setPrescriptionNo(command.getPrescriptionNo())
                .setHospRxno(command.getHospRxno());
    }

    public static ElepUploadExtensionOut toExtensionUploadResult(
            com.zxplat.plugin.elep.domain.prescription.result.ElepUploadResult source) {
        if (source == null) {
            return new ElepUploadExtensionOut();
        }
        return new ElepUploadExtensionOut()
                .setSuccess(source.isSuccess())
                .setHiRxno(source.getHiRxno())
                .setRxTraceCode(source.getRxTraceCode())
                .setMessage(source.getMessage());
    }

    public static com.zxplat.plugin.elep.domain.prescription.command.ElepRevokeCommand toContractsRevokeCommand(
            ElepRevokeExtensionIn command) {
        if (command == null) {
            return null;
        }
        return new com.zxplat.plugin.elep.domain.prescription.command.ElepRevokeCommand()
                .setPrescriptionId(command.getPrescriptionId())
                .setContractId(command.getContractId())
                .setHiRxno(command.getHiRxno())
                .setRevokeReason(command.getRevokeReason());
    }

    public static ElepRevokeExtensionOut toExtensionRevokeResult(
            com.zxplat.plugin.elep.domain.prescription.result.ElepRevokeResult source) {
        if (source == null) {
            return new ElepRevokeExtensionOut();
        }
        return new ElepRevokeExtensionOut()
                .setSuccess(source.isSuccess())
                .setMessage(source.getMessage());
    }

    public static com.zxplat.plugin.elep.domain.prescription.command.ElepQueryCommand toContractsQueryCommand(
            ElepQueryExtensionIn command) {
        if (command == null) {
            return null;
        }
        return new com.zxplat.plugin.elep.domain.prescription.command.ElepQueryCommand()
                .setPrescriptionId(command.getPrescriptionId())
                .setContractId(command.getContractId())
                .setHiRxno(command.getHiRxno());
    }

    public static ElepQueryExtensionOut toExtensionQueryResult(
            com.zxplat.plugin.elep.domain.prescription.result.ElepQueryResult source) {
        if (source == null) {
            return new ElepQueryExtensionOut();
        }
        return new ElepQueryExtensionOut()
                .setSuccess(source.isSuccess())
                .setRxStasCodg(source.getRxStasCodg())
                .setRxUsedStasCodg(source.getRxUsedStasCodg())
                .setMessage(source.getMessage());
    }

    public static ElepMedResultQuery toContractsMedResultQuery(ElepMedResultQueryExtensionIn command) {
        if (command == null) {
            return null;
        }
        return new ElepMedResultQuery()
                .setContractId(command.getContractId())
                .setHiRxno(command.getHiRxno());
    }

    public static ElepMedExtensionOut toExtensionMedResult(ElepMedResultResult source) {
        if (source == null) {
            return new ElepMedExtensionOut();
        }
        return new ElepMedExtensionOut()
                .setSuccess(source.isSuccess())
                .setMedResultJson(source.getMedResultJson())
                .setMessage(source.getMessage());
    }

    public static ElepRxNoQueryExtensionOut toExtensionRxNoSnapshot(
            com.zxplat.plugin.elep.domain.snapshot.ElepRxNoSnapshot source) {
        if (source == null) {
            return null;
        }
        return new ElepRxNoQueryExtensionOut()
                .setHiRxno(source.getHiRxno())
                .setHospRxno(source.getHospRxno())
                .setLocalStatus(source.getLocalStatus());
    }

    public static ElepPatientHistoryQuery toContractsPatientHistoryQuery(PatientElepHistoryQueryExtensionIn query) {
        if (query == null) {
            return null;
        }
        return new ElepPatientHistoryQuery()
                .setPatientId(query.getPatientId())
                .setCurrentEncounterId(query.getCurrentEncounterId())
                .setContractBusNo(query.getContractBusNo())
                .setPage(query.getPage())
                .setPageSize(query.getPageSize());
    }

    public static ElepPageExtensionOut<PatientElepPrescriptionSummaryQueryExtensionOut> toExtensionPatientHistoryPage(
            ElepPatientHistoryResult source) {
        ElepPageExtensionOut<PatientElepPrescriptionSummaryQueryExtensionOut> result =
                new ElepPageExtensionOut<PatientElepPrescriptionSummaryQueryExtensionOut>()
                        .setContent(new ArrayList<>());
        if (source == null) {
            return result;
        }
        result.setTotal(source.getTotal());
        result.setPage(source.getPage());
        result.setPageSize(source.getPageSize());
        for (ElepPatientHistoryItem item : source.getItems()) {
            result.getContent().add(new PatientElepPrescriptionSummaryQueryExtensionOut()
                    .setPrescriptionId(item.getPrescriptionId())
                    .setHiRxno(item.getHiRxno())
                    .setHospRxno(item.getHospRxno())
                    .setLocalStatus(item.getLocalStatus())
                    .setPrscDrName(item.getPrscDrName())
                    .setPrscDeptName(item.getPrscDeptName())
                    .setMainDiagName(item.getMainDiagName())
                    .setDrugSummary(item.getDrugSummary())
                    .setPrscTime(item.getPrscTime()));
        }
        return result;
    }

    public static ElepClonePrescriptionCommand toContractsCloneCommand(CloneElepPrescriptionExtensionIn command) {
        if (command == null) {
            return null;
        }
        return new ElepClonePrescriptionCommand()
                .setSourcePrescriptionId(command.getSourcePrescriptionId())
                .setTargetEncounterId(command.getTargetEncounterId())
                .setPatientId(command.getPatientId())
                .setContractId(command.getContractId())
                .setInitRxno(command.getInitRxno())
                .setContinuationRx(StringUtils.isNotBlank(command.getInitRxno()));
    }

    public static CloneElepPrescriptionExtensionOut toExtensionPrescriptionDraftDto(
            com.zxplat.plugin.elep.domain.prescription.result.ElepPrescriptionDraft source) {
        if (source == null) {
            return new CloneElepPrescriptionExtensionOut();
        }
        return new CloneElepPrescriptionExtensionOut()
                .setPrescriptionId(source.getPrescriptionId())
                .setHospRxno(source.getHospRxno())
                .setInitRxno(source.getInitRxno())
                .setLocalStatus(source.getLocalStatus());
    }
}
