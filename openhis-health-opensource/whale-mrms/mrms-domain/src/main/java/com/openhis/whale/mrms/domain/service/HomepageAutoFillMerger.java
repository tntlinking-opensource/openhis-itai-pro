package com.openhis.whale.mrms.domain.service;

import com.openhis.whale.mrms.domain.entity.CaseRecord;
import com.openhis.whale.mrms.domain.entity.CaseRecordHomepage;
import com.openhis.whale.mrms.domain.enums.HomepageDiagType;
import com.openhis.whale.mrms.domain.model.HomepageSaveCommand.DiagnosisLineCommand;
import com.openhis.whale.mrms.domain.model.HomepageSaveCommand.ProcedureLineCommand;
import com.openhis.whale.mrms.port.DiagnosisProcedurePort;
import com.openhis.whale.mrms.port.EncounterHistoryPort;
import com.openhis.whale.mrms.port.PatientEncounterPort;
import com.openhis.whale.mrms.port.model.DiagnosisLineSnapshot;
import com.openhis.whale.mrms.port.model.EncounterSnapshot;
import com.openhis.whale.mrms.port.model.PatientSnapshot;
import com.openhis.whale.mrms.port.model.ProcedureLineSnapshot;
import com.openhis.whale.mrms.port.model.TransferLineSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 从 Port 拉取 HIS 数据合并到病案首页，维护 field_sources_json。
 */
@Service
@RequiredArgsConstructor
public class HomepageAutoFillMerger {

    private final PatientEncounterPort patientEncounterPort;
    private final DiagnosisProcedurePort diagnosisProcedurePort;
    private final EncounterHistoryPort encounterHistoryPort;
    private final CaseRecordHomepageManager homepageManager;
    private final FieldSourcesJsonHelper fieldSourcesJsonHelper;

    @Transactional(rollbackFor = Exception.class)
    public CaseRecordHomepage merge(CaseRecord caseRecord, List<String> sections, boolean forceOverwrite) {
        CaseRecordHomepage homepage = homepageManager.requireByCaseRecordId(caseRecord.getId());
        Map<String, String> sources = fieldSourcesJsonHelper.parse(homepage.getFieldSourcesJson());
        Set<String> sectionSet = normalizeSections(sections);
        PatientSnapshot patient = patientEncounterPort.findPatient(caseRecord.getPatientId()).orElse(null);
        EncounterSnapshot encounter = patientEncounterPort.findEncounter(caseRecord.getEncounterId()).orElse(null);

        if (sectionSet.contains("basic") && patient != null) {
            mergeBasic(homepage, sources, patient, forceOverwrite);
        }
        if (sectionSet.contains("inpatient") && encounter != null) {
            mergeInpatient(homepage, sources, encounter, forceOverwrite);
        }
        if (sectionSet.contains("staff") && encounter != null) {
            mergeStaff(homepage, sources, encounter, forceOverwrite);
        }
        if (sectionSet.contains("diagnosis") && encounter != null) {
            mergeDiagnoses(caseRecord, encounter.getEncounterId(), forceOverwrite);
        }
        if (sectionSet.contains("procedure") && encounter != null) {
            mergeProcedures(caseRecord, encounter.getEncounterId(), forceOverwrite);
        }
        if (sectionSet.contains("inpatient")) {
            mergeTransferSummary(homepage, sources, caseRecord.getEncounterId(), forceOverwrite);
        }

        homepage.setFieldSourcesJson(fieldSourcesJsonHelper.serialize(sources));
        return homepageManager.saveScalarsOnly(homepage);
    }

    private Set<String> normalizeSections(List<String> sections) {
        if (sections == null || sections.isEmpty()) {
            return Set.of("all");
        }
        Set<String> normalized = new HashSet<>();
        for (String section : sections) {
            if ("all".equalsIgnoreCase(section)) {
                return Set.of("basic", "inpatient", "diagnosis", "procedure", "staff");
            }
            normalized.add(section.toLowerCase());
        }
        return normalized;
    }

