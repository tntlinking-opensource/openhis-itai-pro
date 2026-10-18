/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.caserecord.homepage.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zxplat.application.mrms.caserecord.homepage.dto.BldLineDto;
import com.zxplat.application.mrms.caserecord.homepage.dto.DiagnosisLineDto;
import com.zxplat.application.mrms.caserecord.homepage.dto.HomepageDto;
import com.zxplat.application.mrms.caserecord.homepage.dto.IcuLineDto;
import com.zxplat.application.mrms.caserecord.homepage.dto.ProcedureLineDto;
import com.zxplat.application.mrms.caserecord.homepage.request.SaveHomepageRequest;
import com.zxplat.application.shared.diagnosis.EncounterDiagnosisSourceHintResolver;
import com.zxplat.domain.clinical.conditiondefinition.queries.model.ClinicalDiagnosisPresentationQueryModel;
import com.zxplat.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.zxplat.domain.mrms.caserecord.entity.CaseRecordHomepage;
import com.zxplat.domain.mrms.caserecord.entity.HomepageBld;
import com.zxplat.domain.mrms.caserecord.entity.HomepageDiagnosis;
import com.zxplat.domain.mrms.caserecord.entity.HomepageIcu;
import com.zxplat.domain.mrms.caserecord.entity.HomepageProcedure;
import com.zxplat.domain.mrms.caserecord.command.HomepageSaveCommand;
import com.zxplat.domain.share.constants.DoctorStationDiagnosisConstants;
import com.zxplat.domain.share.utils.FieldSourcesJsonUtil;
import com.zxplat.domain.share.utils.HomepageRegionCodesTextUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 病案首页实体 ↔ DTO/命令映射（homepage IF {@code mapper}）。
 *
 * <p>读路径：{@link CaseRecordHomepage} + 诊断/手术子表 → {@link HomepageDto}；
 * 写路径：{@link SaveHomepageRequest} → {@link HomepageSaveCommand}。
 * {@code field_sources_json} 经 {@link FieldSourcesJsonUtil} 解析；
 * 省市区 codes TEXT 列经 {@link HomepageRegionCodesTextUtil} List↔斜杠拼接字符串。</p>
 */
@Component
@RequiredArgsConstructor
public class HomepageMapper {

    private final ObjectMapper objectMapper;

    private final ConditionDefinitionQueryService conditionDefinitionQueryService;

    private final EncounterDiagnosisSourceHintResolver encounterDiagnosisSourceHintResolver;

