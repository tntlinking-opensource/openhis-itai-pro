/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.conditiondefinition.service;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.domain.clinical.conditiondefinition.command.*;
import com.zxplat.domain.clinical.conditiondefinition.entity.ConditionDefinition;
import com.zxplat.domain.clinical.conditiondefinition.queries.model.EnsureChronicSpecOpspResult;
import com.zxplat.domain.clinical.conditiondefinition.repository.ConditionDefinitionRepository;
import com.zxplat.domain.share.constants.BusinessSequenceConstants;
import com.zxplat.domain.share.constants.DoctorStationDiagnosisConstants;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.enums.PublicationStatus;
import com.zxplat.domain.share.events.clinical.conditiondefinition.*;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.domain.event.IDomainEventPublisher;
import com.zxplat.framework.core.infrastructure.sequence.BusinessSequenceService;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntConsumer;
import lombok.RequiredArgsConstructor;

/**
 * 诊断定义（ConditionDefinition）目录写侧领域服务。
 *
 * <p>职责：主数据新增/编辑、批量启停、国临 2.0 批量导入；编码唯一性按
 * {@code (sourceEnum, conditionCode)} 由 {@link #assertCodeUnique} 保证；
 * 未显式传入诊断编码时走 {@link BusinessSequenceService#getSeq} 按聚合类型取号。</p>
 *
 * <p>事件：所有写路径均在 <strong>save / saveAll 成功之后</strong> 再发布领域事件（B.14）。
 * 批量启停采用「先统一 saveAll，再逐条发布」以避免落库失败产生幽灵事件。</p>
 *
 * <p>与医生站关系：医生站检索/录入读侧走 {@link ConditionDefinitionQueryService}；本服务面向
 * 后台目录维护，不承载门诊快照 JSON。</p>
 */
@Service
@RequiredArgsConstructor
public class ConditionDefinitionDomainService {

    /** 国临导入每批落库条数（独立短事务，避免长事务超时）。 */
    private static final int CLINICAL_V2_IMPORT_BATCH_SIZE = 1000;

    /** 目录实体持久化与编码存在性派生查询。 */
    private final ConditionDefinitionRepository repository;

    /** 目录创建/编辑/启停的领域事件发布器。 */
    private final IDomainEventPublisher domainEventPublisher;

    /** 未传编码时生成目录业务编码（前缀对齐 {@link AggregateTypeConstants#CONDITION_DEFINITION}）。 */
    private final BusinessSequenceService businessSequenceService;

    /** 国临大批量导入分批写库。 */
    private final ClinicalV2CatalogImportWriter clinicalV2CatalogImportWriter;

    /** 医保诊断目录分批写库。 */
    private final YbConditionCatalogImportWriter ybConditionCatalogImportWriter;

    /**
     * 新增诊断目录（普通路径：含拼音五笔、类型与描述等完整字段）。
     *
     * @param cmd 新增命令
     * @return 新增实体主键
     * @throws BusinessException 核心字段断言失败、编码冲突或序列异常
     */
    public Long addDisease(AddDiseaseCommand cmd) {
        String conditionCode = resolveConditionCodeForCreate(cmd.getSourceEnum(), cmd.getConditionCode());
        ConditionDefinition entity = new ConditionDefinition();
        entity.setSourceEnum(cmd.getSourceEnum());
        entity.setConditionCode(conditionCode);
        entity.setName(cmd.getName());
        entity.setPyStr(cmd.getPyStr());
        entity.setWbStr(cmd.getWbStr());
        entity.setTypeCode(cmd.getTypeCode());
        entity.setDescription(cmd.getDescription());
        applyCrossCodeFields(entity, cmd.getSourceEnum(), conditionCode, cmd.getYbNo(), cmd.getYbFlag(),
            cmd.getYbMatchFlag(), cmd.getClinicCode());
        entity.setStatusEnum(cmd.getStatusEnum());
        entity.assertCoreFields();
        entity.rebuildSearchKey();
        ConditionDefinition saved = repository.save(entity);
        syncPeerCatalog(saved);
        domainEventPublisher.publish(new ConditionDefinitionCreatedEvent(saved.getId(), saved.getConditionCode(), saved.getName(), saved.getStatusEnum()));
        return saved.getId();
    }

