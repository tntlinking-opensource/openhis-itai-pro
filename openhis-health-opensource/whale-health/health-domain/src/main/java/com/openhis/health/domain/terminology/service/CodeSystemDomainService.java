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

package com.openhis.health.domain.terminology.service;

import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.terminology.command.codesystem.CreateCodeSystemCommand;
import com.openhis.health.domain.terminology.command.codesystem.UpdateCodeSystemCommand;
import com.openhis.health.domain.terminology.command.concept.CreateConceptCommand;
import com.openhis.health.domain.terminology.command.concept.UpdateConceptCommand;
import com.openhis.health.domain.terminology.entity.CodeSystem;
import com.openhis.health.domain.terminology.entity.Concept;
import com.openhis.health.domain.terminology.repository.CodeSystemRepository;
import com.openhis.health.domain.terminology.repository.ConceptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 编码系统（CodeSystem）与概念（Concept）写侧领域服务：编码系统与概念的增删改及概念缓存写穿。
 * <p>
 * 与 {@link CodeSystemQueryService} 同包并列；读路径请注入 {@link CodeSystemQueryService}；类上不加 {@code @Transactional}，方法与旧实现一致在方法级标注。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class CodeSystemDomainService {

    private final CodeSystemRepository codeSystemRepository;
    private final ConceptRepository conceptRepository;
    private final CodeSystemQueryService codeSystemQueryService;

    /**
     * 新建编码系统：校验名称唯一后落库，并清空字典缓存避免读侧脏读。
     *
     * @param command 创建命令（名称、展示标题、版本、租户等）
     * @return 已持久化的编码系统
     * @throws IllegalArgumentException 当名称与存量冲突时
     */
    @Transactional
    public CodeSystem create(CreateCodeSystemCommand command) {
        // 名称在租户/全局口径下需唯一，重复则拒绝创建以免字典键冲突。
        if (codeSystemRepository.existsByName(command.getName())) {
            throw new IllegalArgumentException("编码系统名称已存在: " + command.getName());
        }
        String version = command.getVersion();
        if (version == null) {
            version = "1.0";
        }
        String status = command.getStatus();
        if (status == null) {
            status = PublicationStatus.ACTIVE.getCode();
        }
        CodeSystem codeSystem = CodeSystem.builder()
            .name(command.getName())
            .title(command.getTitle())
            .description(command.getDescription())
            .version(version)
            .status(status)
            .tenantId(command.getTenantId())
            .build();
        CodeSystem saved = codeSystemRepository.save(codeSystem);
        // 编码系统变更后清缓存：避免后续字典读取读到旧数据
        codeSystemQueryService.clearAllDictCache();
        return saved;
    }

    /**
     * 更新编码系统：按命令中非空字段增量覆盖，名称变更时重新校验唯一性；保存后清空字典缓存。
     *
     * @param command 更新命令（含主键与待覆盖字段）
     * @return 持久化后的实体
     * @throws IllegalArgumentException 当记录不存在或新名称冲突时
     */
    @Transactional
    public CodeSystem update(UpdateCodeSystemCommand command) {
        CodeSystem codeSystem = codeSystemRepository.findById(command.getId())
            .orElseThrow(() -> new IllegalArgumentException("编码系统不存在: " + command.getId()));
        // 仅当名称实际变化时才做唯一性校验，避免无意义的数据库探测。
        if (command.getName() != null && !command.getName().equals(codeSystem.getName())) {
            // 新名称不得与已有编码系统重名，否则下游引用会歧义。
            if (codeSystemRepository.existsByName(command.getName())) {
                throw new IllegalArgumentException("编码系统名称已存在: " + command.getName());
            }
            codeSystem.setName(command.getName());
        }
        // 以下字段均为可选补丁：调用方只传需要变更的列，未传则保持原值。
        if (command.getTitle() != null) {
            codeSystem.setTitle(command.getTitle());
        }
        if (command.getDescription() != null) {
            codeSystem.setDescription(command.getDescription());
        }
        if (command.getVersion() != null) {
            codeSystem.setVersion(command.getVersion());
        }
        if (command.getStatus() != null) {
            codeSystem.setStatus(command.getStatus());
        }
        CodeSystem saved = codeSystemRepository.save(codeSystem);
        // 编码系统变更（含可能的 name 变更）后清空字典缓存，保证一致性
        codeSystemQueryService.clearAllDictCache();
        return saved;
    }

    /**
     * 逻辑停用编码系统：将状态置为停用并保存，随后清空字典缓存。
     *
     * @param id 编码系统主键
     * @throws IllegalArgumentException 当记录不存在时
     */
    @Transactional
    public void delete(Long id) {
        CodeSystem codeSystem = codeSystemRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("编码系统不存在: " + id));
        // 业务上采用状态停用而非物理删除，避免历史就诊/单据引用断裂。
        codeSystem.setStatus(PublicationStatus.RETIRED.getCode());
        codeSystemRepository.save(codeSystem);
        codeSystemQueryService.clearAllDictCache();
    }

    /**
     * 在指定编码系统下新增概念：校验 code 唯一，可选挂载父节点；保存后按编码系统维度驱逐字典缓存。
     *
     * @param command 概念创建命令
     * @return 已持久化的概念
     * @throws IllegalArgumentException 当 code 重复、父不存在或父不属于同一编码系统时
     */
    @Transactional
    public Concept createConcept(CreateConceptCommand command) {
        // 同一 CodeSystem 下 code 必须唯一，否则术语解析无法唯一定位。
        if (conceptRepository.findByCodeSystemIdAndCode(command.getCodeSystemId(), command.getCode()).isPresent()) {
            throw new IllegalArgumentException(String.format("编码已存在: %s (编码系统: %s)", command.getCode(), command.getCodeSystemId()));
        }
        Concept concept = new Concept();
        concept.setCodeSystemId(command.getCodeSystemId());
        concept.setCode(command.getCode());
        concept.setDisplay(command.getDisplay());
        concept.setDefinition(command.getDefinition());
        concept.setSortOrder(command.getSortOrder());
        if (command.getIsActive() != null) {
            concept.setIsActive(command.getIsActive());
        } else {
            concept.setIsActive(true);
        }
        concept.setCssClass(command.getCssClass());
        concept.setListClass(command.getListClass());
        concept.setIsDefault(command.getIsDefault());

        // 可选：挂父节点形成树形术语；父必须存在且与当前编码系统一致。
        if (command.getParentId() != null) {
            Concept parent = conceptRepository.findById(command.getParentId())
                .orElseThrow(() -> new IllegalArgumentException("父概念不存在: " + command.getParentId()));
            // 禁止跨 CodeSystem 挂载，否则层级展示与缓存键会错乱。
            if (!parent.getCodeSystemId().equals(command.getCodeSystemId())) {
                throw new IllegalArgumentException("父概念必须属于同一个编码系统");
            }
            concept.setParent(parent);
        }
        Concept save = conceptRepository.save(concept);
        // 概念变更后按编码系统粒度失效字典缓存，避免下拉仍展示旧项。
        if (save != null) {
            codeSystemQueryService.evictDictByCodeSystemId(save.getCodeSystemId());
        }
        return save;
    }

    /**
     * 更新概念：支持 code、展示、层级等补丁式更新；code 变更时重查唯一性；保存后驱逐对应编码系统缓存。
     *
     * @param command 更新命令
     * @return 持久化后的概念
     * @throws IllegalArgumentException 当记录不存在、新 code 冲突、父非法或自引用时
     */
    @Transactional
    public Concept updateConcept(UpdateConceptCommand command) {
        Concept concept = conceptRepository.findById(command.getId())
            .orElseThrow(() -> new IllegalArgumentException("概念不存在: " + command.getId()));
        // code 变更需重新保证同系统内唯一。
        if (command.getCode() != null && !command.getCode().equals(concept.getCode())) {
            if (conceptRepository.findByCodeSystemIdAndCode(concept.getCodeSystemId(), command.getCode()).isPresent()) {
                throw new IllegalArgumentException("编码已存在: " + command.getCode());
            }
            concept.setCode(command.getCode());
        }
        if (command.getDisplay() != null) {
            concept.setDisplay(command.getDisplay());
        }
        if (command.getDefinition() != null) {
            concept.setDefinition(command.getDefinition());
        }
        if (command.getSortOrder() != null) {
            concept.setSortOrder(command.getSortOrder());
        }
        if (command.getIsActive() != null) {
            concept.setIsActive(command.getIsActive());
        }
        if (command.getCssClass() != null) {
            concept.setCssClass(command.getCssClass());
        }
        if (command.getListClass() != null) {
            concept.setListClass(command.getListClass());
        }
        if (command.getIsDefault() != null) {
            concept.setIsDefault(command.getIsDefault());
        }
        // 父节点变更：需校验同系统、禁止自环。
        if (command.getParentId() != null) {
            Concept parent = conceptRepository.findById(command.getParentId())
                .orElseThrow(() -> new IllegalArgumentException("父概念不存在: " + command.getParentId()));
            if (!parent.getCodeSystemId().equals(concept.getCodeSystemId())) {
                throw new IllegalArgumentException("父概念必须属于同一个编码系统");
            }
            if (command.getParentId().equals(command.getId())) {
                throw new IllegalArgumentException("概念不能将自己设为父概念");
            }
            concept.setParent(parent);
        }
        Concept saved = conceptRepository.save(concept);
        codeSystemQueryService.evictDictByCodeSystemId(saved.getCodeSystemId());
        return saved;
    }

    /**
     * 将指定概念设为该编码系统下的默认项：同系统其他概念的默认标记清零，再标记当前项并刷新缓存。
     *
     * @param id 概念主键
     * @throws IllegalArgumentException 当概念不存在时
     */
    @Transactional
    public void defaultConcept(Long id) {
        Concept concept = conceptRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("编码系统不存在: " + id));
        Optional<CodeSystem> byId = codeSystemRepository.findById(concept.getCodeSystemId());
        // 通过聚合侧 concepts 集合遍历，保证同一 CodeSystem 仅保留一个默认项。
        byId.ifPresent(cs -> {
            cs.getConcepts().forEach(c -> {
                // 当前项稍后单独标记为默认，这里跳过避免重复写库。
                if (c.getId().equals(concept.getId())) {
                    return;
                }
                c.setIsDefault("N");
                conceptRepository.save(c);
            });
        });

        concept.setIsDefault("Y");
        conceptRepository.save(concept);
        codeSystemQueryService.evictDictByCodeSystemId(concept.getCodeSystemId());
    }

    /**
     * 启用概念：用于字典项重新参与业务选择；保存后按编码系统驱逐缓存。
     *
     * @param id 概念主键
     * @throws IllegalArgumentException 当概念不存在时
     */
    @Transactional
    public void activateConcept(Long id) {
        Concept concept = conceptRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("概念不存在: " + id));
        concept.setIsActive(true);
        conceptRepository.save(concept);
        codeSystemQueryService.evictDictByCodeSystemId(concept.getCodeSystemId());
    }

    /**
     * 停用概念：该项不再出现在可选字典中；保存后驱逐缓存。
     *
     * @param id 概念主键
     * @throws IllegalArgumentException 当概念不存在时
     */
    @Transactional
    public void deactivateConcept(Long id) {
        Concept concept = conceptRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("概念不存在: " + id));
        concept.setIsActive(false);
        conceptRepository.save(concept);
        codeSystemQueryService.evictDictByCodeSystemId(concept.getCodeSystemId());
    }

    /**
     * 逻辑删除概念：若仍存在子节点则拒绝，避免产生孤儿层级；删除后驱逐缓存。
     *
     * @param id 概念主键
     * @throws IllegalArgumentException 当概念不存在或存在子概念时
     */
    @Transactional
    public void deleteConcept(Long id) {
        Concept concept = conceptRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("概念不存在: " + id));
        // 有子节点时禁止删除，防止树形术语断裂且无法回溯父路径。
        if (concept.getChildren() != null && !concept.getChildren().isEmpty()) {
            throw new IllegalArgumentException("该概念下有子概念，无法删除");
        }
        concept.setIsDeleted(true);
        conceptRepository.save(concept);
        codeSystemQueryService.evictDictByCodeSystemId(concept.getCodeSystemId());
    }
}
