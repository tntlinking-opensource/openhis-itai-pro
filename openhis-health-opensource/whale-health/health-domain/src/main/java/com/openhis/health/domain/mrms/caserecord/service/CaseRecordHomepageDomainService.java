/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.caserecord.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.health.domain.mrms.caserecord.entity.CaseRecord;
import com.openhis.health.domain.mrms.caserecord.entity.CaseRecordHomepage;
import com.openhis.health.domain.mrms.caserecord.entity.HomepageDiagnosis;
import com.openhis.health.domain.mrms.caserecord.entity.HomepageProcedure;
import com.openhis.health.domain.share.enums.FieldFillSource;
import com.openhis.health.domain.share.enums.HomepageDiagType;
import com.openhis.health.domain.mrms.caserecord.command.HomepageSaveCommand;
import com.openhis.health.domain.mrms.caserecord.support.CaseRecordAutoFillSupport;
import com.openhis.health.domain.mrms.caserecord.vo.DiagnosisLineSnapshot;
import com.openhis.health.domain.mrms.caserecord.vo.EncounterSnapshot;
import com.openhis.health.domain.mrms.caserecord.vo.PatientSnapshot;
import com.openhis.health.domain.mrms.caserecord.vo.ProcedureLineSnapshot;
import com.openhis.health.domain.mrms.caserecord.vo.TransferLineSnapshot;
import com.openhis.health.domain.mrms.caserecord.repository.CaseRecordHomepageRepository;
import com.openhis.health.domain.mrms.caserecord.repository.CaseRecordRepository;
import com.openhis.health.domain.mrms.caserecord.repository.HomepageDiagnosisRepository;
import com.openhis.health.domain.mrms.caserecord.repository.HomepageProcedureRepository;
import com.openhis.health.domain.share.constants.MrmsErrorCodes;
import com.openhis.health.domain.share.utils.FieldSourcesJsonUtil;
import com.openhis.health.domain.share.utils.HomepageExtensionJsonUtil;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 病案首页（{@code mrms_case_record_homepage}）聚合写侧领域服务。
 *
 * <p>职责：懒创建、人工保存、HIS 自动取值合并、子表全量对齐（软删旧行）、归档冻结/解档解冻。</p>
 * <p>读 HIS 快照经 {@link CaseRecordAutoFillSupport}（B.6.4：跨聚合读走各域 QueryService）。</p>
 */
@Service
@RequiredArgsConstructor
public class CaseRecordHomepageDomainService {

    private final CaseRecordRepository caseRecordRepository;
    private final CaseRecordHomepageRepository homepageRepository;
    private final HomepageDiagnosisRepository diagnosisRepository;
    private final HomepageProcedureRepository procedureRepository;
    private final CaseRecordAutoFillSupport caseRecordAutoFillSupport;
    private final ObjectMapper objectMapper;

    /**
     * 获取或创建首页；不存在时初始化空首页与 field_sources_json。
     *
     * @param caseRecordId 病案主档主键
     * @param tenantId 租户主键（创建路径必填）
     * @param operatorId 操作人
     * @return 已存在或新建的首页
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseRecordHomepage ensureHomepage(Long caseRecordId, Long tenantId, Long operatorId) {
        return homepageRepository.findByCaseRecordIdAndIsDeletedFalse(caseRecordId)
                .orElseGet(() -> {
                    // 懒创建路径须带租户
                    if (tenantId == null) {
                        throw new BusinessException(MrmsErrorCodes.CASE_RECORD_NOT_FOUND, "创建首页时租户不能为空");
                    }
                    CaseRecordHomepage homepage = new CaseRecordHomepage();
                    homepage.initEmpty(caseRecordId, tenantId, operatorId);
                    return homepageRepository.save(homepage);
                });
    }

    /**
     * 按病案主键加载已存在首页；不存在时 empty（只读，供质控等场景）。
     *
     * @param caseRecordId 病案主档主键
     * @return 首页实体或 empty
     */
    public Optional<CaseRecordHomepage> findOptionalByCaseRecordId(Long caseRecordId) {
        return homepageRepository.findByCaseRecordIdAndIsDeletedFalse(caseRecordId);
    }