    /**
     * 幂等确保门诊慢特病种目录（{@code chronic_spec}）存在且启用。
     *
     * <p>仅读写 {@code source_enum=chronic_spec} 且 {@code condition_code=opspDiseCode} 的一行：
     * 已存在则必要时启用；不存在则新增。不修改其它来源目录，也不改同名 ICD 行。</p>
     *
     * @param opspDiseCode 门慢门特病种目录代码（如 M01912）
     * @param opspDiseName 门慢门特病种名称
     * @return 目录主键及本次是否写库（新建或启用）
     * @throws BusinessException 编码/名称为空，或并发创建冲突且复读失败
     */
    public EnsureChronicSpecOpspResult ensureActiveChronicSpecOpspDisease(String opspDiseCode, String opspDiseName) {
        String code = trimToNull(opspDiseCode);
        String name = trimToNull(opspDiseName);
        // Gate：缺码或缺名无法落慢病目录主数据。
        if (code == null || name == null) {
            throw new BusinessException(
                ErrorCodeConstants.CONDITION_DEFINITION_INVALID_ARGUMENT, "慢特病种编码与名称不能为空");
        }
        String source = DoctorStationDiagnosisConstants.SOURCE_CHRONIC_SPEC;
        // Pre：只查 chronic_spec 同码行，避免误改 general_dx / clinical_v2。
        Optional<ConditionDefinition> existing = repository.findBySourceEnumAndConditionCode(source, code);
        if (existing.isPresent()) {
            ConditionDefinition entity = existing.get();
            // Cmd：停用行仅启用本行，不改名称/编码/其它来源。
            if (!PublicationStatus.ACTIVE.getCode().equals(entity.getStatusEnum())) {
                entity.enable();
                ConditionDefinition saved = repository.save(entity);
                domainEventPublisher.publish(
                    new ConditionDefinitionEnabledEvent(saved.getId(), saved.getStatusEnum()));
                return new EnsureChronicSpecOpspResult(saved.getId(), true);
            }
            // Asm：已启用则复用，标记未写库。
            return new EnsureChronicSpecOpspResult(entity.getId(), false);
        }
        // Cmd：无同码行则新增一条启用态慢病目录（yb_no=病种码，不写 clinic_code）。
        AddDiseaseCommand cmd = new AddDiseaseCommand();
        cmd.setSourceEnum(source);
        cmd.setConditionCode(code);
        cmd.setName(name);
        cmd.setYbNo(code);
        cmd.setYbFlag(Boolean.TRUE);
        cmd.setYbMatchFlag(Boolean.TRUE);
        cmd.setStatusEnum(PublicationStatus.ACTIVE.getCode());
        try {
            Long id = this.addDisease(cmd);
            return new EnsureChronicSpecOpspResult(id, true);
        } catch (BusinessException ex) {
            // 并发双击：他事务已插入同码时复读返回，避免前端失败。
            if (!ErrorCodeConstants.CONDITION_DEFINITION_CODE_CONFLICT.equals(ex.getErrorCode())) {
                throw ex;
            }
            Optional<ConditionDefinition> raced = repository.findBySourceEnumAndConditionCode(source, code);
            if (raced.isEmpty()) {
                throw ex;
            }
            return new EnsureChronicSpecOpspResult(raced.get().getId(), false);
        }
    }

    /**
     * 新增诊断目录（医保路径：字段子集，仍以核心字段断言与搜索键重建收口）。
     *
     * @param cmd 新增命令
     * @return 新增实体主键
     * @throws BusinessException 核心字段断言失败、编码冲突或序列异常
     */
    public Long addYbDisease(AddYbDiseaseCommand cmd) {
        String conditionCode = resolveConditionCodeForCreate(cmd.getSourceEnum(), cmd.getConditionCode());
        ConditionDefinition entity = new ConditionDefinition();
        entity.setSourceEnum(cmd.getSourceEnum());
        entity.setConditionCode(conditionCode);
        entity.setName(cmd.getName());
        applyCrossCodeFields(entity, cmd.getSourceEnum(), conditionCode, cmd.getYbNo(), cmd.getYbFlag(),
            cmd.getYbMatchFlag(), cmd.getClinicCode());
        entity.setStatusEnum(cmd.getStatusEnum());
        entity.assertCoreFields();
        entity.rebuildSearchKey();
        ConditionDefinition saved = repository.save(entity);
        syncPeerCatalog(saved);
        domainEventPublisher.publish(new ConditionDefinitionCreatedEvent(saved.getId(), saved.getConditionCode(), saved.getName(), saved.getStatusEnum()));
        return saved.getId();
    }

