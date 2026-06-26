/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.application.shared.assembler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.whale.mrms.application.homepage.dto.DiagnosisLineDto;
import com.openhis.whale.mrms.application.homepage.dto.HomepageDto;
import com.openhis.whale.mrms.application.homepage.dto.ProcedureLineDto;
import com.openhis.whale.mrms.application.homepage.request.SaveHomepageRequest;
import com.openhis.whale.mrms.domain.entity.CaseRecordHomepage;
import com.openhis.whale.mrms.domain.entity.HomepageDiagnosis;
import com.openhis.whale.mrms.domain.entity.HomepageProcedure;
import com.openhis.whale.mrms.domain.model.HomepageSaveCommand;
import com.openhis.whale.mrms.domain.service.FieldSourcesJsonHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 病案首页实体与 DTO 装配器。
 */
@Component
@RequiredArgsConstructor
public class HomepageAssembler {

    private static final TypeReference<Map<String, Object>> EXT_TYPE = new TypeReference<>() {
    };

    private final FieldSourcesJsonHelper fieldSourcesJsonHelper;
    private final ObjectMapper objectMapper;

    /**
     * 首页实体与子表转 DTO。
     *
     * @param homepage 首页实体
     * @param diagnoses 诊断行
     * @param procedures 手术行
     * @return 首页 DTO
     */
    public HomepageDto toDto(
            CaseRecordHomepage homepage,
            List<HomepageDiagnosis> diagnoses,
            List<HomepageProcedure> procedures) {
        HomepageDto dto = new HomepageDto();
        dto.setCaseRecordId(homepage.getCaseRecordId());
        dto.setVersionNo(homepage.getVersionNo());
        dto.setFrozen(homepage.getFrozen());
        dto.setIptMedcasHmpgSn(homepage.getIptMedcasHmpgSn());
        dto.setPsnName(homepage.getPsnName());
        dto.setGendCode(homepage.getGendCode());
        dto.setBrdy(homepage.getBrdy());
        dto.setNtlyCode(homepage.getNtlyCode());
        dto.setNatyCode(homepage.getNatyCode());
        dto.setCertno(homepage.getCertno());
        dto.setPrfsCode(homepage.getPrfsCode());
        dto.setMrgStasCode(homepage.getMrgStasCode());
        dto.setCurrAddr(homepage.getCurrAddr());
        dto.setPsnTel(homepage.getPsnTel());
        dto.setConerName(homepage.getConerName());
        dto.setConerTel(homepage.getConerTel());
        dto.setIptNo(homepage.getIptNo());
        dto.setAdmWayCode(homepage.getAdmWayCode());
        dto.setAdmCatyCode(homepage.getAdmCatyCode());
        dto.setAdmCatyName(homepage.getAdmCatyName());
        dto.setAdmTime(homepage.getAdmTime());
        dto.setDscgCatyCode(homepage.getDscgCatyCode());
        dto.setDscgCatyName(homepage.getDscgCatyName());
        dto.setDscgTime(homepage.getDscgTime());
        dto.setIptDays(homepage.getIptDays());
        dto.setTransferDeptSummary(homepage.getTransferDeptSummary());
        dto.setDscgWayCode(homepage.getDscgWayCode());
        dto.setDeptDirectorName(homepage.getDeptDirectorName());
        dto.setChfdrName(homepage.getChfdrName());
        dto.setAtddrName(homepage.getAtddrName());
        dto.setIptDrName(homepage.getIptDrName());
        dto.setRespNursName(homepage.getRespNursName());
        dto.setFieldSources(fieldSourcesJsonHelper.parse(homepage.getFieldSourcesJson()));
        dto.setExtension(parseExtension(homepage.getExtensionJson()));
        dto.setDiagnoses(toDiagnosisDtos(diagnoses));
        dto.setProcedures(toProcedureDtos(procedures));
        return dto;
    }