    /**
     * 按病案主键加载已存在首页；不存在时抛业务异常。
     *
     * @param caseRecordId 病案主档主键
     * @return 首页实体
     */
    public CaseRecordHomepage requireExistingByCaseRecordId(Long caseRecordId) {
        return homepageRepository.findByCaseRecordIdAndIsDeletedFalse(caseRecordId)
                .orElseThrow(() -> new BusinessException(MrmsErrorCodes.UPLOAD_PRECONDITION, "首页不存在"));
    }

    /**
     * 保存首页标量、扩展与子表；子表非 null 时全量替换并标记 manual 来源。
     *
     * @param command 保存命令
     * @return 保存后的首页
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseRecordHomepage save(HomepageSaveCommand command) {
        // Pre：校验主档存在
        var record = caseRecordRepository.findByIdAndIsDeletedFalse(command.getCaseRecordId())
                .orElseThrow(() -> new BusinessException(MrmsErrorCodes.CASE_RECORD_NOT_FOUND, "病案主档不存在"));
        CaseRecordHomepage homepage = ensureHomepage(command.getCaseRecordId(), record.getTenantId(), command.getOperatorId());
        homepage.assertEditable();
        Map<String, String> sources = FieldSourcesJsonUtil.parse(objectMapper, homepage.getFieldSourcesJson());
        // Cmd：标量人工修订写入并 bump 版本
        homepage.applyManualFields(command, sources);
        homepage.setExtensionJson(mergeExtension(homepage.getExtensionJson(), command.getExtension()));
        // 客户端传入诊断子表时标记 manual 来源，后续自动取值不得覆盖
        if (command.getDiagnoses() != null) {
            sources.put("diagnoses", FieldFillSource.MANUAL.getCode());
        }
        // 客户端传入手术子表时标记 manual 来源，后续自动取值不得覆盖
        if (command.getProcedures() != null) {
            sources.put("procedures", FieldFillSource.MANUAL.getCode());
        }
        homepage.setFieldSourcesJson(FieldSourcesJsonUtil.toJson(objectMapper, sources));
        homepage.bumpVersion(command.getOperatorId());
        // 持久化首页标量
        homepageRepository.save(homepage);
        // 诊断子表非 null 时全量替换（null 表示本次不修改子表）
        if (command.getDiagnoses() != null) {
            replaceDiagnoses(command.getCaseRecordId(), homepage.getTenantId(), command.getDiagnoses());
        }
        // 手术子表非 null 时全量替换（null 表示本次不修改子表）
        if (command.getProcedures() != null) {
            replaceProcedures(command.getCaseRecordId(), homepage.getTenantId(), command.getProcedures());
        }
        return homepage;
    }

    /**
     * 从 HIS 读侧快照合并首页字段，尊重 {@code field_sources_json} 中 manual 标记。
     *
     * @param record 病案主档
     * @param sections 合并分区（{@code all/basic/inpatient/staff/diagnosis/procedure}）
     * @param forceOverwrite 是否强制覆盖 manual 字段
     * @param operatorId 操作人
     * @return 合并后的首页
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseRecordHomepage mergeAutoFillFromHis(
            CaseRecord record,
            List<String> sections,
            boolean forceOverwrite,
            Long operatorId) {
        // Cmd：加载首页与字段来源映射
        CaseRecordHomepage homepage = homepageRepository
                .findByCaseRecordIdAndIsDeletedFalse(record.getId())
                .orElseThrow();
        Map<String, String> sources = FieldSourcesJsonUtil.parse(objectMapper, homepage.getFieldSourcesJson());
        boolean all = sections != null && sections.contains("all");
        // 按分区合并标量与子表
        // 患者基础信息分区：姓名、证件、联系方式等
        if (all || containsSection(sections, "basic")) {
            mergeBasicSection(record, homepage, sources, forceOverwrite);
        }
        // 住院/出入院信息分区：入出院科别与时间
        if (all || containsSection(sections, "inpatient")) {
            mergeInpatientSection(record, homepage, sources, forceOverwrite);
        }
        // 参与人员分区：科主任、主治医师、护士等
        if (all || containsSection(sections, "staff")) {
            mergeStaffSection(record, homepage, sources, forceOverwrite);
        }
        // 诊断子表：尊重 manual 锁，实际写入后标记 auto 来源
        if (all || containsSection(sections, "diagnosis")) {
            if (mergeDiagnosisSection(record, homepage, forceOverwrite)) {
                sources.put("diagnoses", FieldFillSource.AUTO.getCode());
            }
        }
        // 手术子表：尊重 manual 锁，实际写入后标记 auto 来源
        if (all || containsSection(sections, "procedure")) {
            if (mergeProcedureSection(record, homepage, forceOverwrite)) {
                sources.put("procedures", FieldFillSource.AUTO.getCode());
            }
        }
        homepage.setFieldSourcesJson(FieldSourcesJsonUtil.toJson(objectMapper, sources));
        homepage.setUpdatedAt(LocalDateTime.now());
        homepage.setUpdatedBy(operatorId);
        return homepageRepository.save(homepage);
    }

    /**
     * 列出病案首页诊断子表（未软删，按类型与序号排序）。
     *
     * @param caseRecordId 病案主档主键
     * @return 诊断行列表
     */
    public List<HomepageDiagnosis> listDiagnoses(Long caseRecordId) {
        return diagnosisRepository
                .findByCaseRecordIdAndIsDeletedFalseOrderByDiagTypeCodeAscSortNoAsc(caseRecordId);
    }

