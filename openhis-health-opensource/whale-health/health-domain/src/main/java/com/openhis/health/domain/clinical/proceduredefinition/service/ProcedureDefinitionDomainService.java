/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.proceduredefinition.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.domain.clinical.proceduredefinition.command.AddProcedureDefinitionCommand;
import com.openhis.health.domain.clinical.proceduredefinition.command.ClinicalProcV3ImportRowCommand;
import com.openhis.health.domain.clinical.proceduredefinition.command.EditProcedureDefinitionCommand;
import com.openhis.health.domain.clinical.proceduredefinition.command.EditProcedureDefinitionStartCommand;
import com.openhis.health.domain.clinical.proceduredefinition.command.EditProcedureDefinitionStopCommand;
import com.openhis.health.domain.clinical.proceduredefinition.command.GeneralOpImportRowCommand;
import com.openhis.health.domain.clinical.proceduredefinition.entity.ProcedureDefinition;
import com.openhis.health.domain.clinical.proceduredefinition.repository.ProcedureDefinitionRepository;
import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.ProcedureDefinitionConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * 手术操作定义写侧领域服务。
 */
@Service
@RequiredArgsConstructor
public class ProcedureDefinitionDomainService {

    private static final int IMPORT_BATCH_SIZE = 1000;

    private final ProcedureDefinitionRepository repository;
    private final BusinessSequenceService businessSequenceService;
    private final ClinicalProcV3CatalogImportWriter clinicalProcV3CatalogImportWriter;
    private final GeneralOpCatalogImportWriter generalOpCatalogImportWriter;

    public Long add(AddProcedureDefinitionCommand cmd) {
        String code = resolveCodeForCreate(cmd.getSourceEnum(), cmd.getProcedureCode());
        ProcedureDefinition entity = new ProcedureDefinition();
        entity.setSourceEnum(cmd.getSourceEnum());
        entity.setProcedureCode(code);
        entity.setName(cmd.getName());
        entity.setPyStr(cmd.getPyStr());
        entity.setWbStr(cmd.getWbStr());
        entity.setTypeCode(cmd.getTypeCode());
        entity.setDescription(cmd.getDescription());
        applyCrossCodeFields(entity, cmd.getSourceEnum(), code, cmd.getYbNo(), cmd.getYbFlag(),
            cmd.getYbMatchFlag(), cmd.getClinicCode());
        entity.setStatusEnum(cmd.getStatusEnum());
        entity.assertCoreFields();
        entity.rebuildSearchKey();
        ProcedureDefinition saved = repository.save(entity);
        syncPeerCatalog(saved);
        return saved.getId();
    }

    public void edit(EditProcedureDefinitionCommand cmd) {
        ProcedureDefinition entity = loadById(cmd.getId());
        String code = resolveCodeForUpdate(cmd.getSourceEnum(), cmd.getProcedureCode(), cmd.getId(), entity.getProcedureCode());
        entity.setSourceEnum(cmd.getSourceEnum());
        entity.setProcedureCode(code);
        entity.setName(cmd.getName());
        entity.setPyStr(cmd.getPyStr());
        entity.setWbStr(cmd.getWbStr());
        entity.setTypeCode(cmd.getTypeCode());
        entity.setDescription(cmd.getDescription());
        applyCrossCodeFields(entity, cmd.getSourceEnum(), code, cmd.getYbNo(), cmd.getYbFlag(),
            cmd.getYbMatchFlag(), cmd.getClinicCode());
        if (StringUtils.isNotEmpty(cmd.getStatusEnum())) {
            entity.setStatusEnum(cmd.getStatusEnum());
        }
        entity.assertCoreFields();
        entity.rebuildSearchKey();
        ProcedureDefinition saved = repository.save(entity);
        syncPeerCatalog(saved);
    }

    /**
     * 按来源规范化双码字段：
     * <ul>
     *   <li>{@code clinical_proc_v3}：国临主码；{@code clinic_code=procedure_code}；医保码由选择写入并与 general_op 对齐</li>
     *   <li>{@code general_op}：医保主码；{@code yb_no} 默认等于编码；国临码由选择写入并与 clinical_proc_v3 对齐</li>
     * </ul>
     */
    private void applyCrossCodeFields(
            ProcedureDefinition entity,
            String sourceEnum,
            String procedureCode,
            String ybNo,
            Boolean ybFlag,
            Boolean ybMatchFlag,
            String clinicCode) {
        String source = trimToNull(sourceEnum);
        if (ProcedureDefinitionConstants.SOURCE_CLINICAL_PROC_V3.equals(source)) {
            entity.setClinicCode(procedureCode);
            String yb = trimToNull(ybNo);
            entity.setYbNo(yb);
            entity.setYbFlag(yb != null ? Boolean.TRUE : Boolean.FALSE);
            entity.setYbMatchFlag(yb != null ? Boolean.TRUE : Boolean.FALSE);
            return;
        }
        if (ProcedureDefinitionConstants.SOURCE_GENERAL_OP.equals(source)) {
            String yb = trimToNull(ybNo);
            if (yb == null) {
                yb = procedureCode;
            }
            entity.setYbNo(yb);
            entity.setYbFlag(Boolean.TRUE);
            entity.setYbMatchFlag(Boolean.TRUE);
            entity.setClinicCode(trimToNull(clinicCode));
            return;
        }
        entity.setYbNo(trimToNull(ybNo));
        entity.setYbFlag(ybFlag);
        entity.setYbMatchFlag(ybMatchFlag);
        entity.setClinicCode(trimToNull(clinicCode));
    }

