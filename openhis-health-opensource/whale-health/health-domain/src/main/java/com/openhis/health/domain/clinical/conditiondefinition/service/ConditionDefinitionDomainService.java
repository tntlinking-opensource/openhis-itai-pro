/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.clinical.conditiondefinition.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.clinical.conditiondefinition.command.*;
import com.openhis.health.domain.clinical.conditiondefinition.entity.ConditionDefinition;
import com.openhis.health.domain.clinical.conditiondefinition.repository.ConditionDefinitionRepository;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.events.clinical.conditiondefinition.*;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * 诊断定义（ConditionDefinition）目录写侧领域服务。
 *
 * <p>职责：主数据新增/编辑、批量启停；编码唯一性在落库前由 {@link #assertCodeUnique} 保证；
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

    /** 目录实体持久化与编码存在性派生查询。 */
    private final ConditionDefinitionRepository repository;

    /** 目录创建/编辑/启停的领域事件发布器。 */
    private final IDomainEventPublisher domainEventPublisher;

    /** 未传编码时生成目录业务编码（前缀对齐 {@link AggregateTypeConstants#CONDITION_DEFINITION}）。 */
    private final BusinessSequenceService businessSequenceService;

    /**
     * 新增诊断目录（普通路径：含拼音五笔、类型与描述等完整字段）。
     *
     * @param cmd 新增命令
     * @return 新增实体主键
     * @throws BusinessException 核心字段断言失败、编码冲突或序列异常
     */
    public Long addDisease(AddDiseaseCommand cmd) {
        String conditionCode = resolveConditionCodeForCreate(cmd.getConditionCode());
        ConditionDefinition entity = new ConditionDefinition();
        entity.setSourceEnum(cmd.getSourceEnum());
        entity.setConditionCode(conditionCode);
        entity.setName(cmd.getName());
        entity.setPyStr(cmd.getPyStr());
        entity.setWbStr(cmd.getWbStr());
        entity.setTypeCode(cmd.getTypeCode());
        entity.setDescription(cmd.getDescription());
        entity.setYbFlag(cmd.getYbFlag());
        entity.setDiseaseCode(cmd.getDiseaseCode());
        entity.setYbNo(cmd.getYbNo());
        entity.setYbMatchFlag(cmd.getYbMatchFlag());
        entity.setStatusEnum(cmd.getStatusEnum());
        entity.assertCoreFields();
        entity.rebuildSearchKey();
        ConditionDefinition saved = repository.save(entity);
        domainEventPublisher.publish(new ConditionDefinitionCreatedEvent(saved.getId(), saved.getConditionCode(), saved.getName(), saved.getStatusEnum()));
        return saved.getId();
    }

    /**
     * 新增诊断目录（医保路径：字段子集，仍以核心字段断言与搜索键重建收口）。
     *
     * @param cmd 新增命令
     * @return 新增实体主键
     * @throws BusinessException 核心字段断言失败、编码冲突或序列异常
     */
    public Long addYbDisease(AddYbDiseaseCommand cmd) {
        String conditionCode = resolveConditionCodeForCreate(cmd.getConditionCode());
        ConditionDefinition entity = new ConditionDefinition();
        entity.setSourceEnum(cmd.getSourceEnum());
        entity.setConditionCode(conditionCode);
        entity.setName(cmd.getName());
        entity.setYbFlag(cmd.getYbFlag());
        entity.setYbNo(cmd.getYbNo());
        entity.setYbMatchFlag(cmd.getYbMatchFlag());
        entity.setDiseaseCode(cmd.getDiseaseCode());
        entity.setStatusEnum(cmd.getStatusEnum());
        entity.assertCoreFields();
        entity.rebuildSearchKey();
        ConditionDefinition saved = repository.save(entity);
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
        String resolvedCode = resolveConditionCodeForUpdate(cmd.getConditionCode(), cmd.getId(), entity.getConditionCode());
        entity.setSourceEnum(cmd.getSourceEnum());
        entity.setConditionCode(resolvedCode);
        entity.setName(cmd.getName());
        entity.setPyStr(cmd.getPyStr());
        entity.setWbStr(cmd.getWbStr());
        entity.setTypeCode(cmd.getTypeCode());
        entity.setDescription(cmd.getDescription());
        entity.setYbFlag(cmd.getYbFlag());
        entity.setDiseaseCode(cmd.getDiseaseCode());
        entity.setYbNo(cmd.getYbNo());
        entity.setYbMatchFlag(cmd.getYbMatchFlag());
        if (StringUtils.isNotBlank(cmd.getStatusEnum())) {
            entity.setStatusEnum(cmd.getStatusEnum());
        }
        entity.assertCoreFields();
        entity.rebuildSearchKey();
        repository.save(entity);
        domainEventPublisher.publish(new ConditionDefinitionEditedEvent(entity.getId(), List.of("name", "conditionCode", "pyStr", "wbStr", "statusEnum")));
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
     * 断言 {@code conditionCode} 在库内唯一：新增路径 {@code id==null} 查全库存在性；更新路径查「他行」占用。
     *
     * @param conditionCode 规范化后的编码
     * @param id            当前编辑实体主键；{@code null} 表示新增
     * @throws BusinessException 编码已被占用
     */
    private void assertCodeUnique(String conditionCode, Long id) {
        if (id == null && repository.existsByConditionCode(conditionCode)) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_DEFINITION_CODE_CONFLICT, "诊断编码冲突");
        }
        if (id != null && repository.existsByConditionCodeAndIdNot(conditionCode, id)) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_DEFINITION_CODE_CONFLICT, "诊断编码冲突");
        }
    }

    /**
     * 解析新增场景下的诊断编码：入参非空白则 trim 后做唯一性校验；否则委托序列服务生成（与 {@link AggregateTypeConstants#CONDITION_DEFINITION} 对齐）。
     *
     * @param requestedCode 请求携带编码，可 null 或空白
     * @return 落库用编码
     * @throws BusinessException 与存量编码冲突
     */
    private String resolveConditionCodeForCreate(String requestedCode) {
        String trimmed = trimToNull(requestedCode);
        if (trimmed != null) {
            assertCodeUnique(trimmed, null);
            return trimmed;
        }
        return businessSequenceService.getSeq(AggregateTypeConstants.CONDITION_DEFINITION, BusinessSequenceConstants.MASTER_DATA_3_PREFIX_SEQ_LENGTH);
    }

    /**
     * 解析更新场景下的诊断编码：trim 后非空则校验唯一并替换；否则保留 {@code currentCode}。
     *
     * @param requestedCode 请求携带编码
     * @param id            当前实体主键
     * @param currentCode   库中现有编码
     * @return 落库用编码
     * @throws BusinessException 新编码与存量冲突
     */
    private String resolveConditionCodeForUpdate(String requestedCode, Long id, String currentCode) {
        String trimmed = trimToNull(requestedCode);
        if (trimmed == null) {
            return currentCode;
        }
        assertCodeUnique(trimmed, id);
        return trimmed;
    }
}