    private void mergeBasic(CaseRecordHomepage homepage, Map<String, String> sources, PatientSnapshot patient,
            boolean force) {
        setAutoField(homepage, sources, "psn_name", patient.getName(), force, homepage::setPsnName);
        setAutoField(homepage, sources, "gend_code", patient.getGenderCode(), force, homepage::setGendCode);
        if (patient.getBirthDate() != null && shouldOverwrite(sources, "brdy", force)) {
            homepage.setBrdy(patient.getBirthDate());
            fieldSourcesJsonHelper.markAuto(sources, "brdy");
        }
        setAutoField(homepage, sources, "certno", patient.getIdCard(), force, homepage::setCertno);
        setAutoField(homepage, sources, "ntly_code", patient.getNationalityCode(), force, homepage::setNtlyCode);
        setAutoField(homepage, sources, "prfs_code", patient.getOccupationCode(), force, homepage::setPrfsCode);
        setAutoField(homepage, sources, "mrg_stas_code", patient.getMaritalStatusCode(), force, homepage::setMrgStasCode);
        setAutoField(homepage, sources, "curr_addr", patient.getAddress(), force, homepage::setCurrAddr);
        setAutoField(homepage, sources, "psn_tel", patient.getPhone(), force, homepage::setPsnTel);
        setAutoField(homepage, sources, "coner_name", patient.getContactName(), force, homepage::setConerName);
        setAutoField(homepage, sources, "coner_tel", patient.getContactPhone(), force, homepage::setConerTel);
        setAutoField(homepage, sources, "coner_addr", patient.getContactAddress(), force, homepage::setConerAddr);
    }

    private void mergeInpatient(CaseRecordHomepage homepage, Map<String, String> sources, EncounterSnapshot encounter,
            boolean force) {
        setAutoField(homepage, sources, "ipt_no", encounter.getInpatientNo(), force, homepage::setIptNo);
        setAutoField(homepage, sources, "adm_way_code", encounter.getAdmitWayCode(), force, homepage::setAdmWayCode);
        setAutoField(homepage, sources, "adm_caty_code", encounter.getAdmitDeptCode(), force, homepage::setAdmCatyCode);
        setAutoField(homepage, sources, "adm_caty_name", encounter.getAdmitDeptName(), force, homepage::setAdmCatyName);
        setAutoField(homepage, sources, "dscg_caty_code", encounter.getDischargeDeptCode(), force, homepage::setDscgCatyCode);
        setAutoField(homepage, sources, "dscg_caty_name", encounter.getDischargeDeptName(), force, homepage::setDscgCatyName);
        if (encounter.getAdmissionAt() != null && shouldOverwrite(sources, "adm_time", force)) {
            homepage.setAdmTime(encounter.getAdmissionAt());
            fieldSourcesJsonHelper.markAuto(sources, "adm_time");
        }
        if (encounter.getDischargeAt() != null && shouldOverwrite(sources, "dscg_time", force)) {
            homepage.setDscgTime(encounter.getDischargeAt());
            fieldSourcesJsonHelper.markAuto(sources, "dscg_time");
        }
        String dischargeWay = encounterHistoryPort.resolveDischargeWayCode(encounter.getEncounterId())
                .orElse(encounter.getDischargeWayCode());
        setAutoField(homepage, sources, "dscg_way_code", dischargeWay, force, homepage::setDscgWayCode);
        if (homepage.getAdmTime() != null && homepage.getDscgTime() != null && shouldOverwrite(sources, "act_ipt_days", force)) {
            long days = ChronoUnit.DAYS.between(homepage.getAdmTime().toLocalDate(), homepage.getDscgTime().toLocalDate());
            homepage.setActIptDays((int) Math.max(days, 1));
            fieldSourcesJsonHelper.markAuto(sources, "act_ipt_days");
        }
    }

    private void mergeStaff(CaseRecordHomepage homepage, Map<String, String> sources, EncounterSnapshot encounter,
            boolean force) {
        setAutoField(homepage, sources, "dept_director_name", encounter.getDeptDirectorName(), force,
                homepage::setDeptDirectorName);
        setAutoField(homepage, sources, "chfdr_name", encounter.getChiefDoctorName(), force, homepage::setChfdrName);
        setAutoField(homepage, sources, "atddr_name", encounter.getAttendingPractitionerName(), force, homepage::setAtddrName);
        setAutoField(homepage, sources, "ipt_dr_name", encounter.getResidentDoctorName(), force, homepage::setIptDrName);
        setAutoField(homepage, sources, "resp_nurs_name", encounter.getPrimaryNurseName(), force, homepage::setRespNursName);
    }

