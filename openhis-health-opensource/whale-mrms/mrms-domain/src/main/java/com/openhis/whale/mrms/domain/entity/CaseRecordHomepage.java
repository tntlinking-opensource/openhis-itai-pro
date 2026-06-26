/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.domain.entity;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import com.openhis.whale.mrms.domain.enums.FieldFillSource;
import com.openhis.whale.mrms.domain.enums.HomepageDiagType;
import com.openhis.whale.mrms.domain.model.HomepageSaveCommand;
import com.openhis.whale.mrms.domain.support.MrmsErrorCodes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 病案首页实体，Wave 1 标量字段 + JSON 扩展列。
 */
@Getter
@Setter
@Entity
@Table(name = "mrms_case_record_homepage")
public class CaseRecordHomepage extends BaseAuditedEntity {

    /** 所属病案主档主键。 */
    @Column(name = "case_record_id", nullable = false)
    private Long caseRecordId;

    /** 首页版本号，每次重大保存递增。 */
    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    /** 归档后冻结标记，true 时禁止编辑。 */
    @Column(name = "frozen", nullable = false)
    private Boolean frozen;

    /** 4401 首页流水号，首次保存时生成。 */
    @Column(name = "ipt_medcas_hmpg_sn", length = 30)
    private String iptMedcasHmpgSn;

    /** 字段来源 JSON，键=列名，值=manual/auto/mixed。 */
    @Column(name = "field_sources_json", columnDefinition = "TEXT")
    private String fieldSourcesJson;

    /** 扩展字段 JSON（费用分项等）。 */
    @Column(name = "extension_json", columnDefinition = "TEXT")
    private String extensionJson;

    @Column(name = "psn_name", length = 100)
    private String psnName;

    @Column(name = "gend_code", length = 16)
    private String gendCode;

    @Column(name = "brdy")
    private LocalDate brdy;

    @Column(name = "ntly_code", length = 16)
    private String ntlyCode;

    @Column(name = "naty_code", length = 16)
    private String natyCode;

    @Column(name = "certno", length = 32)
    private String certno;

    @Column(name = "prfs_code", length = 16)
    private String prfsCode;

    @Column(name = "mrg_stas_code", length = 16)
    private String mrgStasCode;

    @Column(name = "curr_addr", length = 500)
    private String currAddr;

    @Column(name = "psn_tel", length = 32)
    private String psnTel;

    @Column(name = "coner_name", length = 100)
    private String conerName;

    @Column(name = "coner_tel", length = 32)
    private String conerTel;

    @Column(name = "ipt_no", length = 64)
    private String iptNo;

    @Column(name = "adm_way_code", length = 16)
    private String admWayCode;

    @Column(name = "adm_caty_code", length = 32)
    private String admCatyCode;

    @Column(name = "adm_caty_name", length = 100)
    private String admCatyName;

    @Column(name = "adm_time")
    private LocalDateTime admTime;

    @Column(name = "dscg_caty_code", length = 32)
    private String dscgCatyCode;

    @Column(name = "dscg_caty_name", length = 100)
    private String dscgCatyName;

    @Column(name = "dscg_time")
    private LocalDateTime dscgTime;

    @Column(name = "ipt_days")
    private Integer iptDays;

    @Column(name = "transfer_dept_summary", length = 500)
    private String transferDeptSummary;

    @Column(name = "dscg_way_code", length = 16)
    private String dscgWayCode;

    @Column(name = "dept_director_name", length = 50)
    private String deptDirectorName;

    @Column(name = "chfdr_name", length = 50)
    private String chfdrName;

    @Column(name = "atddr_name", length = 50)
    private String atddrName;

    /** 主治医师 {@code adm_practitioner.id}（自动取值）。 */
    @Column(name = "atddr_practitioner_id")
    private Long atddrPractitionerId;

    @Column(name = "ipt_dr_name", length = 50)
    private String iptDrName;

    @Column(name = "resp_nurs_name", length = 50)
    private String respNursName;

    /**
     * 初始化空首页。
     */
    public void initEmpty(Long caseRecordId, Long tenantId, Long operatorId) {
        this.caseRecordId = caseRecordId;
        this.setTenantId(tenantId);
        this.versionNo = 1;
        this.frozen = false;
        this.iptMedcasHmpgSn = generateHmpgSn();
        this.setCreatedAt(LocalDateTime.now());
        this.setCreatedBy(operatorId);
        this.setIsDeleted(false);
    }

    /**
     * 断言首页未冻结可编辑。
     */
    public void assertEditable() {
        if (Boolean.TRUE.equals(frozen)) {
            throw new BusinessException(MrmsErrorCodes.HOMEPAGE_FROZEN, "首页已冻结不可编辑");
        }
    }

    /**
     * 递增版本并更新审计字段。
     *
     * @param operatorId 操作人
     */
    public void bumpVersion(Long operatorId) {
        this.versionNo = this.versionNo + 1;
        this.setUpdatedAt(LocalDateTime.now());
        this.setUpdatedBy(operatorId);
    }

    /**
     * 归档冻结首页。
     *
     * @param operatorId 操作人
     */
    public void freezeHomepage(Long operatorId) {
        this.frozen = true;
        this.setUpdatedAt(LocalDateTime.now());
        this.setUpdatedBy(operatorId);
    }

