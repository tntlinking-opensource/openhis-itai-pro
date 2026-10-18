/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.proceduredefinition.service;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.domain.clinical.proceduredefinition.command.AddProcedureDefinitionCommand;
import com.zxplat.domain.clinical.proceduredefinition.command.ClinicalProcV3ImportRowCommand;
import com.zxplat.domain.clinical.proceduredefinition.command.EditProcedureDefinitionCommand;
import com.zxplat.domain.clinical.proceduredefinition.command.EditProcedureDefinitionStartCommand;
import com.zxplat.domain.clinical.proceduredefinition.command.EditProcedureDefinitionStopCommand;
import com.zxplat.domain.clinical.proceduredefinition.command.GeneralOpImportRowCommand;
import com.zxplat.domain.clinical.proceduredefinition.entity.ProcedureDefinition;
import com.zxplat.domain.clinical.proceduredefinition.repository.ProcedureDefinitionRepository;
import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.domain.share.constants.BusinessSequenceConstants;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.ProcedureDefinitionConstants;
import com.zxplat.domain.share.enums.PublicationStatus;
import com.zxplat.domain.share.events.clinical.proceduredefinition.ProcedureDefinitionCatalogImportedEvent;
import com.zxplat.domain.share.events.clinical.proceduredefinition.ProcedureDefinitionCreatedEvent;
import com.zxplat.domain.share.events.clinical.proceduredefinition.ProcedureDefinitionDeletedEvent;
import com.zxplat.domain.share.events.clinical.proceduredefinition.ProcedureDefinitionDisabledEvent;
import com.zxplat.domain.share.events.clinical.proceduredefinition.ProcedureDefinitionEditedEvent;
import com.zxplat.domain.share.events.clinical.proceduredefinition.ProcedureDefinitionEnabledEvent;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import com.zxplat.framework.core.domain.event.IDomainEventPublisher;
import com.zxplat.framework.core.infrastructure.sequence.BusinessSequenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * 手术操作定义写侧领域服务（DM-CLN-009 §6.1）。
 *
 * <p>聚合边界：仅维护 {@code cln_procedure_definition} 主数据（双码规范化、对端同步、启停、软删、两路导入、
 * 可选挂靠 AD）；不写 {@code ActivityDefinition} 本体、不创建患者侧执行事实。</p>
 *
 * <p>不变量摘要：BR-PD-001～010（来源+主编码唯一、核心字段、检索码、双码规范化、
 * 对端多对多互指且国临 {@code yb_no} 为缺省上传码、启停≠软删、导入 upsert、挂靠 AD）。</p>
 *
 * <p>事件：写路径均在 save/saveAll/delete 成功之后再 {@link IDomainEventPublisher#publish}（beforeCommit / B.14）。</p>
 */
@Service
@RequiredArgsConstructor
public class ProcedureDefinitionDomainService {

    /** 两路导入每批落库条数。 */
    private static final int IMPORT_BATCH_SIZE = 1000;

    /** 本聚合仓储：持久化与编码存在性派生查询。 */
    private final ProcedureDefinitionRepository repository;
    /** 未传主编码时生成业务序列号。 */
    private final BusinessSequenceService businessSequenceService;
    /** 写路径领域事件发布器。 */
    private final IDomainEventPublisher domainEventPublisher;
    /** 国临 3.0 分批 upsert。 */
    private final ClinicalProcV3CatalogImportWriter clinicalProcV3CatalogImportWriter;
    /** 医保手术与操作分批 upsert。 */
    private final GeneralOpCatalogImportWriter generalOpCatalogImportWriter;

    /**
     * 新增手术操作定义（BR-PD-001～006、010）。
     *
     * @param cmd 新增命令；含可选 {@code activityDefinitionId}
     * @return 新建主键
     * @throws BusinessException 编码冲突或核心字段非法
     */
    public Long add(AddProcedureDefinitionCommand cmd) {
        // 前置校验：解析主编码（空则序列号）并验来源内唯一
        String code = resolveCodeForCreate(cmd.getSourceEnum(), cmd.getProcedureCode());
        // 组装聚合：基础字段 + 双码规范化
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
        // 可选挂靠 AD：支撑国临选品计费（BR-PD-010）
        entity.setActivityDefinitionId(cmd.getActivityDefinitionId());
        entity.assertCoreFields();
        entity.rebuildSearchKey();
        // 持久化 + 对端双码同步
        ProcedureDefinition saved = repository.save(entity);
        syncPeerCatalog(saved);
        // 发布事件：保存成功后再发（B.14）
        domainEventPublisher.publish(new ProcedureDefinitionCreatedEvent(
            saved.getId(), saved.getSourceEnum(), saved.getProcedureCode(), saved.getName(), saved.getStatusEnum()));
        return saved.getId();
    }

    /**
     * 编辑手术操作定义（BR-PD-001～006、010）。
     *
     * <p>{@code activityDefinitionId} 为 null 时本轮不改存量挂靠；非空时改绑。</p>
     *
     * @param cmd 编辑命令
     * @throws BusinessException 主键不存在、编码冲突或核心字段非法
     */
    public void edit(EditProcedureDefinitionCommand cmd) {
        // 前置：按 id 加载未删行
        ProcedureDefinition entity = loadById(cmd.getId());
        // 改码时重验来源内唯一（排除自身）
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
        // 状态空则保持原发布状态
        if (StringUtils.isNotEmpty(cmd.getStatusEnum())) {
            entity.setStatusEnum(cmd.getStatusEnum());
        }
        // 仅非空时改绑挂靠 AD；null 表示本轮不改存量挂靠（避免维护端未传字段时误清空）
        if (cmd.getActivityDefinitionId() != null) {
            entity.setActivityDefinitionId(cmd.getActivityDefinitionId());
        }
        entity.assertCoreFields();
        entity.rebuildSearchKey();
        ProcedureDefinition saved = repository.save(entity);
        syncPeerCatalog(saved);
        // 发布事件：编辑后
        domainEventPublisher.publish(new ProcedureDefinitionEditedEvent(
            saved.getId(),
            saved.getSourceEnum(),
            saved.getProcedureCode(),
            List.of("name", "procedureCode", "clinicCode", "ybNo", "pyStr", "wbStr", "statusEnum",
                "activityDefinitionId")));
    }

    /**
     * 批量停用（仅改发布状态，不以软删替代；BR-PD-007）。
     *
     * @param cmd 含 ids；须整批命中
     * @throws BusinessException 存在无效主键
     */
    public void stop(EditProcedureDefinitionStopCommand cmd) {
        List<ProcedureDefinition> entities = repository.findByIdIn(cmd.getIds());
        // 整批须全部命中，防止部分无效 id 静默跳过
        if (entities.size() != cmd.getIds().size()) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_DEFINITION_NOT_FOUND, "存在无效目录主键");
        }
        for (ProcedureDefinition entity : entities) {
            entity.disable();
        }
        repository.saveAll(entities);
        // 发布事件：落库后一次带 idList
        domainEventPublisher.publish(new ProcedureDefinitionDisabledEvent(
            cmd.getIds(), PublicationStatus.INACTIVE.getCode()));
    }

    /**
     * 软删除手术目录（列表不再展示；BR-PD-008）。
     *
     * @param id 主键
     * @throws BusinessException 不存在或已删除
     */
    public void delete(Long id) {
        // 前置：仅未删行可软删
        ProcedureDefinition entity = repository.findById(id)
            .filter(row -> !Boolean.TRUE.equals(row.getIsDeleted()))
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.PROCEDURE_DEFINITION_NOT_FOUND, "手术目录不存在或已删除"));
        String sourceEnum = entity.getSourceEnum();
        String procedureCode = entity.getProcedureCode();
        repository.delete(entity);
        // 发布事件：软删后
        domainEventPublisher.publish(new ProcedureDefinitionDeletedEvent(id, sourceEnum, procedureCode));
    }

    /**
     * 批量启用（仅改发布状态；BR-PD-007）。
     *
     * @param cmd 含 ids；须整批命中
     * @throws BusinessException 存在无效主键
     */
    public void start(EditProcedureDefinitionStartCommand cmd) {
        List<ProcedureDefinition> entities = repository.findByIdIn(cmd.getIds());
        if (entities.size() != cmd.getIds().size()) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_DEFINITION_NOT_FOUND, "存在无效目录主键");
        }
        for (ProcedureDefinition entity : entities) {
            entity.enable();
        }
        repository.saveAll(entities);
        domainEventPublisher.publish(new ProcedureDefinitionEnabledEvent(
            cmd.getIds(), PublicationStatus.ACTIVE.getCode()));
    }

    /**
     * 批量导入国临 3.0：按 {@code (clinical_proc_v3, procedureCode)} upsert（BR-PD-009）。
     *
     * @param rows             导入行
     * @param progressCallback 已处理条数回调，可为 null
     * @return 写入条数
     */
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
            // 非法行跳过（BR-PD-009）
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
        // 发布事件：整次导入完成后
        domainEventPublisher.publish(new ProcedureDefinitionCatalogImportedEvent(
            ProcedureDefinitionConstants.SOURCE_CLINICAL_PROC_V3, written));
        return written;
    }

    /**
     * 国临导入无进度回调重载。
     *
     * @param rows 导入行
     * @return 写入条数
     */
    public int importClinicalProcV3Catalog(List<ClinicalProcV3ImportRowCommand> rows) {
        return importClinicalProcV3Catalog(rows, null);
    }

    /**
     * 批量导入医保「手术与操作」：按 {@code (general_op, procedureCode)} upsert（BR-PD-009）。
     *
     * <p>仅 1308 第 16 列临床版码写入国临对照并可补建 {@code clinical_proc_v3}；
     * 该列为空或占位时不把医保码当作国临码（BR-PD-006）。</p>
     *
     * @param rows             导入行
     * @param progressCallback 已处理条数回调，可为 null
     * @return 写入医保目录条数
     */
    public int importGeneralOpCatalog(List<GeneralOpImportRowCommand> rows, IntConsumer progressCallback) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        Map<String, GeneralOpImportRowCommand> byCode = new LinkedHashMap<>();
        for (GeneralOpImportRowCommand row : rows) {
            if (row == null) {
                continue;
            }
            String code = trimToNull(row.getProcedureCode());
            String name = trimToNull(row.getName());
            // 非法行跳过（BR-PD-009）
            if (code == null || name == null) {
                continue;
            }
            GeneralOpImportRowCommand normalized = new GeneralOpImportRowCommand();
            normalized.setProcedureCode(code);
            normalized.setName(name);
            normalized.setPyStr(trimToNull(row.getPyStr()));
            normalized.setFileClinicCode(catalogCode(row.getFileClinicCode()));
            normalized.setFileClinicName(catalogCode(row.getFileClinicName()));
            byCode.put(code, normalized);
        }
        if (byCode.isEmpty()) {
            return 0;
        }
        // Pre：仅第 16 列有临床版码才绑定并补建国临；没有则不改已有对照
        bindAndSyncClinical(byCode);
        List<String> allCodes = new ArrayList<>(byCode.keySet());
        int written = 0;
        for (int from = 0; from < allCodes.size(); from += IMPORT_BATCH_SIZE) {
            int to = Math.min(from + IMPORT_BATCH_SIZE, allCodes.size());
            written += generalOpCatalogImportWriter.writeBatch(allCodes.subList(from, to), byCode);
            if (progressCallback != null) {
                progressCallback.accept(written);
            }
        }
        domainEventPublisher.publish(new ProcedureDefinitionCatalogImportedEvent(
            ProcedureDefinitionConstants.SOURCE_GENERAL_OP, written));
        return written;
    }

    /**
     * 医保手术导入无进度回调重载。
     *
     * @param rows 导入行
     * @return 写入条数
     */
    public int importGeneralOpCatalog(List<GeneralOpImportRowCommand> rows) {
        return importGeneralOpCatalog(rows, null);
    }

    /**
     * 按来源规范化双码字段（BR-PD-004/005）。
     *
     * <ul>
     *   <li>{@code clinical_proc_v3}：国临主码；{@code clinic_code=procedure_code}；医保码由选择写入</li>
     *   <li>{@code general_op}：医保主码；{@code yb_no} 默认等于编码；国临码由选择写入</li>
     * </ul>
     *
     * @param entity        待写实体
     * @param sourceEnum    来源码
     * @param procedureCode 主编码
     * @param ybNo          医保码
     * @param ybFlag        医保标记（非标准源透传）
     * @param ybMatchFlag   对码标记（非标准源透传）
     * @param clinicCode    国临码
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
            // 国临行：有医保码则置标记，否则清空标记
            if (yb != null) {
                entity.setYbFlag(Boolean.TRUE);
                entity.setYbMatchFlag(Boolean.TRUE);
            } else {
                entity.setYbFlag(Boolean.FALSE);
                entity.setYbMatchFlag(Boolean.FALSE);
            }
            return;
        }
        if (ProcedureDefinitionConstants.SOURCE_GENERAL_OP.equals(source)) {
            String yb = trimToNull(ybNo);
            if (yb == null) {
                yb = procedureCode;
            }
            entity.setYbNo(yb);
            entity.setYbFlag(Boolean.TRUE);
            String clinic = trimToNull(clinicCode);
            entity.setClinicCode(clinic);
            // 医保目录有国临码才算已对上
            if (clinic != null) {
                entity.setYbMatchFlag(Boolean.TRUE);
            } else {
                entity.setYbMatchFlag(Boolean.FALSE);
            }
            return;
        }
        entity.setYbNo(trimToNull(ybNo));
        entity.setYbFlag(ybFlag);
        entity.setYbMatchFlag(ybMatchFlag);
        entity.setClinicCode(trimToNull(clinicCode));
    }

    /**
     * 保存后双向同步对照（BR-PD-006）：多对多互指，不拆其它行；对端空字段才回写。
     *
     * @param saved 刚保存的本源行
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

    /**
     * 医保行保存后：国临行 {@code yb_no} 为空才写入缺省上传码；不拆除其它医保行对照。
     *
     * @param generalOp 医保源行
     */
    private void syncFromGeneralOp(ProcedureDefinition generalOp) {
        String clinic = trimToNull(generalOp.getClinicCode());
        if (clinic == null) {
            return;
        }
        String yb = trimToNull(generalOp.getYbNo());
        // 医保行 yb_no 空时回落主编码
        if (yb == null) {
            yb = trimToNull(generalOp.getProcedureCode());
        }
        if (yb == null) {
            return;
        }
        String ybFinal = yb;
        repository.findBySourceEnumAndProcedureCode(
                ProcedureDefinitionConstants.SOURCE_CLINICAL_PROC_V3, clinic)
            .ifPresent(clinical -> {
                // 已有缺省医保码不覆盖
                if (trimToNull(clinical.getYbNo()) != null) {
                    return;
                }
                clinical.setClinicCode(clinical.getProcedureCode());
                clinical.setYbNo(ybFinal);
                clinical.setYbFlag(Boolean.TRUE);
                clinical.setYbMatchFlag(Boolean.TRUE);
                repository.save(clinical);
            });
    }

    /**
     * 国临行保存后：医保对端 {@code clinic_code} 为空才回写；不拆除其它国临对照。
     *
     * @param clinical 国临源行
     */
    private void syncFromClinicalProcV3(ProcedureDefinition clinical) {
        String clinic = trimToNull(clinical.getProcedureCode());
        String yb = trimToNull(clinical.getYbNo());
        if (clinic == null || yb == null) {
            return;
        }
        String clinicFinal = clinic;
        repository.findBySourceEnumAndProcedureCode(
                ProcedureDefinitionConstants.SOURCE_GENERAL_OP, yb)
            .ifPresentOrElse(
                generalOp -> fillGeneralOpClinicIfEmpty(generalOp, clinicFinal),
                () -> {
                    // 按 yb_no 兜底（编码与 yb_no 不一致的历史行）
                    List<ProcedureDefinition> byYb = repository.findBySourceEnumAndYbNo(
                        ProcedureDefinitionConstants.SOURCE_GENERAL_OP, yb);
                    if (!byYb.isEmpty()) {
                        fillGeneralOpClinicIfEmpty(byYb.get(0), clinicFinal);
                    }
                });
    }

    /**
     * 医保行国临码为空时回写；已有值视为手工或多对多中的其它国临，不覆盖。
     *
     * @param generalOp 医保行
     * @param clinic    国临码
     */
    private void fillGeneralOpClinicIfEmpty(ProcedureDefinition generalOp, String clinic) {
        if (trimToNull(generalOp.getClinicCode()) != null) {
            return;
        }
        generalOp.setClinicCode(clinic);
        generalOp.setYbFlag(Boolean.TRUE);
        generalOp.setYbMatchFlag(Boolean.TRUE);
        repository.save(generalOp);
    }

    /**
     * 按 1308 第 16 列绑定国临码，并分批同步国临目录。
     *
     * <p>临床版列为空或占位时不使用第 11 列医保码补建/绑定，也不改已有对照
     * （权威对照里大量国临码与医保码本来相同）。</p>
     *
     * @param byCode 已归并的医保导入行
     */
    private void bindAndSyncClinical(Map<String, GeneralOpImportRowCommand> byCode) {
        Map<String, ClinicalProcV3ImportRowCommand> toCreate = new LinkedHashMap<>();
        for (GeneralOpImportRowCommand row : byCode.values()) {
            String bind = catalogCode(row.getFileClinicCode());
            // 文件没给临床版码：不补建国临，也不动库里已有对照
            if (bind == null) {
                continue;
            }
            row.setBindClinicCode(bind);
            // 同一国临码多条医保时只补建一次
            if (toCreate.containsKey(bind)) {
                continue;
            }
            ClinicalProcV3ImportRowCommand clinical = new ClinicalProcV3ImportRowCommand();
            clinical.setProcedureCode(bind);
            String clinicName = catalogCode(row.getFileClinicName());
            // 临床版名称为占位时，用医保手术名称补建国临行
            if (clinicName == null) {
                clinicName = catalogCode(row.getName());
            }
            clinical.setName(clinicName);
            toCreate.put(bind, clinical);
        }
        syncClinicalFromFile(byCode, toCreate);
    }

    /**
     * 仅对第 16 列给出的国临码补建缺失行，并回填空的缺省医保码。
     *
     * @param byCode   已绑定临床版码的医保导入行
     * @param toCreate 待补建国临行
     */
    private void syncClinicalFromFile(
            Map<String, GeneralOpImportRowCommand> byCode,
            Map<String, ClinicalProcV3ImportRowCommand> toCreate) {
        Map<String, String> firstYb = firstYbByClinic(byCode);
        List<String> clinicalKeys = new ArrayList<>(firstYb.keySet());
        for (String createCode : toCreate.keySet()) {
            // 已在缺省医保码映射中的国临码不重复加入
            if (firstYb.containsKey(createCode)) {
                continue;
            }
            clinicalKeys.add(createCode);
        }
        // 本批没有任何临床版码则不扫国临表
        if (clinicalKeys.isEmpty()) {
            return;
        }
        for (int from = 0; from < clinicalKeys.size(); from += IMPORT_BATCH_SIZE) {
            int to = Math.min(from + IMPORT_BATCH_SIZE, clinicalKeys.size());
            clinicalProcV3CatalogImportWriter.syncFromInsurance(
                clinicalKeys.subList(from, to), toCreate, firstYb);
        }
    }

    /**
     * 目录码占位清洗：空白、{@code null}、{@code --} 视为无码。
     *
     * @param raw 原始码
     * @return 可用编码；无则 null
     */
    private static String catalogCode(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        if ("null".equalsIgnoreCase(value) || "--".equals(value)) {
            return null;
        }
        return value;
    }

    /**
     * 每个国临码取本批第一条医保码，作为缺省上传码候选。
     *
     * @param byCode 已绑定的医保导入行
     * @return 国临码 → 缺省医保码
     */
    private Map<String, String> firstYbByClinic(Map<String, GeneralOpImportRowCommand> byCode) {
        Map<String, String> firstYb = new LinkedHashMap<>();
        for (GeneralOpImportRowCommand row : byCode.values()) {
            String clinic = trimToNull(row.getBindClinicCode());
            if (clinic == null) {
                continue;
            }
            if (firstYb.containsKey(clinic)) {
                continue;
            }
            firstYb.put(clinic, row.getProcedureCode());
        }
        return firstYb;
    }

    /**
     * 按主键加载目录实体；不存在则阻断。
     *
     * @param id 主键
     * @return 实体
     * @throws BusinessException 不存在
     */
    private ProcedureDefinition loadById(Long id) {
        return repository.findById(id)
            .filter(row -> !Boolean.TRUE.equals(row.getIsDeleted()))
            .orElseThrow(
                () -> new BusinessException(ErrorCodeConstants.PROCEDURE_DEFINITION_NOT_FOUND, "手术定义不存在"));
    }

    /**
     * 断言来源+主编码唯一（BR-PD-001）。
     *
     * @param sourceEnum    来源码
     * @param procedureCode 主编码
     * @param id            编辑时排除自身；新增传 null
     */
    private void assertCodeUnique(String sourceEnum, String procedureCode, Long id) {
        if (id == null && repository.existsBySourceEnumAndProcedureCode(sourceEnum, procedureCode)) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_DEFINITION_CODE_CONFLICT, "手术编码冲突");
        }
        if (id != null && repository.existsBySourceEnumAndProcedureCodeAndIdNot(sourceEnum, procedureCode, id)) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_DEFINITION_CODE_CONFLICT, "手术编码冲突");
        }
    }

    /**
     * 新增时解析主编码：请求非空则验唯一；空则序列号生成后再验。
     *
     * @param sourceEnum    来源码
     * @param requestedCode 请求编码
     * @return 落库主编码
     */
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

    /**
     * 编辑时解析主编码：请求空则保持原码；非空则验唯一后改码。
     *
     * @param sourceEnum    来源码
     * @param requestedCode 请求编码
     * @param id            主键
     * @param currentCode   当前主编码
     * @return 落库主编码
     */
    private String resolveCodeForUpdate(String sourceEnum, String requestedCode, Long id, String currentCode) {
        String trimmed = trimToNull(requestedCode);
        if (trimmed == null) {
            return currentCode;
        }
        assertCodeUnique(sourceEnum, trimmed, id);
        return trimmed;
    }
}
