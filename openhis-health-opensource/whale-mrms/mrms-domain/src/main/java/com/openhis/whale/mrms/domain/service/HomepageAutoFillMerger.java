/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.domain.service;

import com.openhis.whale.mrms.domain.entity.CaseRecord;
import com.openhis.whale.mrms.domain.entity.CaseRecordHomepage;
import com.openhis.whale.mrms.domain.entity.HomepageDiagnosis;
import com.openhis.whale.mrms.domain.entity.HomepageProcedure;
import com.openhis.whale.mrms.domain.enums.FieldFillSource;
import com.openhis.whale.mrms.domain.enums.HomepageDiagType;
import com.openhis.whale.mrms.domain.repository.CaseRecordHomepageRepository;
import com.openhis.whale.mrms.domain.support.HomepageChildTableSupport;
import com.openhis.whale.mrms.contracts.reader.HealthDiagnosisProcedureReader;
import com.openhis.whale.mrms.contracts.reader.HealthEncounterHistoryReader;
import com.openhis.whale.mrms.contracts.reader.HealthPatientEncounterReader;
import com.openhis.whale.mrms.contracts.snapshot.DiagnosisLineSnapshot;
import com.openhis.whale.mrms.contracts.snapshot.EncounterSnapshot;
import com.openhis.whale.mrms.contracts.snapshot.PatientSnapshot;
import com.openhis.whale.mrms.contracts.snapshot.ProcedureLineSnapshot;
import com.openhis.whale.mrms.contracts.snapshot.TransferLineSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 首页自动取值合并器：尊重 field_sources_json 中 manual 标记。
 */
@Service
@RequiredArgsConstructor
public class HomepageAutoFillMerger {

    private final HealthPatientEncounterReader healthPatientEncounterReader;
    private final HealthDiagnosisProcedureReader healthDiagnosisProcedureReader;
    private final HealthEncounterHistoryReader healthEncounterHistoryReader;
    private final CaseRecordHomepageRepository homepageRepository;
    private final HomepageChildTableSupport childTableSupport;
    private final FieldSourcesJsonHelper fieldSourcesJsonHelper;