    /**
     * 应用医生手工保存的标量字段，并标记字段来源为 manual。
     *
     * @param command 保存命令（仅非 null 字段生效）
     * @param sources 字段来源 Map
     */
    public void applyManualFields(HomepageSaveCommand command, Map<String, String> sources) {
        if (command == null) {
            return;
        }
        markManualField(command.getPsnName(), "psn_name", sources, v -> this.psnName = v);
        markManualField(command.getGendCode(), "gend_code", sources, v -> this.gendCode = v);
        if (command.getBrdy() != null) {
            this.brdy = command.getBrdy();
            sources.put("brdy", FieldFillSource.MANUAL.getCode());
        }
        markManualField(command.getCertno(), "certno", sources, v -> this.certno = v);
        markManualField(command.getIptNo(), "ipt_no", sources, v -> this.iptNo = v);
        markManualField(command.getAdmWayCode(), "adm_way_code", sources, v -> this.admWayCode = v);
        markManualField(command.getAdmCatyCode(), "adm_caty_code", sources, v -> this.admCatyCode = v);
        if (command.getAdmTime() != null) {
            this.admTime = command.getAdmTime();
            sources.put("adm_time", FieldFillSource.MANUAL.getCode());
        }
        markManualField(command.getDscgCatyCode(), "dscg_caty_code", sources, v -> this.dscgCatyCode = v);
        if (command.getDscgTime() != null) {
            this.dscgTime = command.getDscgTime();
            sources.put("dscg_time", FieldFillSource.MANUAL.getCode());
        }
        if (command.getIptDays() != null) {
            this.iptDays = command.getIptDays();
            sources.put("ipt_days", FieldFillSource.MANUAL.getCode());
        }
        markManualField(command.getDscgWayCode(), "dscg_way_code", sources, v -> this.dscgWayCode = v);
        markManualField(command.getChfdrName(), "chfdr_name", sources, v -> this.chfdrName = v);
        markManualField(command.getAtddrName(), "atddr_name", sources, v -> this.atddrName = v);
        markManualField(command.getIptDrName(), "ipt_dr_name", sources, v -> this.iptDrName = v);
    }

    private void markManualField(
            String value,
            String sourceKey,
            Map<String, String> sources,
            java.util.function.Consumer<String> setter) {
        if (value == null) {
            return;
        }
        setter.accept(value);
        sources.put(sourceKey, FieldFillSource.MANUAL.getCode());
    }

    /**
     * 校验首页是否满足提交条件（不含主档状态，由 {@link CaseRecord} 负责）。
     *
     * @param diagnoses 当前有效诊断行
     */
    public void assertReadyToSubmit(List<HomepageDiagnosis> diagnoses) {
        List<String> errors = new ArrayList<>();
        if (Boolean.TRUE.equals(frozen)) {
            errors.add("首页已冻结不可提交");
        }
        validateRequired(errors, "姓名", psnName);
        validateRequired(errors, "性别", gendCode);
        if (brdy == null) {
            errors.add("出生日期不能为空");
        }
        validateRequired(errors, "证件号", certno);
        validateRequired(errors, "住院号", iptNo);
        validateRequired(errors, "入院途径", admWayCode);
        validateRequired(errors, "入院科别", admCatyCode);
        if (admTime == null) {
            errors.add("入院时间不能为空");
        }
        validateRequired(errors, "出院科别", dscgCatyCode);
        if (dscgTime == null) {
            errors.add("出院时间不能为空");
        }
        if (iptDays == null) {
            errors.add("住院天数不能为空");
        }
        validateRequired(errors, "离院方式", dscgWayCode);
        validateRequired(errors, "主任/副主任医师", chfdrName);
        validateRequired(errors, "主治医师", atddrName);
        validateRequired(errors, "住院医师", iptDrName);
        if (admTime != null && dscgTime != null && dscgTime.isBefore(admTime)) {
            errors.add("出院时间不能早于入院时间");
        }
        validateDischargeMainDiagnosis(errors, diagnoses);
        if (!errors.isEmpty()) {
            throw new BusinessException(
                    MrmsErrorCodes.HOMEPAGE_SUBMIT_VALIDATION,
                    String.join("；", errors));
        }
    }

    private void validateDischargeMainDiagnosis(List<String> errors, List<HomepageDiagnosis> diagnoses) {
        if (diagnoses == null || diagnoses.isEmpty()) {
            errors.add("出院主诊断编码不能为空");
            return;
        }
        boolean hasMainDischarge = false;
        int mainDischargeCount = 0;
        for (HomepageDiagnosis row : diagnoses) {
            if (!HomepageDiagType.DISCHARGE_MAIN.getCode().equals(row.getDiagTypeCode())) {
                continue;
            }
            if (trimToNull(row.getDiagCode()) != null) {
                hasMainDischarge = true;
            }
            if ("1".equals(row.getMaindiagFlag())) {
                mainDischargeCount = mainDischargeCount + 1;
            }
        }
        if (!hasMainDischarge) {
            errors.add("出院主诊断编码不能为空");
        }
        if (mainDischargeCount > 1) {
            errors.add("出院主诊断只能有一条");
        }
    }

    private void validateRequired(List<String> errors, String label, String value) {
        if (trimToNull(value) == null) {
            errors.add(label + "不能为空");
        }
    }

    private static String generateHmpgSn() {
        return "HMPG" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }
}