    /**
     * 列出病案首页手术子表（未软删，按序号排序）。
     *
     * @param caseRecordId 病案主档主键
     * @return 手术行列表
     */
    public List<HomepageProcedure> listProcedures(Long caseRecordId) {
        return procedureRepository.findByCaseRecordIdAndIsDeletedFalseOrderBySortNoAsc(caseRecordId);
    }

    /**
     * 自动取值路径：软删后按快照行落库诊断行。
     */
    private void replaceDiagnosisSnapshots(Long caseRecordId, Long tenantId, List<HomepageDiagnosis> lines) {
        softDeleteDiagnoses(caseRecordId);
        // 无新行时仅软删旧行即可
        if (lines == null || lines.isEmpty()) {
            return;
        }
        // 遍历快照行补齐外键与审计字段后批量落库
        for (HomepageDiagnosis line : lines) {
            line.setCaseRecordId(caseRecordId);
            line.setTenantId(tenantId);
            line.setValiFlag("1");
            line.setCreatedAt(LocalDateTime.now());
            line.setIsDeleted(false);
        }
        diagnosisRepository.saveAll(lines);
    }

    /**
     * 自动取值路径：软删后按快照行落库手术行。
     */
    private void replaceProcedureSnapshots(Long caseRecordId, Long tenantId, List<HomepageProcedure> lines) {
        softDeleteProcedures(caseRecordId);
        // 无新行时仅软删旧行即可
        if (lines == null || lines.isEmpty()) {
            return;
        }
        // 遍历快照行补齐外键与审计字段后批量落库
        for (HomepageProcedure line : lines) {
            line.setCaseRecordId(caseRecordId);
            line.setTenantId(tenantId);
            line.setValiFlag("1");
            line.setCreatedAt(LocalDateTime.now());
            line.setIsDeleted(false);
        }
        procedureRepository.saveAll(lines);
    }