    /**
     * 编辑单条诊断目录（覆盖写命令字段；状态仅当入参非空白时变更）。
     *
     * @param cmd 编辑命令
     * @throws BusinessException 记录不存在、编码冲突或核心字段断言失败
     */
    public void editDisease(EditDiseaseCommand cmd) {
        ConditionDefinition entity = this.loadById(cmd.getId());
        String resolvedCode = resolveConditionCodeForUpdate(
            cmd.getSourceEnum(), cmd.getConditionCode(), cmd.getId(), entity.getConditionCode());
        entity.setSourceEnum(cmd.getSourceEnum());
        entity.setConditionCode(resolvedCode);
        entity.setName(cmd.getName());
        entity.setPyStr(cmd.getPyStr());
        entity.setWbStr(cmd.getWbStr());
        entity.setTypeCode(cmd.getTypeCode());
        entity.setDescription(cmd.getDescription());
        applyCrossCodeFields(entity, cmd.getSourceEnum(), resolvedCode, cmd.getYbNo(), cmd.getYbFlag(),
            cmd.getYbMatchFlag(), cmd.getClinicCode());
        if (StringUtils.isNotEmpty(cmd.getStatusEnum())) {
            entity.setStatusEnum(cmd.getStatusEnum());
        }
        entity.assertCoreFields();
        entity.rebuildSearchKey();
        ConditionDefinition saved = repository.save(entity);
        syncPeerCatalog(saved);
        domainEventPublisher.publish(new ConditionDefinitionEditedEvent(saved.getId(), List.of("name", "conditionCode", "clinicCode", "pyStr", "wbStr", "statusEnum")));
    }

    /**
     * 按来源规范化双码字段：
     * <ul>
     *   <li>{@code clinical_v2}：国临主码；{@code clinic_code=condition_code}；医保码由选择写入</li>
     *   <li>{@code hospital_dx} / {@code peis_dx}：院内/体检主码；不把编码默认当成医保码；国临/医保对照仅显式写入</li>
     *   <li>其它诊断目录：医保主码优先 {@code yb_no}（空则用编码）；国临码由选择写入</li>
     * </ul>
     */
    private void applyCrossCodeFields(
            ConditionDefinition entity,
            String sourceEnum,
            String conditionCode,
            String ybNo,
            Boolean ybFlag,
            Boolean ybMatchFlag,
            String clinicCode) {
        String source = trimToNull(sourceEnum);
        if (DoctorStationDiagnosisConstants.SOURCE_CLINICAL_V2.equals(source)) {
            entity.setClinicCode(conditionCode);
            String yb = trimToNull(ybNo);
            entity.setYbNo(yb);
            entity.setYbFlag(yb != null ? Boolean.TRUE : Boolean.FALSE);
            entity.setYbMatchFlag(yb != null ? Boolean.TRUE : Boolean.FALSE);
            return;
        }
        if (DoctorStationDiagnosisConstants.SOURCE_HOSPITAL_DX.equals(source)
            || DoctorStationDiagnosisConstants.SOURCE_PEIS_DX.equals(source)) {
            String yb = trimToNull(ybNo);
            String clinic = trimToNull(clinicCode);
            entity.setYbNo(yb);
            entity.setClinicCode(clinic);
            entity.setYbFlag(yb != null ? Boolean.TRUE : Boolean.FALSE);
            entity.setYbMatchFlag(clinic != null && yb != null ? Boolean.TRUE : Boolean.FALSE);
            return;
        }
        String yb = trimToNull(ybNo);
        if (yb == null) {
            yb = conditionCode;
        }
        entity.setYbNo(yb);
        entity.setYbFlag(yb != null ? Boolean.TRUE : ybFlag);
        entity.setYbMatchFlag(yb != null ? Boolean.TRUE : ybMatchFlag);
        entity.setClinicCode(trimToNull(clinicCode));
    }

