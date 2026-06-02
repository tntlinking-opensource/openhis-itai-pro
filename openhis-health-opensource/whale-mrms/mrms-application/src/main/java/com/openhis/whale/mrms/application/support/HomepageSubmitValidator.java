package com.openhis.whale.mrms.application.support;

import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.mrms.domain.entity.CaseRecord;
import com.openhis.whale.mrms.domain.entity.CaseRecordHomepage;
import com.openhis.whale.mrms.domain.entity.HomepageDiagnosis;
import com.openhis.whale.mrms.domain.enums.CaseRecordStatus;
import com.openhis.whale.mrms.domain.enums.HomepageDiagType;
import com.openhis.whale.mrms.domain.service.CaseRecordHomepageManager;
import com.openhis.whale.mrms.domain.service.CaseRecordManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 病案首页提交前校验（Wave 1）。
 */
@Component
@RequiredArgsConstructor
public class HomepageSubmitValidator {

    private final CaseRecordManager caseRecordManager;
    private final CaseRecordHomepageManager homepageManager;

    public void validate(Long caseRecordId) {
        List<String> errors = new ArrayList<>();
        CaseRecord caseRecord = caseRecordManager.requireById(caseRecordId);
        CaseRecordStatus status = CaseRecordStatus.fromCode(caseRecord.getStatusCode());
        if (status != CaseRecordStatus.DRAFT && status != CaseRecordStatus.RETURNED) {
            errors.add("仅草稿或已退回状态可提交");
        }
        CaseRecordHomepage homepage = homepageManager.requireByCaseRecordId(caseRecordId);
        if (Boolean.TRUE.equals(homepage.getFrozen())) {
            errors.add("病案首页已冻结，禁止提交");
        }
        requireText(errors, "psn_name", homepage.getPsnName());
        requireText(errors, "gend_code", homepage.getGendCode());
        if (homepage.getBrdy() == null) {
            errors.add("出生日期(brdy)不能为空");
        }
        requireText(errors, "certno", homepage.getCertno());
        requireText(errors, "ipt_no", homepage.getIptNo());
        requireText(errors, "adm_way_code", homepage.getAdmWayCode());
        requireText(errors, "adm_caty_code", homepage.getAdmCatyCode());
        if (homepage.getAdmTime() == null) {
            errors.add("入院时间(adm_time)不能为空");
        }
        requireText(errors, "dscg_caty_code", homepage.getDscgCatyCode());
        if (homepage.getDscgTime() == null) {
            errors.add("出院时间(dscg_time)不能为空");
        }
        if (homepage.getActIptDays() == null) {
            errors.add("住院天数(act_ipt_days)不能为空");
        }
        requireText(errors, "dscg_way_code", homepage.getDscgWayCode());
        requireText(errors, "chfdr_name", homepage.getChfdrName());
        requireText(errors, "atddr_name", homepage.getAtddrName());
        requireText(errors, "ipt_dr_name", homepage.getIptDrName());

        if (homepage.getAdmTime() != null && homepage.getDscgTime() != null
                && homepage.getDscgTime().isBefore(homepage.getAdmTime())) {
            errors.add("出院时间不能早于入院时间");
        }
        if (homepage.getAdmTime() != null && homepage.getDscgTime() != null && homepage.getActIptDays() != null) {
            long days = ChronoUnit.DAYS.between(homepage.getAdmTime().toLocalDate(), homepage.getDscgTime().toLocalDate());
            long expected = Math.max(days, 1);
            if (Math.abs(homepage.getActIptDays() - expected) > 1) {
                errors.add("住院天数与入出院日期差不一致");
            }
        }

        List<HomepageDiagnosis> diagnoses = homepageManager.listDiagnoses(caseRecordId);
        validateDiagnoses(errors, diagnoses);

        if (!errors.isEmpty()) {
            throw new ValidationException(String.join("；", errors));
        }
    }

    private void validateDiagnoses(List<String> errors, List<HomepageDiagnosis> diagnoses) {
        long mainCount = diagnoses.stream()
                .filter(d -> HomepageDiagType.DISCHARGE_MAIN.getCode().equals(d.getDiagTypeCode()))
                .filter(d -> StringUtils.hasText(d.getDiagCode()))
                .count();
        if (mainCount < 1) {
            errors.add("至少需 1 条出院主诊断且编码非空");
        }
        if (mainCount > 1) {
            errors.add("出院主诊断仅允许 1 条");
        }
        Set<String> sortKeys = new HashSet<>();
        for (HomepageDiagnosis diagnosis : diagnoses) {
            String key = diagnosis.getDiagTypeCode() + "#" + diagnosis.getSortNo();
            if (!sortKeys.add(key)) {
                errors.add("同一诊断类型内排序号不能重复");
                break;
            }
        }
    }

    private void requireText(List<String> errors, String field, String value) {
        if (!StringUtils.hasText(value)) {
            errors.add(field + " 不能为空");
        }
    }
}