    /**
     * 首页实体与子表转 DTO。
     *
     * @param homepage     首页实体
     * @param diagnoses    诊断行
     * @param procedures   手术行
     * @param icus         重症监护行
     * @param blds         输血行
     * @param encounterId  就诊主键；用于按医生实际开立目录来源区分国临/医保同码；可空
     * @return 首页 DTO
     */
    public HomepageDto toDto(
            CaseRecordHomepage homepage,
            List<HomepageDiagnosis> diagnoses,
            List<HomepageProcedure> procedures,
            List<HomepageIcu> icus,
            List<HomepageBld> blds,
            Long encounterId) {
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
        dto.setConerRltsCode(homepage.getConerRltsCode());
        dto.setConerAddr(homepage.getConerAddr());
        dto.setTrtTypeCode(homepage.getTrtTypeCode());
        dto.setClinicalPathCode(homepage.getClinicalPathCode());
        dto.setTcmPrepCode(homepage.getTcmPrepCode());
        dto.setTcmDeviceCode(homepage.getTcmDeviceCode());
        dto.setTcmTechCode(homepage.getTcmTechCode());
        dto.setSyndromeNurseCode(homepage.getSyndromeNurseCode());
        dto.setSpgaNurscareDays(homepage.getSpgaNurscareDays());
        dto.setLv1NurscareDays(homepage.getLv1NurscareDays());
        dto.setScdNurscareDays(homepage.getScdNurscareDays());
        dto.setLv3NurscareDays(homepage.getLv3NurscareDays());
        dto.setOtpWmDise(homepage.getOtpWmDise());
        dto.setWmDiseCode(homepage.getWmDiseCode());
        dto.setOtpTcmDise(homepage.getOtpTcmDise());
        dto.setTcmDiseCode(homepage.getTcmDiseCode());
        dto.setBrnDamgBfadmComaDura(homepage.getBrnDamgBfadmComaDura());
        dto.setBrnDamgAfadmComaDura(homepage.getBrnDamgAfadmComaDura());
        dto.setVentUsedDura(homepage.getVentUsedDura());
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
        dto.setPayWayCode(homepage.getPayWayCode());
        dto.setHealthCardNo(homepage.getHealthCardNo());
        dto.setAgeYear(homepage.getAgeYear());
        dto.setAgeMonth(homepage.getAgeMonth());
        dto.setBirplcCodes(HomepageRegionCodesTextUtil.textToStringList(homepage.getBirplcCodes()));
        dto.setBirplcName(homepage.getBirplcName());
        dto.setNaplCodes(HomepageRegionCodesTextUtil.textToStringList(homepage.getNaplCodes()));
        dto.setNaplName(homepage.getNaplName());
        dto.setCurrAddrCodes(HomepageRegionCodesTextUtil.textToStringList(homepage.getCurrAddrCodes()));
        dto.setCurrAddrDetail(homepage.getCurrAddrDetail());
        dto.setCurrAddrPoscode(homepage.getCurrAddrPoscode());
        dto.setResdAddrCodes(HomepageRegionCodesTextUtil.textToStringList(homepage.getResdAddrCodes()));
        dto.setResdAddrDetail(homepage.getResdAddrDetail());
        dto.setResdAddrPoscode(homepage.getResdAddrPoscode());
        dto.setEmprAddr(homepage.getEmprAddr());
        dto.setEmprTel(homepage.getEmprTel());
        dto.setEmprPoscode(homepage.getEmprPoscode());
        dto.setAdmWard(homepage.getAdmWard());
        dto.setAdmBed(homepage.getAdmBed());
        dto.setDscgWard(homepage.getDscgWard());
        dto.setDscgBed(homepage.getDscgBed());
        dto.setDrugAllergyCode(homepage.getDrugAllergyCode());
        dto.setAllergyDrugName(homepage.getAllergyDrugName());
        dto.setPathologyNo(homepage.getPathologyNo());
        dto.setMainDiagCureCode(homepage.getMainDiagCureCode());
        dto.setRescueCount(homepage.getRescueCount());
        dto.setRescueSuccessCount(homepage.getRescueSuccessCount());
        dto.setClinicPathManageCode(homepage.getClinicPathManageCode());
        dto.setDiagMatchOpDscg(homepage.getDiagMatchOpDscg());
        dto.setDiagMatchAdmDscg(homepage.getDiagMatchAdmDscg());
        dto.setDiagMatchPrePost(homepage.getDiagMatchPrePost());
        dto.setDiagMatchClinicPath(homepage.getDiagMatchClinicPath());
        dto.setDiagMatchRadioPath(homepage.getDiagMatchRadioPath());
        dto.setAutopsyCode(homepage.getAutopsyCode());
        dto.setAboCode(homepage.getAboCode());
        dto.setRhCode(homepage.getRhCode());
        dto.setTrainDrName(homepage.getTrainDrName());
        dto.setIntnDrName(homepage.getIntnDrName());
        dto.setCodrName(homepage.getCodrName());
        dto.setMedcasQltCode(homepage.getMedcasQltCode());
        dto.setQltctrlDate(homepage.getQltctrlDate());
        dto.setQltctrlDrName(homepage.getQltctrlDrName());
        dto.setQltctrlNursName(homepage.getQltctrlNursName());
        dto.setAcpMedinsName(homepage.getAcpMedinsName());
        dto.setAcpCommunityName(homepage.getAcpCommunityName());
        dto.setRinp31Flag(homepage.getRinp31Flag());
        dto.setRinp31Purpose(homepage.getRinp31Purpose());
        dto.setFieldSources(FieldSourcesJsonUtil.parse(objectMapper, homepage.getFieldSourcesJson()));
        // 费用由 AppService 从首页快照列叠加到 fees
        dto.setFees(new HashMap<>());
        // 中医信息只认正式列
        Map<String, String> sourceHints =
                encounterDiagnosisSourceHintResolver.resolveSourceHintByCode(encounterId);
        dto.setDiagnoses(toDiagnosisDtos(diagnoses, sourceHints));
        dto.setProcedures(toProcedureDtos(procedures));
        dto.setIcus(toIcuDtos(icus));
        dto.setBlds(toBldDtos(blds));
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
        command.setNtlyCode(request.getNtlyCode());
        command.setNatyCode(request.getNatyCode());
        command.setCertno(request.getCertno());
        command.setPrfsCode(request.getPrfsCode());
        command.setMrgStasCode(request.getMrgStasCode());
        command.setCurrAddr(request.getCurrAddr());
        command.setPsnTel(request.getPsnTel());
        command.setConerName(request.getConerName());
        command.setConerTel(request.getConerTel());
        command.setConerRltsCode(request.getConerRltsCode());
        command.setConerAddr(request.getConerAddr());
        command.setTrtTypeCode(request.getTrtTypeCode());
        command.setClinicalPathCode(request.getClinicalPathCode());
        command.setTcmPrepCode(request.getTcmPrepCode());
        command.setTcmDeviceCode(request.getTcmDeviceCode());
        command.setTcmTechCode(request.getTcmTechCode());
        command.setSyndromeNurseCode(request.getSyndromeNurseCode());
        command.setSpgaNurscareDays(request.getSpgaNurscareDays());
        command.setLv1NurscareDays(request.getLv1NurscareDays());
        command.setScdNurscareDays(request.getScdNurscareDays());
        command.setLv3NurscareDays(request.getLv3NurscareDays());
        command.setOtpWmDise(request.getOtpWmDise());
        command.setWmDiseCode(request.getWmDiseCode());
        command.setOtpTcmDise(request.getOtpTcmDise());
        command.setTcmDiseCode(request.getTcmDiseCode());
        command.setBrnDamgBfadmComaDura(request.getBrnDamgBfadmComaDura());
        command.setBrnDamgAfadmComaDura(request.getBrnDamgAfadmComaDura());
        command.setVentUsedDura(request.getVentUsedDura());
        command.setIptNo(request.getIptNo());
        command.setAdmWayCode(request.getAdmWayCode());
        command.setAdmCatyCode(request.getAdmCatyCode());
        command.setAdmCatyName(request.getAdmCatyName());
        command.setAdmTime(request.getAdmTime());
        command.setDscgCatyCode(request.getDscgCatyCode());
        command.setDscgCatyName(request.getDscgCatyName());
        command.setDscgTime(request.getDscgTime());
        command.setIptDays(request.getIptDays());
        command.setTransferDeptSummary(request.getTransferDeptSummary());
        command.setDscgWayCode(request.getDscgWayCode());
        command.setDeptDirectorName(request.getDeptDirectorName());
        command.setChfdrName(request.getChfdrName());
        command.setAtddrName(request.getAtddrName());
        command.setIptDrName(request.getIptDrName());
        command.setRespNursName(request.getRespNursName());
        command.setPayWayCode(request.getPayWayCode());
        command.setHealthCardNo(request.getHealthCardNo());
        command.setAgeYear(request.getAgeYear());
        command.setAgeMonth(request.getAgeMonth());
        command.setBirplcCodes(request.getBirplcCodes());
        command.setBirplcName(request.getBirplcName());
        command.setNaplCodes(request.getNaplCodes());
        command.setNaplName(request.getNaplName());
        command.setCurrAddrCodes(request.getCurrAddrCodes());
        command.setCurrAddrDetail(request.getCurrAddrDetail());
        command.setCurrAddrPoscode(request.getCurrAddrPoscode());
        command.setResdAddrCodes(request.getResdAddrCodes());
        command.setResdAddrDetail(request.getResdAddrDetail());
        command.setResdAddrPoscode(request.getResdAddrPoscode());
        command.setEmprAddr(request.getEmprAddr());
        command.setEmprTel(request.getEmprTel());
        command.setEmprPoscode(request.getEmprPoscode());
        command.setAdmWard(request.getAdmWard());
        command.setAdmBed(request.getAdmBed());
        command.setDscgWard(request.getDscgWard());
        command.setDscgBed(request.getDscgBed());
        command.setDrugAllergyCode(request.getDrugAllergyCode());
        command.setAllergyDrugName(request.getAllergyDrugName());
        command.setPathologyNo(request.getPathologyNo());
        command.setMainDiagCureCode(request.getMainDiagCureCode());
        command.setRescueCount(request.getRescueCount());
        command.setRescueSuccessCount(request.getRescueSuccessCount());
        command.setClinicPathManageCode(request.getClinicPathManageCode());
        command.setDiagMatchOpDscg(request.getDiagMatchOpDscg());
        command.setDiagMatchAdmDscg(request.getDiagMatchAdmDscg());
        command.setDiagMatchPrePost(request.getDiagMatchPrePost());
        command.setDiagMatchClinicPath(request.getDiagMatchClinicPath());
        command.setDiagMatchRadioPath(request.getDiagMatchRadioPath());
        command.setAutopsyCode(request.getAutopsyCode());
        command.setAboCode(request.getAboCode());
        command.setRhCode(request.getRhCode());
        command.setTrainDrName(request.getTrainDrName());
        command.setIntnDrName(request.getIntnDrName());
        command.setCodrName(request.getCodrName());
        command.setMedcasQltCode(request.getMedcasQltCode());
        command.setQltctrlDate(request.getQltctrlDate());
        command.setQltctrlDrName(request.getQltctrlDrName());
        command.setQltctrlNursName(request.getQltctrlNursName());
        command.setAcpMedinsName(request.getAcpMedinsName());
        command.setAcpCommunityName(request.getAcpCommunityName());
        command.setRinp31Flag(request.getRinp31Flag());
        command.setRinp31Purpose(request.getRinp31Purpose());
        command.setDiagnoses(toDiagnosisEntities(request.getDiagnoses()));
        command.setProcedures(toProcedureEntities(request.getProcedures()));
        command.setIcus(toIcuEntities(request.getIcus()));
        command.setBlds(toBldEntities(request.getBlds()));
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
        // 前端未传诊断子表时返回空列表，由领域按全量对齐处理
        if (lines == null) {
            return result;
        }
        // 逐行映射 DTO → 实体（保存时不带 id，由领域 upsert）
        for (DiagnosisLineDto line : lines) {
            HomepageDiagnosis entity = new HomepageDiagnosis();
            entity.setDiagTypeCode(line.getDiagTypeCode());
            entity.setSortNo(line.getSortNo());
            entity.setDiagCode(line.getDiagCode());
            entity.setDiagName(line.getDiagName());
            entity.setAdmCondCode(line.getAdmCondCode());
            entity.setOutIllStatusCode(line.getOutIllStatusCode());
            entity.setConfirmTime(line.getConfirmTime());
            entity.setDiagRemark(line.getDiagRemark());
            // 出院诊断：诊断医生主键+姓名、是否上送医保
            entity.setConfirmDrId(line.getConfirmDrId());
            entity.setConfirmDrName(line.getConfirmDrName());
            entity.setNeedYbUp(line.getNeedYbUp());
            entity.setMaindiagFlag(line.getMaindiagFlag());
            entity.setValiFlag(line.getValiFlag());
            result.add(entity);
        }
        return result;
    }