    /**
     * 保存后双向同步：诊断目录.clinic_code ↔ clinical_v2.yb_no。
     * <p>院内诊断 / 体检诊断不参与医保对码双向回写，避免把非医保主码误写入国临/医保侧。</p>
     */
    private void syncPeerCatalog(ConditionDefinition saved) {
        if (saved == null || Boolean.TRUE.equals(saved.getIsDeleted())) {
            return;
        }
        String source = trimToNull(saved.getSourceEnum());
        if (DoctorStationDiagnosisConstants.SOURCE_HOSPITAL_DX.equals(source)
            || DoctorStationDiagnosisConstants.SOURCE_PEIS_DX.equals(source)) {
            return;
        }
        if (DoctorStationDiagnosisConstants.SOURCE_CLINICAL_V2.equals(source)) {
            syncFromClinicalV2(saved);
            return;
        }
        syncFromDiagnosisCatalog(saved);
    }

    private void syncFromDiagnosisCatalog(ConditionDefinition dx) {
        String clinic = trimToNull(dx.getClinicCode());
        String yb = trimToNull(dx.getYbNo());
        if (yb == null) {
            yb = trimToNull(dx.getConditionCode());
        }
        if (clinic == null) {
            return;
        }
        String finalYb = yb;
        repository.findBySourceEnumAndConditionCode(
                DoctorStationDiagnosisConstants.SOURCE_CLINICAL_V2, clinic)
            .ifPresent(clinical -> {
                clinical.setClinicCode(clinical.getConditionCode());
                clinical.setYbNo(finalYb);
                clinical.setYbFlag(finalYb != null);
                clinical.setYbMatchFlag(finalYb != null);
                repository.save(clinical);
            });
    }

    private void syncFromClinicalV2(ConditionDefinition clinical) {
        String clinic = trimToNull(clinical.getConditionCode());
        String yb = trimToNull(clinical.getYbNo());
        // 优先回写疾病与诊断（general_dx）；找不到再按 yb_no 扫其它非国临来源
        if (yb != null) {
            repository.findBySourceEnumAndConditionCode(
                    DoctorStationDiagnosisConstants.SOURCE_GENERAL_DX, yb)
                .ifPresentOrElse(
                    dx -> {
                        dx.setClinicCode(clinic);
                        if (trimToNull(dx.getYbNo()) == null) {
                            dx.setYbNo(yb);
                        }
                        dx.setYbFlag(Boolean.TRUE);
                        dx.setYbMatchFlag(Boolean.TRUE);
                        repository.save(dx);
                    },
                    () -> {
                        List<ConditionDefinition> byYb = repository.findBySourceEnumAndYbNo(
                            DoctorStationDiagnosisConstants.SOURCE_GENERAL_DX, yb);
                        if (!byYb.isEmpty()) {
                            ConditionDefinition dx = byYb.get(0);
                            dx.setClinicCode(clinic);
                            dx.setYbFlag(Boolean.TRUE);
                            dx.setYbMatchFlag(Boolean.TRUE);
                            repository.save(dx);
                        }
                    });
        }
        // 清掉 general_dx 上仍挂本国会临但医保码已变更的旧挂接
        for (ConditionDefinition other : repository.findBySourceEnumAndClinicCode(
            DoctorStationDiagnosisConstants.SOURCE_GENERAL_DX, clinic)) {
            String otherYb = trimToNull(other.getYbNo());
            if (otherYb == null) {
                otherYb = trimToNull(other.getConditionCode());
            }
            if (yb == null || !Objects.equals(otherYb, yb)) {
                other.setClinicCode(null);
                repository.save(other);
            }
        }
    }