    /**
     * 归档冻结首页。
     *
     * @param caseRecordId 病案主档主键
     * @param operatorId 操作人
     */
    @Transactional(rollbackFor = Exception.class)
    public void freeze(Long caseRecordId, Long operatorId) {
        CaseRecordHomepage homepage = homepageRepository.findByCaseRecordIdAndIsDeletedFalse(caseRecordId)
                .orElse(null);
        // 无首页时跳过
        if (homepage == null) {
            return;
        }
        homepage.freezeHomepage(operatorId);
        // 持久化
        homepageRepository.save(homepage);
    }

    /**
     * 解档解冻首页。
     *
     * @param caseRecordId 病案主档主键
     * @param operatorId 操作人
     */
    @Transactional(rollbackFor = Exception.class)
    public void unfreeze(Long caseRecordId, Long operatorId) {
        CaseRecordHomepage homepage = homepageRepository.findByCaseRecordIdAndIsDeletedFalse(caseRecordId)
                .orElse(null);
        // 无首页时跳过
        if (homepage == null) {
            return;
        }
        homepage.unfreezeHomepage(operatorId);
        // 持久化
        homepageRepository.save(homepage);
    }

    /** 人工保存路径：软删旧行后重建诊断子表。 */
    private void replaceDiagnoses(Long caseRecordId, Long tenantId, List<HomepageDiagnosis> lines) {
        softDeleteDiagnoses(caseRecordId);
        if (lines == null || lines.isEmpty()) {
            return;
        }
        List<HomepageDiagnosis> toSave = new ArrayList<>();
        for (HomepageDiagnosis line : lines) {
            HomepageDiagnosis entity = new HomepageDiagnosis();
            entity.setCaseRecordId(caseRecordId);
            entity.setTenantId(tenantId);
            entity.setDiagTypeCode(line.getDiagTypeCode());
            entity.setSortNo(line.getSortNo());
            entity.setDiagCode(line.getDiagCode());
            entity.setDiagName(line.getDiagName());
            entity.setAdmCondCode(line.getAdmCondCode());
            entity.setMaindiagFlag(line.getMaindiagFlag());
            entity.setValiFlag("1");
            entity.setCreatedAt(LocalDateTime.now());
            entity.setIsDeleted(false);
            toSave.add(entity);
        }
        diagnosisRepository.saveAll(toSave);
    }

    /** 人工保存路径：软删旧行后重建手术子表。 */
    private void replaceProcedures(Long caseRecordId, Long tenantId, List<HomepageProcedure> lines) {
        softDeleteProcedures(caseRecordId);
        if (lines == null || lines.isEmpty()) {
            return;
        }
        List<HomepageProcedure> toSave = new ArrayList<>();
        for (HomepageProcedure line : lines) {
            HomepageProcedure entity = new HomepageProcedure();
            entity.setCaseRecordId(caseRecordId);
            entity.setTenantId(tenantId);
            entity.setSortNo(line.getSortNo());
            entity.setOprnOprtCode(line.getOprnOprtCode());
            entity.setOprnOprtName(line.getOprnOprtName());
            entity.setOprnDate(line.getOprnDate());
            entity.setMainOprnFlag(line.getMainOprnFlag());
            entity.setValiFlag("1");
            entity.setCreatedAt(LocalDateTime.now());
            entity.setIsDeleted(false);
            toSave.add(entity);
        }
        procedureRepository.saveAll(toSave);
    }

    /** 子表全量对齐：软删当前有效诊断行。 */
    private void softDeleteDiagnoses(Long caseRecordId) {
        List<HomepageDiagnosis> existing = diagnosisRepository.findByCaseRecordIdAndIsDeletedFalse(caseRecordId);
        for (HomepageDiagnosis row : existing) {
            row.setIsDeleted(true);
        }
        diagnosisRepository.saveAll(existing);
    }