    /**
     * 手术 DTO 转领域实体（保存用；字段与 {@link ProcedureLineDto} / 表列同构）。
     *
     * @param lines DTO 列表
     * @return 实体列表
     */
    public List<HomepageProcedure> toProcedureEntities(List<ProcedureLineDto> lines) {
        List<HomepageProcedure> result = new ArrayList<>();
        // 前端未传手术子表时返回空列表
        if (lines == null) {
            return result;
        }
        for (ProcedureLineDto line : lines) {
            HomepageProcedure entity = new HomepageProcedure();
            entity.setSortNo(line.getSortNo());
            entity.setOprnOprtCode(line.getOprnOprtCode());
            entity.setOprnOprtName(line.getOprnOprtName());
            entity.setOprnDate(line.getOprnDate());
            entity.setOprnTime(line.getOprnTime());
            entity.setMainOprnFlag(line.getMainOprnFlag());
            entity.setOprnLevelCode(line.getOprnLevelCode());
            entity.setOprnIncisionCode(line.getOprnIncisionCode());
            entity.setHealGradeCode(line.getHealGradeCode());
            entity.setAnstWayCode(line.getAnstWayCode());
            entity.setOperDrName(line.getOperDrName());
            entity.setAsit1Name(line.getAsit1Name());
            entity.setAsit2Name(line.getAsit2Name());
            entity.setNurseName(line.getNurseName());
            entity.setAnstDrName(line.getAnstDrName());
            entity.setAnstStartTime(line.getAnstStartTime());
            entity.setAnstEndTime(line.getAnstEndTime());
            entity.setSpecimen(line.getSpecimen());
            entity.setNote(line.getNote());
            entity.setSendLabFlag(line.getSendLabFlag());
            entity.setValiFlag(line.getValiFlag());
            result.add(entity);
        }
        return result;
    }

