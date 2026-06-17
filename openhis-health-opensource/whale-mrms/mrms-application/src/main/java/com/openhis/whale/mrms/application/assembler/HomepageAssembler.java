package com.openhis.whale.mrms.application.assembler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.whale.mrms.application.dto.DiagnosisLineDto;
import com.openhis.whale.mrms.application.dto.HomepageDto;
import com.openhis.whale.mrms.application.dto.IcdItemDto;
import com.openhis.whale.mrms.application.dto.ProcedureLineDto;
import com.openhis.whale.mrms.domain.entity.CaseRecordHomepage;
import com.openhis.whale.mrms.domain.entity.HomepageDiagnosis;
import com.openhis.whale.mrms.domain.entity.HomepageProcedure;
import com.openhis.whale.mrms.port.model.IcdItemSnapshot;
import com.openhis.whale.mrms.domain.service.FieldSourcesJsonHelper;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class HomepageAssembler {

    private static final TypeReference<Map<String, Object>> EXT_TYPE = new TypeReference<>() {};

    private final FieldSourcesJsonHelper fieldSourcesJsonHelper;
    private final ObjectMapper objectMapper;

    public HomepageAssembler(FieldSourcesJsonHelper fieldSourcesJsonHelper, ObjectMapper objectMapper) {
        this.fieldSourcesJsonHelper = fieldSourcesJsonHelper;
        this.objectMapper = objectMapper;
    }

    public HomepageDto toDto(CaseRecordHomepage homepage, List<HomepageDiagnosis> diagnoses, List<HomepageProcedure> procedures) {
        HomepageDto dto = new HomepageDto();
        dto.setCaseRecordId(homepage.getCaseRecordId());
        dto.setVersionNo(homepage.getVersionNo());
        dto.setFrozen(homepage.getFrozen());
        dto.setPsnName(homepage.getPsnName());
        dto.setGendCode(homepage.getGendCode());
        dto.setBrdy(homepage.getBrdy());
        dto.setCertno(homepage.getCertno());
        dto.setNtlyCode(homepage.getNtlyCode());
        dto.setNatyCode(homepage.getNatyCode());
        dto.setPrfsCode(homepage.getPrfsCode());
        dto.setMrgStasCode(homepage.getMrgStasCode());
        dto.setCurrAddr(homepage.getCurrAddr());
        dto.setPsnTel(homepage.getPsnTel());
        dto.setConerName(homepage.getConerName());
        dto.setConerTel(homepage.getConerTel());
        dto.setConerAddr(homepage.getConerAddr());
        dto.setIptNo(homepage.getIptNo());
        dto.setAdmWayCode(homepage.getAdmWayCode());
        dto.setAdmCatyCode(homepage.getAdmCatyCode());
        dto.setAdmCatyName(homepage.getAdmCatyName());
        dto.setDscgCatyCode(homepage.getDscgCatyCode());
        dto.setDscgCatyName(homepage.getDscgCatyName());
        dto.setAdmTime(homepage.getAdmTime());
        dto.setDscgTime(homepage.getDscgTime());
        dto.setActIptDays(homepage.getActIptDays());
        dto.setDscgWayCode(homepage.getDscgWayCode());
        dto.setTransferDeptSummary(homepage.getTransferDeptSummary());
        dto.setDeptDirectorName(homepage.getDeptDirectorName());
        dto.setChfdrName(homepage.getChfdrName());
        dto.setAtddrName(homepage.getAtddrName());
        dto.setIptDrName(homepage.getIptDrName());
        dto.setRespNursName(homepage.getRespNursName());
        dto.setFieldSources(fieldSourcesJsonHelper.parse(homepage.getFieldSourcesJson()));
        dto.setExtension(parseExtension(homepage.getExtensionJson()));
        dto.setDiagnoses(diagnoses.stream().map(this::toDiagnosisDto).collect(Collectors.toList()));
        dto.setProcedures(procedures.stream().map(this::toProcedureDto).collect(Collectors.toList()));
        return dto;
    }

    public DiagnosisLineDto toDiagnosisDto(HomepageDiagnosis entity) {
        DiagnosisLineDto dto = new DiagnosisLineDto();
        dto.setId(entity.getId());
        dto.setDiagTypeCode(entity.getDiagTypeCode());
        dto.setSortNo(entity.getSortNo());
        dto.setDiagCode(entity.getDiagCode());
        dto.setDiagName(entity.getDiagName());
        dto.setAdmCondCode(entity.getAdmCondCode());
        return dto;
    }

    public ProcedureLineDto toProcedureDto(HomepageProcedure entity) {
        ProcedureLineDto dto = new ProcedureLineDto();
        dto.setId(entity.getId());
        dto.setSortNo(entity.getSortNo());
        dto.setOprnOprtCode(entity.getOprnOprtCode());
        dto.setOprnOprtName(entity.getOprnOprtName());
        dto.setOprnDate(entity.getOprnDate());
        dto.setOprnLevelCode(entity.getOprnLevelCode());
        dto.setOprnIncisionCode(entity.getOprnIncisionCode());
        dto.setHealGradeCode(entity.getHealGradeCode());
        dto.setAnstWayCode(entity.getAnstWayCode());
        return dto;
    }

    public IcdItemDto toIcdDto(IcdItemSnapshot snapshot) {
        IcdItemDto dto = new IcdItemDto();
        dto.setId(snapshot.getId());
        dto.setCode(snapshot.getCode());
        dto.setName(snapshot.getName());
        dto.setPyStr(snapshot.getPyStr());
        dto.setWbStr(snapshot.getWbStr());
        return dto;
    }

    private Map<String, Object> parseExtension(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, EXT_TYPE);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