    private void mergeTransferSummary(CaseRecordHomepage homepage, Map<String, String> sources, long encounterId,
            boolean force) {
        List<TransferLineSnapshot> transfers = encounterHistoryPort.listTransfers(encounterId);
        if (transfers.isEmpty()) {
            return;
        }
        String summary = transfers.stream()
                .map(t -> String.format("%s→%s", nullToEmpty(t.getFromDeptName()), nullToEmpty(t.getToDeptName())))
                .collect(Collectors.joining("；"));
        setAutoField(homepage, sources, "transfer_dept_summary", summary, force, homepage::setTransferDeptSummary);
    }

    private void mergeDiagnoses(CaseRecord caseRecord, long encounterId, boolean forceOverwrite) {
        List<DiagnosisLineSnapshot> discharge = diagnosisProcedurePort.listDischargeDiagnoses(encounterId);
        if (discharge.isEmpty()) {
            return;
        }
        List<DiagnosisLineCommand> mainLines = new ArrayList<>();
        List<DiagnosisLineCommand> otherLines = new ArrayList<>();
        int otherSort = 0;
        for (DiagnosisLineSnapshot line : discharge) {
            DiagnosisLineCommand cmd = toDiagnosisCommand(line);
            if (line.isPrimary()) {
                cmd.setDiagTypeCode(HomepageDiagType.DISCHARGE_MAIN.getCode());
                cmd.setSortNo(0);
                mainLines.add(cmd);
            } else {
                cmd.setDiagTypeCode(HomepageDiagType.DISCHARGE_OTHER.getCode());
                cmd.setSortNo(otherSort++);
                otherLines.add(cmd);
            }
        }
        if (!mainLines.isEmpty() && forceOverwrite) {
            homepageManager.replaceDiagnosesByType(caseRecord.getId(), caseRecord.getTenantId(),
                    HomepageDiagType.DISCHARGE_MAIN.getCode(), mainLines);
        }
        if (!otherLines.isEmpty() && forceOverwrite) {
            homepageManager.replaceDiagnosesByType(caseRecord.getId(), caseRecord.getTenantId(),
                    HomepageDiagType.DISCHARGE_OTHER.getCode(), otherLines);
        }
    }

    private void mergeProcedures(CaseRecord caseRecord, long encounterId, boolean forceOverwrite) {
        if (!forceOverwrite) {
            return;
        }
        List<ProcedureLineSnapshot> procedures = diagnosisProcedurePort.listProcedures(encounterId);
        if (procedures.isEmpty()) {
            return;
        }
        List<ProcedureLineCommand> lines = new ArrayList<>();
        for (ProcedureLineSnapshot snapshot : procedures) {
            ProcedureLineCommand cmd = new ProcedureLineCommand();
            cmd.setSortNo(snapshot.getSortNo());
            cmd.setOprnOprtCode(snapshot.getProcedureCode());
            cmd.setOprnOprtName(snapshot.getProcedureName());
            cmd.setOprnDate(snapshot.getProcedureDate());
            cmd.setOprnLevelCode(snapshot.getLevelCode());
            cmd.setOprnIncisionCode(snapshot.getIncisionCode());
            cmd.setHealGradeCode(snapshot.getHealGradeCode());
            cmd.setAnstWayCode(snapshot.getAnesthesiaCode());
            lines.add(cmd);
        }
        homepageManager.replaceProcedures(caseRecord.getId(), caseRecord.getTenantId(), lines);
    }

    private DiagnosisLineCommand toDiagnosisCommand(DiagnosisLineSnapshot line) {
        DiagnosisLineCommand cmd = new DiagnosisLineCommand();
        cmd.setSortNo(line.getSortNo());
        cmd.setDiagCode(line.getDiagCode());
        cmd.setDiagName(line.getDiagName());
        cmd.setAdmCondCode(line.getAdmCondCode());
        return cmd;
    }

    private void setAutoField(CaseRecordHomepage homepage, Map<String, String> sources, String fieldKey, String value,
            boolean force, java.util.function.Consumer<String> setter) {
        if (!StringUtils.hasText(value) || !shouldOverwrite(sources, fieldKey, force)) {
            return;
        }
        setter.accept(value);
        fieldSourcesJsonHelper.markAuto(sources, fieldKey);
    }

    private boolean shouldOverwrite(Map<String, String> sources, String fieldKey, boolean force) {
        if (force) {
            return true;
        }
        return !fieldSourcesJsonHelper.isManual(sources, fieldKey);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