    /**
     * 诊断实体行转 DTO（含主键，供编辑页回显）。
     *
     * @param rows        诊断实体列表，可为 null
     * @param sourceHints 就诊诊断来源提示（编码 → source_enum）；可空
     * @return DTO 列表，永不为 null
     */
    private List<DiagnosisLineDto> toDiagnosisDtos(
            List<HomepageDiagnosis> rows, Map<String, String> sourceHints) {
        List<DiagnosisLineDto> result = new ArrayList<>();
        // 无行时返回空列表，前端展示空表头
        if (rows == null) {
            return result;
        }
        Set<String> storedCodes = new LinkedHashSet<>();
        for (HomepageDiagnosis row : rows) {
            if (row == null) {
                continue;
            }
            String code = row.getDiagCode();
            if (code != null && !code.isBlank()) {
                storedCodes.add(code.trim());
            }
        }
        Map<String, ClinicalDiagnosisPresentationQueryModel> presentationByCode =
                conditionDefinitionQueryService.mapClinicalPresentationByStoredCodes(storedCodes, sourceHints);
        Map<String, String> catalogSourceByCode =
                conditionDefinitionQueryService.mapCatalogSourceByConditionCodes(storedCodes);
        // 按库表顺序回显诊断行（含 id 供前端编辑；扩列含病情/确诊/医生/上传）
        for (HomepageDiagnosis row : rows) {
            DiagnosisLineDto dto = new DiagnosisLineDto();
            dto.setId(row.getId());
            dto.setDiagTypeCode(row.getDiagTypeCode());
            dto.setSortNo(row.getSortNo());
            String storedCode = row.getDiagCode();
            String storedName = row.getDiagName();
            ClinicalDiagnosisPresentationQueryModel presentation = null;
            if (storedCode != null && !storedCode.isBlank()) {
                presentation = presentationByCode.get(storedCode.trim());
            }
            // 首页仅「诊断名称/编码」一列口径：有国临绑码优先回显国临；否则回显落库码/名（中医、未对照西医医保均不置空）
            if (presentation != null) {
                String clinicCode = presentation.getClinicCode();
                String clinicName = presentation.getClinicName();
                if (clinicCode != null && !clinicCode.isBlank()) {
                    dto.setDiagCode(clinicCode);
                    if (clinicName != null && !clinicName.isBlank()) {
                        dto.setDiagName(clinicName);
                    } else {
                        dto.setDiagName(storedName);
                    }
                } else if (DoctorStationDiagnosisConstants.DIAGNOSIS_KIND_TAG_TCM.equals(
                        presentation.getDiagnosisKind())
                        && presentation.getYbCode() != null
                        && !presentation.getYbCode().isBlank()) {
                    // 中医：目录码/名正常落主列
                    dto.setDiagCode(presentation.getYbCode());
                    if (presentation.getYbName() != null && !presentation.getYbName().isBlank()) {
                        dto.setDiagName(presentation.getYbName());
                    } else {
                        dto.setDiagName(storedName);
                    }
                } else {
                    // 西医医保无国临绑码：首页无医保列，仍展示落库码便于改选，禁止置空
                    dto.setDiagCode(storedCode);
                    dto.setDiagName(storedName);
                }
            } else {
                dto.setDiagCode(storedCode);
                dto.setDiagName(storedName);
            }
            dto.setSourceEnum(this.resolveDiagSource(storedCode, sourceHints, catalogSourceByCode));
            dto.setAdmCondCode(row.getAdmCondCode());
            dto.setOutIllStatusCode(row.getOutIllStatusCode());
            dto.setConfirmTime(row.getConfirmTime());
            dto.setDiagRemark(row.getDiagRemark());
            dto.setConfirmDrId(row.getConfirmDrId());
            dto.setConfirmDrName(row.getConfirmDrName());
            dto.setNeedYbUp(row.getNeedYbUp());
            dto.setMaindiagFlag(row.getMaindiagFlag());
            dto.setValiFlag(row.getValiFlag());
            result.add(dto);
        }
        return result;
    }