    /** 子表全量对齐：软删当前有效手术行。 */
    private void softDeleteProcedures(Long caseRecordId) {
        List<HomepageProcedure> existing = procedureRepository.findByCaseRecordIdAndIsDeletedFalse(caseRecordId);
        for (HomepageProcedure row : existing) {
            row.setIsDeleted(true);
        }
        procedureRepository.saveAll(existing);
    }

    /** 合并患者基础信息分区（adm_patient）。 */
    private void mergeBasicSection(
            CaseRecord record,
            CaseRecordHomepage homepage,
            Map<String, String> sources,
            boolean force) {
        PatientSnapshot patient = caseRecordAutoFillSupport.loadPatient(record.getPatientId());
        // HIS 无患者快照时跳过本分区
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

    /** 合并住院信息分区（adm_encounter + 转科摘要）。 */
    private void mergeInpatientSection(
            CaseRecord record,
            CaseRecordHomepage homepage,
            Map<String, String> sources,
            boolean force) {
        EncounterSnapshot enc = caseRecordAutoFillSupport.loadEncounter(record.getEncounterId());
        // HIS 无就诊快照时跳过本分区
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
        // 出入院时间齐全时计算住院天数（4401 至少 1 天）
        if (enc.getAdmTime() != null && enc.getDscgTime() != null) {
            long days = ChronoUnit.DAYS.between(enc.getAdmTime().toLocalDate(), enc.getDscgTime().toLocalDate());
            // 同日出入院按 1 天计
            if (days < 1) {
                days = 1;
            }
            putIfAllowed(homepage, sources, "ipt_days", (int) days, force);
        }
        putIfAllowed(
                homepage,
                sources,
                "dscg_way_code",
                caseRecordAutoFillSupport.resolveDischargeWayCode(record.getEncounterId()),
                force);
        List<TransferLineSnapshot> transfers = caseRecordAutoFillSupport.listTransfers(record.getEncounterId());
        // 有转科里程碑时拼接科室路径摘要（A→B→C）
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

    /** 合并参与人员分区（就诊参与者）。 */
    private void mergeStaffSection(
            CaseRecord record,
            CaseRecordHomepage homepage,
            Map<String, String> sources,
            boolean force) {
        EncounterSnapshot enc = caseRecordAutoFillSupport.loadEncounter(record.getEncounterId());
        // HIS 无就诊快照时跳过本分区
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

    /**
     * 首页自动填充：合并出院/入院诊断子表；manual 锁定且非 force 时跳过。
     *
     * @param record   病案
     * @param homepage 首页快照
     * @param force    是否强制覆盖 manual 段
     * @return true 表示已写入
     */
    private boolean mergeDiagnosisSection(CaseRecord record, CaseRecordHomepage homepage, boolean force) {
        // 诊断子表已 manual 锁定且非强制覆盖时跳过
        if (!force && isManualSection(homepage, "diagnoses")) {
            return false;
        }
        List<HomepageDiagnosis> lines = new ArrayList<>();
        appendDiagLines(
                lines,
                HomepageDiagType.DISCHARGE_MAIN,
                caseRecordAutoFillSupport.listDischargeDiagnoses(record.getEncounterId()),
                true);
        appendDiagLines(
                lines,
                HomepageDiagType.DISCHARGE_OTHER,
                caseRecordAutoFillSupport.listDischargeDiagnoses(record.getEncounterId()),
                false);
        appendDiagLines(
                lines,
                HomepageDiagType.ADMISSION_MAIN,
                caseRecordAutoFillSupport.listAdmissionDiagnoses(record.getEncounterId()),
                true);
        appendDiagLines(
                lines,
                HomepageDiagType.ADMISSION_OTHER,
                caseRecordAutoFillSupport.listAdmissionDiagnoses(record.getEncounterId()),
                false);
        replaceDiagnosisSnapshots(record.getId(), homepage.getTenantId(), lines);
        return true;
    }

    /**
     * 首页自动填充：合并手术子表；manual 锁定且非 force 时跳过。
     *
     * @param record   病案
     * @param homepage 首页快照
     * @param force    是否强制覆盖 manual 段
     * @return true 表示已写入
     */
    private boolean mergeProcedureSection(CaseRecord record, CaseRecordHomepage homepage, boolean force) {
        // 手术子表已 manual 锁定且非强制覆盖时跳过
        if (!force && isManualSection(homepage, "procedures")) {
            return false;
        }
        List<ProcedureLineSnapshot> procLines =
                caseRecordAutoFillSupport.listProcedures(record.getEncounterId());
        List<HomepageProcedure> lines = new ArrayList<>();
        int sort = 0;
        for (ProcedureLineSnapshot snap : procLines) {
            HomepageProcedure row = new HomepageProcedure();
            row.setSortNo(sort++);
            row.setOprnOprtCode(snap.getOprnCode());
            row.setOprnOprtName(snap.getOprnName());
            row.setOprnDate(snap.getOprnDate());
            // 主手术标记：首条或快照 main=true 写 1，否则 0
            if (Boolean.TRUE.equals(snap.getMain())) {
                row.setMainOprnFlag("1");
            } else {
                row.setMainOprnFlag("0");
            }
            lines.add(row);
        }
        replaceProcedureSnapshots(record.getId(), homepage.getTenantId(), lines);
        return true;
    }

    /** 将 HIS 诊断快照追加为首页诊断行（主/次诊断分流）。 */
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
            // 主/次诊断分流：mainOnly 时跳过非主诊断行
            if (mainOnly && !Boolean.TRUE.equals(snap.getMain())) {
                continue;
            }
            // 次诊断分流：跳过主诊断行
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

    /** 判断子表/分区是否被人工编辑锁定。 */
    private boolean isManualSection(CaseRecordHomepage homepage, String sectionKey) {
        if (homepage == null) {
            return false;
        }
        Map<String, String> sources = FieldSourcesJsonUtil.parse(objectMapper, homepage.getFieldSourcesJson());
        return FieldFillSource.MANUAL.getCode().equals(sources.get(sectionKey));
    }

    /** 按 field_sources 与 force 决定是否写入标量并标记 auto。 */
    private void putIfAllowed(
            CaseRecordHomepage homepage,
            Map<String, String> sources,
            String fieldKey,
            Object newVal,
            boolean force) {
        // 源值为空时不覆盖首页字段
        if (newVal == null) {
            return;
        }
        String source = sources.get(fieldKey);
        // manual 锁定且非强制覆盖时保留医生手工值
        if (!force && FieldFillSource.MANUAL.getCode().equals(source)) {
            return;
        }
        applyAutoFillField(homepage, fieldKey, newVal);
        sources.put(fieldKey, FieldFillSource.AUTO.getCode());
    }

    /** 自动取值标量字段映射（switch 与首页列一一对应）。 */
    private void applyAutoFillField(CaseRecordHomepage homepage, String fieldKey, Object value) {
        switch (fieldKey) {
            case "psn_name" -> homepage.setPsnName(String.valueOf(value));
            case "gend_code" -> homepage.setGendCode(String.valueOf(value));
            case "brdy" -> homepage.setBrdy((LocalDate) value);
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
            }
        }
    }

    /** 判断合并请求是否包含指定分区 code。 */
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

    /** 浅合并 extension_json 键值（incoming 覆盖同 key）。 */
    private String mergeExtension(String existingJson, Map<String, Object> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return existingJson;
        }
        Map<String, Object> merged = new HashMap<>();
        if (existingJson != null && !existingJson.isBlank()) {
            // 扩展 JSON 损坏时以本次入参为准重建
            merged.putAll(HomepageExtensionJsonUtil.parse(objectMapper, existingJson));
        }
        merged.putAll(incoming);
        String serialized = HomepageExtensionJsonUtil.toJson(objectMapper, merged);
        if (serialized != null) {
            return serialized;
        }
        return existingJson;
    }
}