    /**
     * 保存后双向同步对照：general_op.clinic_code ↔ clinical_proc_v3.yb_no。
     */
    private void syncPeerCatalog(ProcedureDefinition saved) {
        if (saved == null || Boolean.TRUE.equals(saved.getIsDeleted())) {
            return;
        }
        String source = trimToNull(saved.getSourceEnum());
        if (ProcedureDefinitionConstants.SOURCE_GENERAL_OP.equals(source)) {
            syncFromGeneralOp(saved);
            return;
        }
        if (ProcedureDefinitionConstants.SOURCE_CLINICAL_PROC_V3.equals(source)) {
            syncFromClinicalProcV3(saved);
        }
    }

    private void syncFromGeneralOp(ProcedureDefinition generalOp) {
        String clinic = trimToNull(generalOp.getClinicCode());
        String ybNo = trimToNull(generalOp.getYbNo());
        String yb = ybNo != null ? ybNo : trimToNull(generalOp.getProcedureCode());
        // 同一国临只保留一条医保对照
        if (clinic != null) {
            for (ProcedureDefinition other : repository.findBySourceEnumAndClinicCode(
                ProcedureDefinitionConstants.SOURCE_GENERAL_OP, clinic)) {
                if (!Objects.equals(other.getId(), generalOp.getId())) {
                    other.setClinicCode(null);
                    repository.save(other);
                }
            }
            repository.findBySourceEnumAndProcedureCode(
                    ProcedureDefinitionConstants.SOURCE_CLINICAL_PROC_V3, clinic)
                .ifPresent(clinical -> {
                    clinical.setClinicCode(clinical.getProcedureCode());
                    clinical.setYbNo(yb);
                    clinical.setYbFlag(yb != null);
                    clinical.setYbMatchFlag(yb != null);
                    repository.save(clinical);
                });
        }
    }

    private void syncFromClinicalProcV3(ProcedureDefinition clinical) {
        String clinic = trimToNull(clinical.getProcedureCode());
        String yb = trimToNull(clinical.getYbNo());
        // 清掉其它仍指向本国会临的医保行（本次将改挂或清空）
        for (ProcedureDefinition other : repository.findBySourceEnumAndClinicCode(
            ProcedureDefinitionConstants.SOURCE_GENERAL_OP, clinic)) {
            String otherYb = trimToNull(other.getYbNo());
            if (otherYb == null) {
                otherYb = trimToNull(other.getProcedureCode());
            }
            if (yb == null || !Objects.equals(otherYb, yb)) {
                other.setClinicCode(null);
                repository.save(other);
            }
        }
        if (yb == null) {
            return;
        }
        repository.findBySourceEnumAndProcedureCode(
                ProcedureDefinitionConstants.SOURCE_GENERAL_OP, yb)
            .ifPresentOrElse(
                generalOp -> {
                    generalOp.setClinicCode(clinic);
                    generalOp.setYbNo(yb);
                    generalOp.setYbFlag(Boolean.TRUE);
                    generalOp.setYbMatchFlag(Boolean.TRUE);
                    repository.save(generalOp);
                },
                () -> {
                    // 按 yb_no 兜底（编码与 yb_no 不一致的历史行）
                    List<ProcedureDefinition> byYb = repository.findBySourceEnumAndYbNo(
                        ProcedureDefinitionConstants.SOURCE_GENERAL_OP, yb);
                    if (!byYb.isEmpty()) {
                        ProcedureDefinition generalOp = byYb.get(0);
                        generalOp.setClinicCode(clinic);
                        generalOp.setYbFlag(Boolean.TRUE);
                        generalOp.setYbMatchFlag(Boolean.TRUE);
                        repository.save(generalOp);
                    }
                });
    }

