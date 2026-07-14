/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.terminologycodemapping.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.domain.share.constants.CustomConstants;
import com.openhis.health.domain.custom.terminologycodemapping.command.*;
import com.openhis.health.domain.custom.terminologycodemapping.entity.TerminologyCodeMapping;
import com.openhis.health.domain.custom.terminologycodemapping.repository.TerminologyCodeMappingRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.events.custom.terminologycodemapping.*;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/**
 * 术语编码映射（{@code cus_terminology_code_mapping}）聚合写侧领域服务。
 *
 * <p>职责：源术语表+源编码与目标编码的对照维护；支持单条增改、启停、软删及批量导入。</p>
 * <p>不变量：激活态下源表+源编码+目标编码三元组唯一；持久化先于映射领域事件发布。</p>
 * <p>仅注入本聚合 {@link TerminologyCodeMappingRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class TerminologyCodeMappingDomainService {

    private final TerminologyCodeMappingRepository repository;
    private final IDomainEventPublisher domainEventPublisher;

    /**
     * 新建术语映射：校验激活态身份键不冲突，初始化启用后落库并发布创建事件。
     *
     * @param command 源/目标项与操作人
     * @return 新记录主键
     * @throws BusinessException 身份键已存在或必填字段不满足时
     */
    public Long createMapping(CreateMappingCommand command) {
        // 前置校验：激活态身份键不冲突
        this.assertActiveIdentityNotExists(command.getSourceItemTable(), command.getSourceItemNo(), command.getTargetItemNo());
        // 加载/组装：装配映射实体并初始化启用
        TerminologyCodeMapping mapping = new TerminologyCodeMapping();
        mapping.setSourceItemTable(command.getSourceItemTable());
        mapping.setSourceItemNo(command.getSourceItemNo());
        mapping.setTargetItemNo(command.getTargetItemNo());
        mapping.setTargetName(command.getTargetName());
        mapping.initActive();
        mapping.assertRequiredFields();
        // 持久化
        TerminologyCodeMapping saved = repository.save(mapping);
        // 发布事件：映射已创建
        domainEventPublisher.publish(new TerminologyMappingCreatedEvent(
            saved.getId(),
            saved.getSourceItemTable(),
            saved.getSourceItemNo(),
            saved.getTargetItemNo()
        , command.getOperatorId()));
        return saved.getId();
    }

    /**
     * 修改映射展示名称（目标名称），发布修改事件。
     *
     * @param command 须含主键与新名称
     * @throws BusinessException 记录不存在时
     */
    public void modifyMapping(ModifyMappingCommand command) {
        // 加载/组装：加载目标映射
        TerminologyCodeMapping mapping = this.requireMapping(command.getId());
        mapping.setTargetName(command.getTargetName());
        // 持久化
        repository.save(mapping);
        // 发布事件：映射名称已修改
        domainEventPublisher.publish(new TerminologyMappingModifiedEvent(
            mapping.getId(),
            CustomConstants.ChangedField.DEFAULT_CHANGED_FIELDS
        , command.getOperatorId()));
    }

    /**
     * 停用映射：聚合根校验可停用后置 activeFlag=false，并发布停用事件。
     *
     * @param command 须含主键、原因与操作人
     * @throws BusinessException 记录不存在或状态不允许停用时
     */
    public void deactivateMapping(DeactivateMappingCommand command) {
        TerminologyCodeMapping mapping = this.requireMapping(command.getId());
        mapping.assertDeactivatable();
        mapping.setActiveFlag(Boolean.FALSE);
        repository.save(mapping);
        domainEventPublisher.publish(new TerminologyMappingDeactivatedEvent(mapping.getId(), command.getReason(), command.getOperatorId()));
    }

    /**
     * 重新启用映射：校验可启用且激活态身份键不冲突后置 activeFlag=true，并发布启用事件。
     *
     * @param command 须含主键与操作人
     * @throws BusinessException 记录不存在、状态不允许或与现存激活行冲突时
     */
    public void reactivateMapping(ReactivateMappingCommand command) {
        TerminologyCodeMapping mapping = this.requireMapping(command.getId());
        mapping.assertReactivatable();
        this.assertActiveIdentityNotExists(mapping.getSourceItemTable(), mapping.getSourceItemNo(), mapping.getTargetItemNo());
        mapping.setActiveFlag(Boolean.TRUE);
        repository.save(mapping);
        domainEventPublisher.publish(new TerminologyMappingReactivatedEvent(mapping.getId(), command.getOperatorId()));
    }

    /**
     * 软删映射并发布删除事件。
     *
     * @param command 须含主键与操作人
     * @throws BusinessException 记录不存在时
     */
    public void deleteMapping(DeleteMappingCommand command) {
        TerminologyCodeMapping mapping = this.requireMapping(command.getId());
        mapping.markDeleted(command.getOperatorId());
        repository.save(mapping);
        domainEventPublisher.publish(new TerminologyMappingDeletedEvent(
            mapping.getId(),
            mapping.getSourceItemTable(),
            mapping.getSourceItemNo()
        , command.getOperatorId()));
    }

    /**
     * 批量导入：按源术语聚合，已存在且目标编码相同则按需更新名称；否则新建。统计创建/更新/失败条数后发布批量导入事件。
     *
     * @param command 批量行与操作人（非法行计入 failed 不中断整批）
     * @throws BusinessException 单条身份键冲突时（非 import 路径）
     */
    public void importMappingBatch(ImportMappingBatchCommand command) {
        // 加载/组装：逐行解析导入项并 upsert
        List<ImportMappingBatchCommand.ImportMappingItemCommand> items = command.resolveItems();
        int created = 0;
        int updated = 0;
        int failed = 0;
        List<TerminologyCodeMapping> toSave = new ArrayList<>();
        Map<SourceKey, Map<String, TerminologyCodeMapping>> existedByTargetNo = new HashMap<>();
        Set<String> dedupIdentityKeys = new HashSet<>();

        for (ImportMappingBatchCommand.ImportMappingItemCommand item : items) {
            // 空行计入失败数并跳过，不中断整批导入。
            if (item == null) {
                failed++;
                continue;
            }
            // 源表/源编码/目标编码任一缺失则该行无效。
            if (StringUtils.isEmpty(item.getSourceItemTable())
                || StringUtils.isEmpty(item.getSourceItemNo())
                || StringUtils.isEmpty(item.getTargetItemNo())) {
                failed++;
                continue;
            }

            String identityKey = item.getSourceItemTable().trim() + "\u0001" + item.getSourceItemNo().trim() + "\u0001" + item.getTargetItemNo().trim();
            // 同一身份键在入参内重复时跳过后续写库。
            if (dedupIdentityKeys.contains(identityKey)) {
                // 入参去重：避免同一身份键重复写库或重复计数。
                continue;
            }
            dedupIdentityKeys.add(identityKey);

            SourceKey sourceKey = new SourceKey(item.getSourceItemTable().trim(), item.getSourceItemNo().trim());
            Map<String, TerminologyCodeMapping> targetNoMap = existedByTargetNo.get(sourceKey);
            // 首次遇到该源术语键时批量预取存量映射，避免 N+1。
            if (targetNoMap == null) {
                List<TerminologyCodeMapping> existed = repository.findAllBySourceItemTableAndSourceItemNoAndActiveFlagTrue(
                    sourceKey.getSourceItemTable(),
                    sourceKey.getSourceItemNo()
                );
                targetNoMap = new HashMap<>();
                for (TerminologyCodeMapping row : existed) {
                    // 仅索引有目标编码的存量行。
                    if (row.getTargetItemNo() != null) {
                        targetNoMap.put(row.getTargetItemNo(), row);
                    }
                }
                existedByTargetNo.put(sourceKey, targetNoMap);
            }

            TerminologyCodeMapping existed = targetNoMap.get(item.getTargetItemNo().trim());
            // 目标编码已存在时走更新路径，仅名称变化才落库。
            if (existed != null) {
                // 更新：仅当名称变化时才计数与落库。
                if (!Objects.equals(trimToNull(item.getTargetName()), trimToNull(existed.getTargetName()))) {
                    existed.setTargetName(item.getTargetName());
                    toSave.add(existed);
                    updated++;
                }
                continue;
            }

            TerminologyCodeMapping mapping = new TerminologyCodeMapping();
            mapping.setSourceItemTable(sourceKey.getSourceItemTable());
            mapping.setSourceItemNo(sourceKey.getSourceItemNo());
            mapping.setTargetItemNo(item.getTargetItemNo().trim());
            mapping.setTargetName(item.getTargetName());
            mapping.initActive();
            mapping.assertRequiredFields();
            toSave.add(mapping);
            created++;
        }

        List<TerminologyCodeMapping> saved = repository.saveAll(toSave);
        Long aggregateId = 0L;
        // 有落库行时取首条 ID 作为批量事件代表锚点。
        if (!saved.isEmpty()) {
            aggregateId = saved.get(0).getId();
        }
        String sourceItemTable = this.resolveSingleSourceTable(items);
        // 发布事件：批量导入汇总
        domainEventPublisher.publish(new TerminologyMappingBatchImportedEvent(
            aggregateId,
            sourceItemTable,
            created,
            updated,
            failed
        , command.getOperatorId()));
    }

    /**
     * 解析批量导入的 sourceItemTable（若多表混合则返回 null）。
     *
     * @param items 批量条目
     * @return 单一 sourceItemTable 或 null
     */
    private String resolveSingleSourceTable(List<ImportMappingBatchCommand.ImportMappingItemCommand> items) {
        String result = null;
        for (ImportMappingBatchCommand.ImportMappingItemCommand item : items) {
            // 空行或缺源表时跳过，不参与单表判定。
            if (item == null || StringUtils.isEmpty(item.getSourceItemTable())) {
                continue;
            }
            String current = item.getSourceItemTable().trim();
            // 首条有效源表作为候选。
            if (result == null) {
                result = current;
                continue;
            }
            // 出现不同源表则返回 null，表示批量混合导入。
            if (!result.equals(current)) {
                return null;
            }
        }
        return result;
    }

    /** 按主键加载映射，不存在则抛出业务异常。
     *
     * @param id 映射主键
     * @return 术语映射实体
     */
    private TerminologyCodeMapping requireMapping(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.TERMINOLOGY_MAPPING_NOT_FOUND, "术语映射不存在"));
    }

    /** 校验同一源表+源编码+目标编码在激活态下尚不存在。 */
    private void assertActiveIdentityNotExists(String sourceItemTable, String sourceItemNo, String targetItemNo) {
        // 激活态下同源+目标编码映射已存在则拒绝新建。
        if (repository.existsBySourceItemTableAndSourceItemNoAndTargetItemNoAndActiveFlagTrue(
            sourceItemTable,
            sourceItemNo,
            targetItemNo
        )) {
            throw new BusinessException(ErrorCodeConstants.TERMINOLOGY_MAPPING_IDENTITY_CONFLICT, "术语映射身份键冲突");
        }
    }

    /**
     * 批量导入的源术语键（sourceItemTable + sourceItemNo）。
     */
    private static class SourceKey {
        private final String sourceItemTable;
        private final String sourceItemNo;

        private SourceKey(String sourceItemTable, String sourceItemNo) {
            this.sourceItemTable = sourceItemTable;
            this.sourceItemNo = sourceItemNo;
        }

        /** SourceKey 复合键：源术语项表名。 */
        private String getSourceItemTable() {
            return sourceItemTable;
        }

        /** SourceKey 复合键：源术语项业务编码。 */
        private String getSourceItemNo() {
            return sourceItemNo;
        }

        @Override
        public boolean equals(Object o) {
            // 同一引用直接判定相等。
            if (this == o) {
                return true;
            }
            // 类型不匹配则源术语键不等。
            if (o == null || this.getClass() != o.getClass()) {
                return false;
            }
            SourceKey that = (SourceKey) o;
            return Objects.equals(sourceItemTable, that.sourceItemTable) && Objects.equals(sourceItemNo, that.sourceItemNo);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sourceItemTable, sourceItemNo);
        }
    }
}