    /**
     * 批量导入国家临床版 2.0：按 {@code (clinical_v2, conditionCode)} upsert，
     * {@code clinic_code} 与编码相同，状态 {@code ACTIVE}。
     *
     * <p>性能：按批独立短事务；不逐条发领域事件；导入不重算拼音/五笔（检索仍可用编码/名称）。</p>
     *
     * @param rows             导入行（编码、名称）；空集合时返回 0
     * @param progressCallback 已处理条数回调，可为 null
     * @return 写入（新增+更新）条数
     */
    public int importClinicalV2Catalog(List<ClinicalV2ImportRowCommand> rows, IntConsumer progressCallback) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        // Pre：去重（同文件重复码取最后一次）
        Map<String, ClinicalV2ImportRowCommand> byCode = new HashMap<>();
        for (ClinicalV2ImportRowCommand row : rows) {
            if (row == null) {
                continue;
            }
            String code = trimToNull(row.getConditionCode());
            String name = trimToNull(row.getName());
            if (code == null || name == null) {
                continue;
            }
            ClinicalV2ImportRowCommand normalized = new ClinicalV2ImportRowCommand();
            normalized.setConditionCode(code);
            normalized.setName(name);
            byCode.put(code, normalized);
        }
        if (byCode.isEmpty()) {
            return 0;
        }
        List<String> allCodes = new ArrayList<>(byCode.keySet());
        int written = 0;
        // Loop：分批短事务写入，避免单事务锁表/超时
        for (int from = 0; from < allCodes.size(); from += CLINICAL_V2_IMPORT_BATCH_SIZE) {
            int to = Math.min(from + CLINICAL_V2_IMPORT_BATCH_SIZE, allCodes.size());
            List<String> batchCodes = allCodes.subList(from, to);
            written += clinicalV2CatalogImportWriter.writeBatch(batchCodes, byCode);
            if (progressCallback != null) {
                progressCallback.accept(written);
            }
        }
        return written;
    }

    /**
     * 兼容旧调用：无进度回调。
     *
     * @param rows 导入行
     * @return 写入条数
     */
    public int importClinicalV2Catalog(List<ClinicalV2ImportRowCommand> rows) {
        return this.importClinicalV2Catalog(rows, null);
    }

    /**
     * 批量导入医保诊断目录：按 {@code (sourceEnum, conditionCode)} upsert，
     * {@code yb_no} 与编码相同，状态 {@code ACTIVE}。
     *
     * <p>性能：按批独立短事务；不逐条发领域事件；导入不重算拼音/五笔。</p>
     *
     * @param sourceEnum       目录来源（如 general_dx）
     * @param rows             导入行
     * @param progressCallback 已处理条数回调，可为 null
     * @return 写入（新增+更新）条数
     */
    public int importYbConditionCatalog(
            String sourceEnum,
            List<YbConditionImportRowCommand> rows,
            IntConsumer progressCallback) {
        String source = trimToNull(sourceEnum);
        if (source == null || rows == null || rows.isEmpty()) {
            return 0;
        }
        Map<String, YbConditionImportRowCommand> byCode = new HashMap<>();
        for (YbConditionImportRowCommand row : rows) {
            if (row == null) {
                continue;
            }
            String code = trimToNull(row.getConditionCode());
            String name = trimToNull(row.getName());
            if (code == null || name == null) {
                continue;
            }
            YbConditionImportRowCommand normalized = new YbConditionImportRowCommand();
            normalized.setConditionCode(code);
            normalized.setName(name);
            normalized.setPyStr(trimToNull(row.getPyStr()));
            byCode.put(code, normalized);
        }
        if (byCode.isEmpty()) {
            return 0;
        }
        List<String> allCodes = new ArrayList<>(byCode.keySet());
        int written = 0;
        for (int from = 0; from < allCodes.size(); from += CLINICAL_V2_IMPORT_BATCH_SIZE) {
            int to = Math.min(from + CLINICAL_V2_IMPORT_BATCH_SIZE, allCodes.size());
            written += ybConditionCatalogImportWriter.writeBatch(source, allCodes.subList(from, to), byCode);
            if (progressCallback != null) {
                progressCallback.accept(written);
            }
        }
        return written;
    }

    /**
     * 兼容旧调用：无进度回调。
     *
     * @param sourceEnum 目录来源
     * @param rows       导入行
     * @return 写入条数
     */
    public int importYbConditionCatalog(String sourceEnum, List<YbConditionImportRowCommand> rows) {
        return importYbConditionCatalog(sourceEnum, rows, null);
    }

    /**
     * 批量停用诊断目录。
     *
     * <p>一致性：要求 {@code findByIdIn} 命中条数与入参 id 条数一致，防止部分 id 无效时静默跳过（入参含重复 id 时
     * 仓储去重可能导致命中数小于入参 size——调用方应先去重）。</p>
     *
     * @param cmd 停用命令，含待停用主键集合
     * @throws BusinessException 存在无效主键
     */
    public void editDiseaseStop(EditDiseaseStopCommand cmd) {
        List<ConditionDefinition> entities = repository.findByIdIn(cmd.getIds());
        if (entities.size() != cmd.getIds().size()) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_DEFINITION_NOT_FOUND, "存在无效目录主键");
        }
        // Loop：遍历实体仅修改内存状态，不触发单条 save（A.9.1 批量 I/O 约束）。
        for (ConditionDefinition entity : entities) {
            entity.disable();
        }
        repository.saveAll(entities);
        // Event：落库完成后逐条发布停用事件（B.14 事件时序约束）。
        for (ConditionDefinition entity : entities) {
            domainEventPublisher.publish(new ConditionDefinitionDisabledEvent(entity.getId(), entity.getStatusEnum()));
        }
    }

    /**
     * 软删除诊断目录（列表不再展示）。
     *
     * @param id 主键
     */
    public void delete(Long id) {
        ConditionDefinition entity = repository.findById(id)
            .filter(row -> !Boolean.TRUE.equals(row.getIsDeleted()))
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.CONDITION_DEFINITION_NOT_FOUND, "诊断目录不存在或已删除"));
        repository.delete(entity);
    }

    /**
     * 批量启用诊断目录。
     *
     * <p>一致性说明同 {@link #editDiseaseStop(EditDiseaseStopCommand)}。</p>
     *
     * @param cmd 启用命令
     * @throws BusinessException 存在无效主键
     */
    public void editDiseaseStart(EditDiseaseStartCommand cmd) {
        List<ConditionDefinition> entities = repository.findByIdIn(cmd.getIds());
        if (entities.size() != cmd.getIds().size()) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_DEFINITION_NOT_FOUND, "存在无效目录主键");
        }
        for (ConditionDefinition entity : entities) {
            entity.enable();
        }
        repository.saveAll(entities);
        for (ConditionDefinition entity : entities) {
            domainEventPublisher.publish(new ConditionDefinitionEnabledEvent(entity.getId(), entity.getStatusEnum()));
        }
    }

    /**
     * 按主键加载目录实体，不存在则阻断。
     *
     * @param id 主键
     * @return 非 null 实体
     * @throws BusinessException 主键不存在
     */
    private ConditionDefinition loadById(Long id) {
        return repository.findById(id).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.CONDITION_DEFINITION_NOT_FOUND, "诊断定义不存在"));
    }

    /**
     * 断言 {@code (sourceEnum, conditionCode)} 在库内唯一。
     *
     * @param sourceEnum    目录来源
     * @param conditionCode 规范化后的编码
     * @param id            当前编辑实体主键；{@code null} 表示新增
     * @throws BusinessException 编码已被占用
     */
    private void assertCodeUnique(String sourceEnum, String conditionCode, Long id) {
        if (id == null && repository.existsBySourceEnumAndConditionCode(sourceEnum, conditionCode)) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_DEFINITION_CODE_CONFLICT, "诊断编码冲突");
        }
        if (id != null && repository.existsBySourceEnumAndConditionCodeAndIdNot(sourceEnum, conditionCode, id)) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_DEFINITION_CODE_CONFLICT, "诊断编码冲突");
        }
    }

    /**
     * 解析新增场景下的诊断编码：入参非空白则 trim 后做唯一性校验；否则委托序列服务生成。
     *
     * @param sourceEnum    目录来源
     * @param requestedCode 请求携带编码，可 null 或空白
     * @return 落库用编码
     * @throws BusinessException 与存量编码冲突
     */
    private String resolveConditionCodeForCreate(String sourceEnum, String requestedCode) {
        String trimmed = trimToNull(requestedCode);
        if (trimmed != null) {
            assertCodeUnique(sourceEnum, trimmed, null);
            return trimmed;
        }
        // 序列生成后仍按来源做唯一性校验，避免碰撞
        String generated = businessSequenceService.getSeq(
            AggregateTypeConstants.CONDITION_DEFINITION, BusinessSequenceConstants.MASTER_DATA_3_PREFIX_SEQ_LENGTH);
        assertCodeUnique(sourceEnum, generated, null);
        return generated;
    }

    /**
     * 解析更新场景下的诊断编码：trim 后非空则校验唯一并替换；否则保留 {@code currentCode}。
     *
     * @param sourceEnum    目录来源
     * @param requestedCode 请求携带编码
     * @param id            当前实体主键
     * @param currentCode   库中现有编码
     * @return 落库用编码
     * @throws BusinessException 新编码与存量冲突
     */
    private String resolveConditionCodeForUpdate(String sourceEnum, String requestedCode, Long id, String currentCode) {
        String trimmed = trimToNull(requestedCode);
        if (trimmed == null) {
            return currentCode;
        }
        assertCodeUnique(sourceEnum, trimmed, id);
        return trimmed;
    }
}
