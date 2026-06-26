/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.domain.support;

import com.openhis.whale.mrms.domain.entity.HomepageDiagnosis;
import com.openhis.whale.mrms.domain.entity.HomepageProcedure;
import com.openhis.whale.mrms.domain.repository.HomepageDiagnosisRepository;
import com.openhis.whale.mrms.domain.repository.HomepageProcedureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 首页诊断/手术子表软删替换（聚合内子实体一致性）。
 */
@Component
@RequiredArgsConstructor
public class HomepageChildTableSupport {

    private final HomepageDiagnosisRepository diagnosisRepository;
    private final HomepageProcedureRepository procedureRepository;

    /**
     * 查首页诊断行。
     *
     * @param caseRecordId 主档主键
     * @return 诊断行
     */
    public List<HomepageDiagnosis> listDiagnoses(Long caseRecordId) {
        return diagnosisRepository
                .findByCaseRecordIdAndIsDeletedFalseOrderByDiagTypeCodeAscSortNoAsc(caseRecordId);
    }

    /**
     * 查首页手术行。
     *
     * @param caseRecordId 主档主键
     * @return 手术行
     */
    public List<HomepageProcedure> listProcedures(Long caseRecordId) {
        return procedureRepository.findByCaseRecordIdAndIsDeletedFalseOrderBySortNoAsc(caseRecordId);
    }

    /**
     * 全量替换诊断行（软删旧行后插入新行）。
     *
     * @param caseRecordId 主档主键
     * @param tenantId 租户
     * @param lines 新行模板（可含部分字段）
     */
    public void replaceDiagnoses(Long caseRecordId, Long tenantId, List<HomepageDiagnosis> lines) {
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

    /**
     * 全量替换手术行（软删旧行后插入新行）。
     *
     * @param caseRecordId 主档主键
     * @param tenantId 租户
     * @param lines 新行模板
     */
    public void replaceProcedures(Long caseRecordId, Long tenantId, List<HomepageProcedure> lines) {
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

    /**
     * 自动取值路径：软删后按快照行落库（行对象已含业务字段）。
     *
     * @param caseRecordId 主档主键
     * @param tenantId 租户
     * @param lines 完整行
     */
    public void replaceDiagnosisSnapshots(Long caseRecordId, Long tenantId, List<HomepageDiagnosis> lines) {
        softDeleteDiagnoses(caseRecordId);
        if (lines == null || lines.isEmpty()) {
            return;
        }
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
     * 自动取值路径：软删后按快照行落库。
     *
     * @param caseRecordId 主档主键
     * @param tenantId 租户
     * @param lines 完整行
     */
    public void replaceProcedureSnapshots(Long caseRecordId, Long tenantId, List<HomepageProcedure> lines) {
        softDeleteProcedures(caseRecordId);
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (HomepageProcedure line : lines) {
            line.setCaseRecordId(caseRecordId);
            line.setTenantId(tenantId);
            line.setValiFlag("1");
            line.setCreatedAt(LocalDateTime.now());
            line.setIsDeleted(false);
        }
        procedureRepository.saveAll(lines);
    }

    private void softDeleteDiagnoses(Long caseRecordId) {
        List<HomepageDiagnosis> existing = diagnosisRepository.findByCaseRecordIdAndIsDeletedFalse(caseRecordId);
        for (HomepageDiagnosis row : existing) {
            row.setIsDeleted(true);
        }
        diagnosisRepository.saveAll(existing);
    }

    private void softDeleteProcedures(Long caseRecordId) {
        List<HomepageProcedure> existing = procedureRepository.findByCaseRecordIdAndIsDeletedFalse(caseRecordId);
        for (HomepageProcedure row : existing) {
            row.setIsDeleted(true);
        }
        procedureRepository.saveAll(existing);
    }
}