    /**
     * 保存请求转领域命令。
     *
     * @param request 保存请求
     * @param operatorId 操作人
     * @return 领域命令
     */
    public HomepageSaveCommand toSaveCommand(SaveHomepageRequest request, Long operatorId) {
        HomepageSaveCommand command = new HomepageSaveCommand();
        command.setCaseRecordId(request.getCaseRecordId());
        command.setOperatorId(operatorId);
        command.setPsnName(request.getPsnName());
        command.setGendCode(request.getGendCode());
        command.setBrdy(request.getBrdy());
        command.setCertno(request.getCertno());
        command.setIptNo(request.getIptNo());
        command.setAdmWayCode(request.getAdmWayCode());
        command.setAdmCatyCode(request.getAdmCatyCode());
        command.setAdmTime(request.getAdmTime());
        command.setDscgCatyCode(request.getDscgCatyCode());
        command.setDscgTime(request.getDscgTime());
        command.setIptDays(request.getIptDays());
        command.setDscgWayCode(request.getDscgWayCode());
        command.setChfdrName(request.getChfdrName());
        command.setAtddrName(request.getAtddrName());
        command.setIptDrName(request.getIptDrName());
        command.setExtension(request.getExtension());
        command.setDiagnoses(toDiagnosisEntities(request.getDiagnoses()));
        command.setProcedures(toProcedureEntities(request.getProcedures()));
        return command;
    }

    /**
     * 诊断 DTO 转领域实体（保存用）。
     *
     * @param lines DTO 列表
     * @return 实体列表
     */
    public List<HomepageDiagnosis> toDiagnosisEntities(List<DiagnosisLineDto> lines) {
        List<HomepageDiagnosis> result = new ArrayList<>();
        if (lines == null) {
            return result;
        }
        for (DiagnosisLineDto line : lines) {
            HomepageDiagnosis entity = new HomepageDiagnosis();
            entity.setDiagTypeCode(line.getDiagTypeCode());
            entity.setSortNo(line.getSortNo());
            entity.setDiagCode(line.getDiagCode());
            entity.setDiagName(line.getDiagName());
            entity.setAdmCondCode(line.getAdmCondCode());
            entity.setMaindiagFlag(line.getMaindiagFlag());
            entity.setValiFlag(line.getValiFlag());
            result.add(entity);
        }
        return result;
    }

    /**
     * 手术 DTO 转领域实体（保存用）。
     *
     * @param lines DTO 列表
     * @return 实体列表
     */
    public List<HomepageProcedure> toProcedureEntities(List<ProcedureLineDto> lines) {
        List<HomepageProcedure> result = new ArrayList<>();
        if (lines == null) {
            return result;
        }
        for (ProcedureLineDto line : lines) {
            HomepageProcedure entity = new HomepageProcedure();
            entity.setSortNo(line.getSortNo());
            entity.setOprnOprtCode(line.getOprnOprtCode());
            entity.setOprnOprtName(line.getOprnOprtName());
            entity.setOprnDate(line.getOprnDate());
            entity.setMainOprnFlag(line.getMainOprnFlag());
            entity.setValiFlag(line.getValiFlag());
            result.add(entity);
        }
        return result;
    }

    private List<DiagnosisLineDto> toDiagnosisDtos(List<HomepageDiagnosis> rows) {
        List<DiagnosisLineDto> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (HomepageDiagnosis row : rows) {
            DiagnosisLineDto dto = new DiagnosisLineDto();
            dto.setId(row.getId());
            dto.setDiagTypeCode(row.getDiagTypeCode());
            dto.setSortNo(row.getSortNo());
            dto.setDiagCode(row.getDiagCode());
            dto.setDiagName(row.getDiagName());
            dto.setAdmCondCode(row.getAdmCondCode());
            dto.setMaindiagFlag(row.getMaindiagFlag());
            dto.setValiFlag(row.getValiFlag());
            result.add(dto);
        }
        return result;
    }

    private List<ProcedureLineDto> toProcedureDtos(List<HomepageProcedure> rows) {
        List<ProcedureLineDto> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (HomepageProcedure row : rows) {
            ProcedureLineDto dto = new ProcedureLineDto();
            dto.setId(row.getId());
            dto.setSortNo(row.getSortNo());
            dto.setOprnOprtCode(row.getOprnOprtCode());
            dto.setOprnOprtName(row.getOprnOprtName());
            dto.setOprnDate(row.getOprnDate());
            dto.setMainOprnFlag(row.getMainOprnFlag());
            dto.setValiFlag(row.getValiFlag());
            result.add(dto);
        }
        return result;
    }

    private Map<String, Object> parseExtension(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, EXT_TYPE);
            if (map == null) {
                return new HashMap<>();
            }
            return map;
        } catch (Exception ex) {
            return new HashMap<>();
        }
    }
}