    /**
     * 按段刷新自动取值。
     *
     * @param record 病案主档
     * @param sections 刷新段列表
     * @param forceOverwrite 是否强制覆盖 auto/mixed
     * @param operatorId 操作人
     * @return 刷新后首页
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseRecordHomepage merge(
            CaseRecord record,
            List<String> sections,
            boolean forceOverwrite,
            Long operatorId) {
        CaseRecordHomepage homepage = homepageRepository
                .findByCaseRecordIdAndIsDeletedFalse(record.getId())
                .orElseThrow();
        Map<String, String> sources = fieldSourcesJsonHelper.parse(homepage.getFieldSourcesJson());
        boolean all = sections != null && sections.contains("all");
        if (all || containsSection(sections, "basic")) {
            mergeBasic(record, homepage, sources, forceOverwrite);
        }
        if (all || containsSection(sections, "inpatient")) {
            mergeInpatient(record, homepage, sources, forceOverwrite);
        }
        if (all || containsSection(sections, "staff")) {
            mergeStaff(record, homepage, sources, forceOverwrite);
        }
        if (all || containsSection(sections, "diagnosis")) {
            if (mergeDiagnoses(record, homepage, forceOverwrite)) {
                sources.put("diagnoses", FieldFillSource.AUTO.getCode());
            }
        }
        if (all || containsSection(sections, "procedure")) {
            if (mergeProcedures(record, homepage, forceOverwrite)) {
                sources.put("procedures", FieldFillSource.AUTO.getCode());
            }
        }
        homepage.setFieldSourcesJson(fieldSourcesJsonHelper.toJson(sources));
        homepage.setUpdatedAt(LocalDateTime.now());
        homepage.setUpdatedBy(operatorId);
        return homepageRepository.save(homepage);
    }

    private void mergeBasic(CaseRecord record, CaseRecordHomepage homepage, Map<String, String> sources, boolean force) {
        PatientSnapshot patient = healthPatientEncounterReader.loadPatient(record.getPatientId());
        if (patient == null) {
            return;
        }
        putIfAllowed(homepage, sources, "psn_name", patient.getName(), force);
        putIfAllowed(homepage, sources, "gend_code", patient.getGenderCode(), force);
        putIfAllowed(homepage, sources, "brdy", patient.getBirthDate(), force);
        putIfAllowed(homepage, sources, "certno", patient.getCertno(), force);
        putIfAllowed(homepage, sources, "curr_addr", patient.getCurrAddr(), force);
        putIfAllowed(homepage, sources, "psn_tel", patient.getPhone(), force);
        putIfAllowed(homepage, sources, "coner_name", patient.getContactName(), force);
        putIfAllowed(homepage, sources, "coner_tel", patient.getContactPhone(), force);
        putIfAllowed(homepage, sources, "ntly_code", patient.getNationalityCode(), force);
        putIfAllowed(homepage, sources, "naty_code", patient.getNatyCode(), force);
    }

    private void mergeInpatient(CaseRecord record, CaseRecordHomepage homepage, Map<String, String> sources, boolean force) {
        EncounterSnapshot enc = healthPatientEncounterReader.loadEncounter(record.getEncounterId());
        if (enc == null) {
            return;
        }
        putIfAllowed(homepage, sources, "ipt_no", enc.getIptNo(), force);
        putIfAllowed(homepage, sources, "adm_time", enc.getAdmTime(), force);
        putIfAllowed(homepage, sources, "dscg_time", enc.getDscgTime(), force);
        putIfAllowed(homepage, sources, "adm_caty_code", enc.getAdmCatyCode(), force);
        putIfAllowed(homepage, sources, "adm_caty_name", enc.getAdmCatyName(), force);
        putIfAllowed(homepage, sources, "dscg_caty_code", enc.getDscgCatyCode(), force);
        putIfAllowed(homepage, sources, "dscg_caty_name", enc.getDscgCatyName(), force);
        if (enc.getAdmTime() != null && enc.getDscgTime() != null) {
            long days = ChronoUnit.DAYS.between(enc.getAdmTime().toLocalDate(), enc.getDscgTime().toLocalDate());
            if (days < 1) {
                days = 1;
            }
            putIfAllowed(homepage, sources, "ipt_days", (int) days, force);
        }
        String dscgWay = healthEncounterHistoryReader.resolveDischargeWayCode(record.getEncounterId());
        putIfAllowed(homepage, sources, "dscg_way_code", dscgWay, force);
        List<TransferLineSnapshot> transfers = healthEncounterHistoryReader.listTransfers(record.getEncounterId());
        if (transfers != null && !transfers.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (TransferLineSnapshot line : transfers) {
                if (sb.length() > 0) {
                    sb.append("→");
                }
                sb.append(line.getToDeptName());
            }
            putIfAllowed(homepage, sources, "transfer_dept_summary", sb.toString(), force);
        }
    }

    private void mergeStaff(CaseRecord record, CaseRecordHomepage homepage, Map<String, String> sources, boolean force) {
        EncounterSnapshot enc = healthPatientEncounterReader.loadEncounter(record.getEncounterId());
        if (enc == null) {
            return;
        }
        putIfAllowed(homepage, sources, "dept_director_name", enc.getDeptDirectorName(), force);
        putIfAllowed(homepage, sources, "chfdr_name", enc.getChfdrName(), force);
        putIfAllowed(homepage, sources, "atddr_name", enc.getAtddrName(), force);
        putIfAllowed(homepage, sources, "atddr_practitioner_id", enc.getAtddrPractitionerId(), force);
        putIfAllowed(homepage, sources, "ipt_dr_name", enc.getIptDrName(), force);
        putIfAllowed(homepage, sources, "resp_nurs_name", enc.getRespNursName(), force);
    }

    private boolean mergeDiagnoses(CaseRecord record, CaseRecordHomepage homepage, boolean force) {
        if (!force && hasManualDiagnosis(homepage)) {
            return false;
        }
        List<HomepageDiagnosis> lines = new ArrayList<>();
        appendDiagLines(lines, HomepageDiagType.DISCHARGE_MAIN, healthDiagnosisProcedureReader.listDischargeDiagnoses(record.getEncounterId()), true);
        appendDiagLines(lines, HomepageDiagType.DISCHARGE_OTHER, healthDiagnosisProcedureReader.listDischargeDiagnoses(record.getEncounterId()), false);
        appendDiagLines(lines, HomepageDiagType.ADMISSION_MAIN, healthDiagnosisProcedureReader.listAdmissionDiagnoses(record.getEncounterId()), true);
        appendDiagLines(lines, HomepageDiagType.ADMISSION_OTHER, healthDiagnosisProcedureReader.listAdmissionDiagnoses(record.getEncounterId()), false);
        childTableSupport.replaceDiagnosisSnapshots(record.getId(), homepage.getTenantId(), lines);
        return true;
    }

    private boolean mergeProcedures(CaseRecord record, CaseRecordHomepage homepage, boolean force) {
        if (!force && hasManualProcedure(homepage)) {
            return false;
        }
        List<ProcedureLineSnapshot> procLines = healthDiagnosisProcedureReader.listProcedures(record.getEncounterId());
        List<HomepageProcedure> lines = new ArrayList<>();
        int sort = 0;
        for (ProcedureLineSnapshot snap : procLines) {
            HomepageProcedure row = new HomepageProcedure();
            row.setSortNo(sort++);
            row.setOprnOprtCode(snap.getOprnCode());
            row.setOprnOprtName(snap.getOprnName());
            row.setOprnDate(snap.getOprnDate());
            if (Boolean.TRUE.equals(snap.getMain())) {
                row.setMainOprnFlag("1");
            } else {
                row.setMainOprnFlag("0");
            }
            lines.add(row);
        }
        childTableSupport.replaceProcedureSnapshots(record.getId(), homepage.getTenantId(), lines);
        return true;
    }

    private void appendDiagLines(
            List<HomepageDiagnosis> target,
            HomepageDiagType type,
            List<DiagnosisLineSnapshot> snaps,
            boolean mainOnly) {
        if (snaps == null) {
            return;
        }
        int sort = 0;
        for (DiagnosisLineSnapshot snap : snaps) {
            if (mainOnly && !Boolean.TRUE.equals(snap.getMain())) {
                continue;
            }
            if (!mainOnly && Boolean.TRUE.equals(snap.getMain())) {
                continue;
            }
            HomepageDiagnosis row = new HomepageDiagnosis();
            row.setDiagTypeCode(type.getCode());
            row.setSortNo(sort++);
            row.setDiagCode(snap.getDiagCode());
            row.setDiagName(snap.getDiagName());
            row.setAdmCondCode(snap.getAdmCondCode());
            if (Boolean.TRUE.equals(snap.getMain())) {
                row.setMaindiagFlag("1");
            } else {
                row.setMaindiagFlag("0");
            }
            target.add(row);
        }
    }

    private boolean hasManualDiagnosis(CaseRecordHomepage homepage) {
        return isManualSection(homepage, "diagnoses");
    }

    private boolean hasManualProcedure(CaseRecordHomepage homepage) {
        return isManualSection(homepage, "procedures");
    }

    private boolean isManualSection(CaseRecordHomepage homepage, String sectionKey) {
        if (homepage == null) {
            return false;
        }
        Map<String, String> sources = fieldSourcesJsonHelper.parse(homepage.getFieldSourcesJson());
        return FieldFillSource.MANUAL.getCode().equals(sources.get(sectionKey));
    }

    private void putIfAllowed(
            CaseRecordHomepage homepage,
            Map<String, String> sources,
            String fieldKey,
            Object newVal,
            boolean force) {
        if (newVal == null) {
            return;
        }
        String source = sources.get(fieldKey);
        if (!force && FieldFillSource.MANUAL.getCode().equals(source)) {
            return;
        }
        applyField(homepage, fieldKey, newVal);
        sources.put(fieldKey, FieldFillSource.AUTO.getCode());
    }

    private void applyField(CaseRecordHomepage homepage, String fieldKey, Object value) {
        switch (fieldKey) {
            case "psn_name" -> homepage.setPsnName(String.valueOf(value));
            case "gend_code" -> homepage.setGendCode(String.valueOf(value));
            case "brdy" -> homepage.setBrdy((java.time.LocalDate) value);
            case "certno" -> homepage.setCertno(String.valueOf(value));
            case "curr_addr" -> homepage.setCurrAddr(String.valueOf(value));
            case "psn_tel" -> homepage.setPsnTel(String.valueOf(value));
            case "coner_name" -> homepage.setConerName(String.valueOf(value));
            case "coner_tel" -> homepage.setConerTel(String.valueOf(value));
            case "ntly_code" -> homepage.setNtlyCode(String.valueOf(value));
            case "naty_code" -> homepage.setNatyCode(String.valueOf(value));
            case "ipt_no" -> homepage.setIptNo(String.valueOf(value));
            case "adm_time" -> homepage.setAdmTime((LocalDateTime) value);
            case "dscg_time" -> homepage.setDscgTime((LocalDateTime) value);
            case "adm_caty_code" -> homepage.setAdmCatyCode(String.valueOf(value));
            case "adm_caty_name" -> homepage.setAdmCatyName(String.valueOf(value));
            case "dscg_caty_code" -> homepage.setDscgCatyCode(String.valueOf(value));
            case "dscg_caty_name" -> homepage.setDscgCatyName(String.valueOf(value));
            case "ipt_days" -> homepage.setIptDays((Integer) value);
            case "dscg_way_code" -> homepage.setDscgWayCode(String.valueOf(value));
            case "transfer_dept_summary" -> homepage.setTransferDeptSummary(String.valueOf(value));
            case "dept_director_name" -> homepage.setDeptDirectorName(String.valueOf(value));
            case "chfdr_name" -> homepage.setChfdrName(String.valueOf(value));
            case "atddr_name" -> homepage.setAtddrName(String.valueOf(value));
            case "atddr_practitioner_id" -> homepage.setAtddrPractitionerId((Long) value);
            case "ipt_dr_name" -> homepage.setIptDrName(String.valueOf(value));
            case "resp_nurs_name" -> homepage.setRespNursName(String.valueOf(value));
            default -> {
                // 未注册字段忽略
            }
        }
    }

    private boolean containsSection(List<String> sections, String code) {
        if (sections == null) {
            return false;
        }
        for (String section : sections) {
            if (code.equals(trimToNull(section))) {
                return true;
            }
        }
        return false;
    }
}