    /**
     * 诊断行目录来源：优先就诊实际开立的目录，否则按编码反查目录。
     *
     * @param storedCode          落库诊断编码
     * @param sourceHints         就诊诊断编码 → source_enum
     * @param catalogSourceByCode 目录反查编码 → source_enum
     * @return tcm_dx / tcm_syndrome / clinical_v2 等；无法解析时 null
     */
    private String resolveDiagSource(
            String storedCode,
            Map<String, String> sourceHints,
            Map<String, String> catalogSourceByCode) {
        if (storedCode == null || storedCode.isBlank()) {
            return null;
        }
        String code = storedCode.trim();
        String hinted = null;
        if (sourceHints != null) {
            hinted = sourceHints.get(code);
        }
        if (hinted != null && !hinted.isBlank()) {
            return hinted.trim();
        }
        if (catalogSourceByCode == null) {
            return null;
        }
        String lookedUp = catalogSourceByCode.get(code);
        if (lookedUp == null || lookedUp.isBlank()) {
            return null;
        }
        return lookedUp.trim();
    }

    /**
     * 手术实体行转 DTO（含主键与扩列，供编辑页回显；对齐 AS-MRMS-HP-001）。
     *
     * @param rows 手术实体列表，可为 null
     * @return DTO 列表，永不为 null
     */
    private List<ProcedureLineDto> toProcedureDtos(List<HomepageProcedure> rows) {
        List<ProcedureLineDto> result = new ArrayList<>();
        // 无手术行时返回空列表，前端展示空表头
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
            dto.setOprnTime(row.getOprnTime());
            dto.setMainOprnFlag(row.getMainOprnFlag());
            dto.setOprnLevelCode(row.getOprnLevelCode());
            dto.setOprnIncisionCode(row.getOprnIncisionCode());
            dto.setHealGradeCode(row.getHealGradeCode());
            dto.setAnstWayCode(row.getAnstWayCode());
            dto.setOperDrName(row.getOperDrName());
            dto.setAsit1Name(row.getAsit1Name());
            dto.setAsit2Name(row.getAsit2Name());
            dto.setNurseName(row.getNurseName());
            dto.setAnstDrName(row.getAnstDrName());
            dto.setAnstStartTime(row.getAnstStartTime());
            dto.setAnstEndTime(row.getAnstEndTime());
            dto.setSpecimen(row.getSpecimen());
            dto.setNote(row.getNote());
            dto.setSendLabFlag(row.getSendLabFlag());
            dto.setValiFlag(row.getValiFlag());
            result.add(dto);
        }
        return result;
    }

    /**
     * ICU 实体行 → 首页出站 DTO（保留 id，供前端编辑回显）。
     *
     * @param rows ICU 实体列表，可为 null（视为空）
     * @return DTO 列表；永不返回 null
     */
    private List<IcuLineDto> toIcuDtos(List<HomepageIcu> rows) {
        List<IcuLineDto> result = new ArrayList<>();
        // 无 ICU 子表时出站空数组，避免前端判 null
        if (rows == null) {
            return result;
        }
        // 逐行投影：病房类型/进出时间/合计时长
        for (HomepageIcu row : rows) {
            IcuLineDto dto = new IcuLineDto();
            dto.setId(row.getId());
            dto.setSortNo(row.getSortNo());
            dto.setScsCutdWardType(row.getScsCutdWardType());
            dto.setScsCutdInpoolTime(row.getScsCutdInpoolTime());
            dto.setScsCutdExitTime(row.getScsCutdExitTime());
            dto.setScsCutdSumDura(row.getScsCutdSumDura());
            dto.setValiFlag(row.getValiFlag());
            result.add(dto);
        }
        return result;
    }

    /**
     * 输血实体行 → 首页出站 DTO（保留 id，供前端编辑回显）。
     *
     * @param rows 输血实体列表，可为 null（视为空）
     * @return DTO 列表；永不返回 null
     */
    private List<BldLineDto> toBldDtos(List<HomepageBld> rows) {
        List<BldLineDto> result = new ArrayList<>();
        // 无输血子表时出站空数组
        if (rows == null) {
            return result;
        }
        // 逐行投影：品种/量/单位
        for (HomepageBld row : rows) {
            BldLineDto dto = new BldLineDto();
            dto.setId(row.getId());
            dto.setSortNo(row.getSortNo());
            dto.setBldCat(row.getBldCat());
            dto.setBldAmt(row.getBldAmt());
            dto.setBldUnt(row.getBldUnt());
            dto.setValiFlag(row.getValiFlag());
            result.add(dto);
        }
        return result;
    }

    /**
     * ICU 入站 DTO → 领域实体（保存全量替换用；不带 id，由领域层重建）。
     *
     * @param lines DTO 列表，可为 null（视为空）
     * @return 实体列表；永不返回 null
     */
    public List<HomepageIcu> toIcuEntities(List<IcuLineDto> lines) {
        List<HomepageIcu> result = new ArrayList<>();
        // null 与空列表均表示「替换为空」
        if (lines == null) {
            return result;
        }
        // 仅映射业务字段；caseRecordId/tenantId 由 DomainService 补齐
        for (IcuLineDto line : lines) {
            HomepageIcu entity = new HomepageIcu();
            entity.setSortNo(line.getSortNo());
            entity.setScsCutdWardType(line.getScsCutdWardType());
            entity.setScsCutdInpoolTime(line.getScsCutdInpoolTime());
            entity.setScsCutdExitTime(line.getScsCutdExitTime());
            entity.setScsCutdSumDura(line.getScsCutdSumDura());
            entity.setValiFlag(line.getValiFlag());
            result.add(entity);
        }
        return result;
    }

    /**
     * 输血入站 DTO → 领域实体（保存全量替换用；不带 id，由领域层重建）。
     *
     * @param lines DTO 列表，可为 null（视为空）
     * @return 实体列表；永不返回 null
     */
    public List<HomepageBld> toBldEntities(List<BldLineDto> lines) {
        List<HomepageBld> result = new ArrayList<>();
        // null 与空列表均表示「替换为空」
        if (lines == null) {
            return result;
        }
        // 仅映射品种/量/单位；外键由 DomainService 补齐
        for (BldLineDto line : lines) {
            HomepageBld entity = new HomepageBld();
            entity.setSortNo(line.getSortNo());
            entity.setBldCat(line.getBldCat());
            entity.setBldAmt(line.getBldAmt());
            entity.setBldUnt(line.getBldUnt());
            entity.setValiFlag(line.getValiFlag());
            result.add(entity);
        }
        return result;
    }
}
