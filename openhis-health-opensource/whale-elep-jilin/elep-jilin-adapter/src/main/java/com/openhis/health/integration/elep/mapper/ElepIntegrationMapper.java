/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.elep.mapper;

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
import com.openhis.whale.elep.jilin.contracts.command.ElepClonePrescriptionCommand;
import com.openhis.whale.elep.jilin.contracts.command.ElepMedResultQuery;
import com.openhis.whale.elep.jilin.contracts.command.ElepPatientHistoryQuery;
import com.openhis.whale.elep.jilin.contracts.dto.ElepPatientHistoryItem;
import com.openhis.whale.elep.jilin.contracts.result.ElepMedResultResult;
import com.openhis.whale.elep.jilin.contracts.result.ElepPatientHistoryResult;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;

/** health-integration-contracts ↔ elep-jilin-contracts DTO 映射。 */
public final class ElepIntegrationMapper {

    private ElepIntegrationMapper() {
    }

    public static com.openhis.whale.elep.jilin.contracts.command.ElepUploadCommand toContractsUploadCommand(
            ElepUploadCommand command) {
        if (command == null) {
            return null;
        }
        return new com.openhis.whale.elep.jilin.contracts.command.ElepUploadCommand()
                .setEncounterId(command.getEncounterId())
                .setContractId(command.getContractId())
                .setPrescriptionNo(command.getPrescriptionNo())
                .setHospRxno(command.getHospRxno());
    }

    public static ElepUploadResult toExtensionUploadResult(
            com.openhis.whale.elep.jilin.contracts.result.ElepUploadResult source) {
        if (source == null) {
            return new ElepUploadResult();
        }
        return new ElepUploadResult()
                .setSuccess(source.isSuccess())
                .setHiRxno(source.getHiRxno())
                .setRxTraceCode(source.getRxTraceCode())
                .setMessage(source.getMessage());
    }

    public static com.openhis.whale.elep.jilin.contracts.command.ElepRevokeCommand toContractsRevokeCommand(
            ElepRevokeCommand command) {
        if (command == null) {
            return null;
        }
        return new com.openhis.whale.elep.jilin.contracts.command.ElepRevokeCommand()
                .setPrescriptionId(command.getPrescriptionId())
                .setContractId(command.getContractId())
                .setHiRxno(command.getHiRxno())
                .setRevokeReason(command.getRevokeReason());
    }

    public static ElepRevokeResult toExtensionRevokeResult(
            com.openhis.whale.elep.jilin.contracts.result.ElepRevokeResult source) {
        if (source == null) {
            return new ElepRevokeResult();
        }
        return new ElepRevokeResult()
                .setSuccess(source.isSuccess())
                .setMessage(source.getMessage());
    }

    public static com.openhis.whale.elep.jilin.contracts.command.ElepQueryCommand toContractsQueryCommand(
            ElepQueryCommand command) {
        if (command == null) {
            return null;
        }
        return new com.openhis.whale.elep.jilin.contracts.command.ElepQueryCommand()
                .setPrescriptionId(command.getPrescriptionId())
                .setContractId(command.getContractId())
                .setHiRxno(command.getHiRxno());
    }

    public static ElepQueryResult toExtensionQueryResult(
            com.openhis.whale.elep.jilin.contracts.result.ElepQueryResult source) {
        if (source == null) {
            return new ElepQueryResult();
        }
        return new ElepQueryResult()
                .setSuccess(source.isSuccess())
                .setRxStasCodg(source.getRxStasCodg())
                .setRxUsedStasCodg(source.getRxUsedStasCodg())
                .setMessage(source.getMessage());
    }

    public static ElepMedResultQuery toContractsMedResultQuery(ElepMedResultQueryCommand command) {
        if (command == null) {
            return null;
        }
        return new ElepMedResultQuery()
                .setContractId(command.getContractId())
                .setHiRxno(command.getHiRxno());
    }

    public static ElepMedResult toExtensionMedResult(ElepMedResultResult source) {
        if (source == null) {
            return new ElepMedResult();
        }
        return new ElepMedResult()
                .setSuccess(source.isSuccess())
                .setMedResultJson(source.getMedResultJson())
                .setMessage(source.getMessage());
    }

    public static ElepRxNoSnapshot toExtensionRxNoSnapshot(
            com.openhis.whale.elep.jilin.contracts.snapshot.ElepRxNoSnapshot source) {
        if (source == null) {
            return null;
        }
        return new ElepRxNoSnapshot()
                .setHiRxno(source.getHiRxno())
                .setHospRxno(source.getHospRxno())
                .setLocalStatus(source.getLocalStatus());
    }

    public static ElepPatientHistoryQuery toContractsPatientHistoryQuery(PatientElepHistoryQuery query) {
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

    public static ElepPageResult<PatientElepPrescriptionSummaryDto> toExtensionPatientHistoryPage(
            ElepPatientHistoryResult source) {
        ElepPageResult<PatientElepPrescriptionSummaryDto> result =
                new ElepPageResult<PatientElepPrescriptionSummaryDto>()
                        .setContent(new ArrayList<>());
        if (source == null) {
            return result;
        }
        result.setTotal(source.getTotal());
        result.setPage(source.getPage());
        result.setPageSize(source.getPageSize());
        for (ElepPatientHistoryItem item : source.getItems()) {
            result.getContent().add(new PatientElepPrescriptionSummaryDto()
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

    public static ElepClonePrescriptionCommand toContractsCloneCommand(CloneElepPrescriptionCommand command) {
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

    public static ElepPrescriptionDraftDto toExtensionPrescriptionDraftDto(
            com.openhis.whale.elep.jilin.contracts.dto.ElepPrescriptionDraftDto source) {
        if (source == null) {
            return new ElepPrescriptionDraftDto();
        }
        return new ElepPrescriptionDraftDto()
                .setPrescriptionId(source.getPrescriptionId())
                .setHospRxno(source.getHospRxno())
                .setInitRxno(source.getInitRxno())
                .setLocalStatus(source.getLocalStatus());
    }
}