    public void stop(EditProcedureDefinitionStopCommand cmd) {
        List<ProcedureDefinition> entities = repository.findByIdIn(cmd.getIds());
        if (entities.size() != cmd.getIds().size()) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_DEFINITION_NOT_FOUND, "存在无效目录主键");
        }
        for (ProcedureDefinition entity : entities) {
            entity.disable();
        }
        repository.saveAll(entities);
    }

    /**
     * 软删除手术目录（列表不再展示）。
     *
     * @param id 主键
     */
    public void delete(Long id) {
        ProcedureDefinition entity = repository.findById(id)
            .filter(row -> !Boolean.TRUE.equals(row.getIsDeleted()))
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.PROCEDURE_DEFINITION_NOT_FOUND, "手术目录不存在或已删除"));
        repository.delete(entity);
    }

    public void start(EditProcedureDefinitionStartCommand cmd) {
        List<ProcedureDefinition> entities = repository.findByIdIn(cmd.getIds());
        if (entities.size() != cmd.getIds().size()) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_DEFINITION_NOT_FOUND, "存在无效目录主键");
        }
        for (ProcedureDefinition entity : entities) {
            entity.enable();
        }
        repository.saveAll(entities);
    }

    public int importClinicalProcV3Catalog(List<ClinicalProcV3ImportRowCommand> rows, IntConsumer progressCallback) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        Map<String, ClinicalProcV3ImportRowCommand> byCode = new HashMap<>();
        for (ClinicalProcV3ImportRowCommand row : rows) {
            if (row == null) {
                continue;
            }
            String code = trimToNull(row.getProcedureCode());
            String name = trimToNull(row.getName());
            if (code == null || name == null) {
                continue;
            }
            ClinicalProcV3ImportRowCommand normalized = new ClinicalProcV3ImportRowCommand();
            normalized.setProcedureCode(code);
            normalized.setName(name);
            normalized.setTypeCode(trimToNull(row.getTypeCode()));
            byCode.put(code, normalized);
        }
        if (byCode.isEmpty()) {
            return 0;
        }
        List<String> allCodes = new ArrayList<>(byCode.keySet());
        int written = 0;
        for (int from = 0; from < allCodes.size(); from += IMPORT_BATCH_SIZE) {
            int to = Math.min(from + IMPORT_BATCH_SIZE, allCodes.size());
            written += clinicalProcV3CatalogImportWriter.writeBatch(allCodes.subList(from, to), byCode);
            if (progressCallback != null) {
                progressCallback.accept(written);
            }
        }
        return written;
    }

    public int importClinicalProcV3Catalog(List<ClinicalProcV3ImportRowCommand> rows) {
        return importClinicalProcV3Catalog(rows, null);
    }

    /**
     * 批量导入医保「手术与操作」：按 {@code (general_op, procedureCode)} upsert，
     * {@code yb_no} 与编码相同，状态 {@code ACTIVE}。
     *
     * @param rows             导入行
     * @param progressCallback 已处理条数回调，可为 null
     * @return 写入条数
     */
    public int importGeneralOpCatalog(List<GeneralOpImportRowCommand> rows, IntConsumer progressCallback) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        Map<String, GeneralOpImportRowCommand> byCode = new HashMap<>();
        for (GeneralOpImportRowCommand row : rows) {
            if (row == null) {
                continue;
            }
            String code = trimToNull(row.getProcedureCode());
            String name = trimToNull(row.getName());
            if (code == null || name == null) {
                continue;
            }
            GeneralOpImportRowCommand normalized = new GeneralOpImportRowCommand();
            normalized.setProcedureCode(code);
            normalized.setName(name);
            normalized.setPyStr(trimToNull(row.getPyStr()));
            byCode.put(code, normalized);
        }
        if (byCode.isEmpty()) {
            return 0;
        }
        List<String> allCodes = new ArrayList<>(byCode.keySet());
        int written = 0;
        for (int from = 0; from < allCodes.size(); from += IMPORT_BATCH_SIZE) {
            int to = Math.min(from + IMPORT_BATCH_SIZE, allCodes.size());
            written += generalOpCatalogImportWriter.writeBatch(allCodes.subList(from, to), byCode);
            if (progressCallback != null) {
                progressCallback.accept(written);
            }
        }
        return written;
    }

    public int importGeneralOpCatalog(List<GeneralOpImportRowCommand> rows) {
        return importGeneralOpCatalog(rows, null);
    }

    private ProcedureDefinition loadById(Long id) {
        return repository.findById(id).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.PROCEDURE_DEFINITION_NOT_FOUND, "手术定义不存在"));
    }

    private void assertCodeUnique(String sourceEnum, String procedureCode, Long id) {
        if (id == null && repository.existsBySourceEnumAndProcedureCode(sourceEnum, procedureCode)) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_DEFINITION_CODE_CONFLICT, "手术编码冲突");
        }
        if (id != null && repository.existsBySourceEnumAndProcedureCodeAndIdNot(sourceEnum, procedureCode, id)) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_DEFINITION_CODE_CONFLICT, "手术编码冲突");
        }
    }

    private String resolveCodeForCreate(String sourceEnum, String requestedCode) {
        String trimmed = trimToNull(requestedCode);
        if (trimmed != null) {
            assertCodeUnique(sourceEnum, trimmed, null);
            return trimmed;
        }
        String generated = businessSequenceService.getSeq(
            AggregateTypeConstants.PROCEDURE_DEFINITION, BusinessSequenceConstants.MASTER_DATA_3_PREFIX_SEQ_LENGTH);
        assertCodeUnique(sourceEnum, generated, null);
        return generated;
    }

    private String resolveCodeForUpdate(String sourceEnum, String requestedCode, Long id, String currentCode) {
        String trimmed = trimToNull(requestedCode);
        if (trimmed == null) {
            return currentCode;
        }
        assertCodeUnique(sourceEnum, trimmed, id);
        return trimmed;
    }
}
