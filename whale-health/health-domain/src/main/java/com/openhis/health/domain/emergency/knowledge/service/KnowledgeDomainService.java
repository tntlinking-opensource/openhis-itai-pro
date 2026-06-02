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

package com.openhis.health.domain.emergency.knowledge.service;

import com.openhis.health.domain.emergency.knowledge.KnowledgeConstants;
import com.openhis.health.domain.emergency.knowledge.command.ChangeKnowledgeBaseItemSortOrderCommand;
import com.openhis.health.domain.emergency.knowledge.command.CreateKnowledgeBaseItemCommand;
import com.openhis.health.domain.emergency.knowledge.command.DeleteKnowledgeBaseItemCommand;
import com.openhis.health.domain.emergency.knowledge.command.UpdateKnowledgeBaseItemCommand;
import com.openhis.health.domain.emergency.knowledge.entity.KnowledgeBaseItem;
import com.openhis.health.domain.emergency.knowledge.repository.KnowledgeBaseItemRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.events.emergency.knowledgebaseitem.KnowledgeBaseItemCreatedEvent;
import com.openhis.health.domain.share.events.emergency.knowledgebaseitem.KnowledgeBaseItemDeletedEvent;
import com.openhis.health.domain.share.events.emergency.knowledgebaseitem.KnowledgeBaseItemUpdatedEvent;
import com.openhis.health.domain.share.events.emergency.knowledgebaseitem.KnowledgeBaseSortOrderChangedEvent;
import com.openhis.health.domain.terminology.entity.Concept;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * 急诊知识库条目写侧（DM-EMG-002 / SDD §5.1）。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeDomainService {

    private final KnowledgeBaseItemRepository knowledgeBaseItemRepository;
    private final CodeSystemQueryService codeSystemQueryService;
    private final IDomainEventPublisher domainEventPublisher;

    public KnowledgeBaseItem createKnowledgeBaseItem(CreateKnowledgeBaseItemCommand cmd) {
        if (cmd == null || cmd.getOperatorId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_KNOWLEDGE_INVALID_ARGUMENT, "命令或操作人不能为空");
        }
        assertCategoryEnabled(cmd.getCategory());
        String typeNorm = normalizeType(cmd.getType());
        assertNameUniqueWithinType(typeNorm, cmd.getName(), null);
        String pinyin = cmd.getPinyinCode() != null ? cmd.getPinyinCode().trim() : "";
        KnowledgeBaseItem root =
                KnowledgeBaseItem.createNew(cmd.getName(), pinyin, cmd.getTriageLevel(), typeNorm, cmd.getCategory(),
                        cmd.getSortOrder(), cmd.getStatus());
        assertSortOrderFree(root.getType(), root.getSortOrder(), null);
        root = knowledgeBaseItemRepository.save(root);
        domainEventPublisher.publish(new KnowledgeBaseItemCreatedEvent(
                root.getId(), root.getType(), root.getCategory(), root.getStatus()));
        return root;
    }

    public KnowledgeBaseItem updateKnowledgeBaseItem(UpdateKnowledgeBaseItemCommand cmd) {
        if (cmd == null || cmd.getItemId() == null || cmd.getOperatorId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_KNOWLEDGE_INVALID_ARGUMENT, "命令主键或操作人不能为空");
        }
        KnowledgeBaseItem root = knowledgeBaseItemRepository
                .findByIdAndIsDeletedFalse(cmd.getItemId())
                .orElseThrow(() -> new BusinessException(ErrorCodeConstants.EMERGENCY_KNOWLEDGE_NOT_FOUND, "知识库条目不存在"));
        List<String> changed = new ArrayList<>();
        if (cmd.getName() != null && !Objects.equals(cmd.getName().trim(), root.getName())) {
            root.setName(cmd.getName());
            changed.add("name");
        }
        if (cmd.getPinyinCode() != null && !Objects.equals(cmd.getPinyinCode().trim(), root.getPinyinCode())) {
            root.setPinyinCode(cmd.getPinyinCode().trim());
            changed.add("pinyinCode");
        }
        if (cmd.getTriageLevel() != null && !Objects.equals(cmd.getTriageLevel(), root.getTriageLevel())) {
            root.setTriageLevel(cmd.getTriageLevel());
            changed.add("triageLevel");
        }
        if (cmd.getType() != null) {
            String typeNorm = normalizeType(cmd.getType());
            if (!Objects.equals(typeNorm, root.getType())) {
                root.setType(typeNorm);
                changed.add("type");
            }
        }
        if (cmd.getCategory() != null && !Objects.equals(cmd.getCategory().trim(), root.getCategory())) {
            assertCategoryEnabled(cmd.getCategory());
            root.setCategory(cmd.getCategory().trim());
            changed.add("category");
        }
        if (cmd.getSortOrder() != null && !Objects.equals(cmd.getSortOrder(), root.getSortOrder())) {
            assertSortOrderFree(root.getType(), cmd.getSortOrder(), root.getId());
            root.setSortOrder(cmd.getSortOrder());
            changed.add("sortOrder");
        }
        if (cmd.getStatus() != null && !Objects.equals(cmd.getStatus(), root.getStatus())) {
            root.setStatus(cmd.getStatus());
            changed.add("status");
        }
        root.assertNamePresent();
        root.assertTypeAllowed();
        root.assertLevelRange();
        root.assertSortOrderNonNegative();
        root.assertStatusAllowed();
        if (cmd.getName() != null || cmd.getType() != null) {
            assertNameUniqueWithinType(root.getType(), root.getName(), root.getId());
        }
        root = knowledgeBaseItemRepository.save(root);
        if (!changed.isEmpty()) {
            domainEventPublisher.publish(new KnowledgeBaseItemUpdatedEvent(root.getId(), String.join(",", changed)));
        }
        return root;
    }

    public boolean deleteKnowledgeBaseItem(DeleteKnowledgeBaseItemCommand cmd) {
        if (cmd == null || cmd.getItemId() == null || cmd.getOperatorId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_KNOWLEDGE_INVALID_ARGUMENT, "命令主键或操作人不能为空");
        }
        KnowledgeBaseItem root = knowledgeBaseItemRepository
                .findByIdAndIsDeletedFalse(cmd.getItemId())
                .orElseThrow(() -> new BusinessException(ErrorCodeConstants.EMERGENCY_KNOWLEDGE_NOT_FOUND, "知识库条目不存在"));
        root.markDeleted(cmd.getOperatorId());
        knowledgeBaseItemRepository.save(root);
        domainEventPublisher.publish(new KnowledgeBaseItemDeletedEvent(root.getId(), cmd.getOperatorId()));
        return true;
    }

    public KnowledgeBaseItem changeKnowledgeBaseItemSortOrder(ChangeKnowledgeBaseItemSortOrderCommand cmd) {
        if (cmd == null || cmd.getItemId() == null || cmd.getNewSortOrder() == null || cmd.getOperatorId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_KNOWLEDGE_INVALID_ARGUMENT, "排序变更参数不完整");
        }
        KnowledgeBaseItem root = knowledgeBaseItemRepository
                .findByIdAndIsDeletedFalse(cmd.getItemId())
                .orElseThrow(() -> new BusinessException(ErrorCodeConstants.EMERGENCY_KNOWLEDGE_NOT_FOUND, "知识库条目不存在"));
        Integer oldOrder = root.getSortOrder();
        assertSortOrderFree(root.getType(), cmd.getNewSortOrder(), root.getId());
        root.applySortOrder(cmd.getNewSortOrder());
        root = knowledgeBaseItemRepository.save(root);
        domainEventPublisher.publish(new KnowledgeBaseSortOrderChangedEvent(root.getId(), oldOrder, root.getSortOrder()));
        return root;
    }

    private void assertCategoryEnabled(String category) {
        if (StringUtils.isBlank(category)) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_KNOWLEDGE_INVALID_ARGUMENT, "分类编码不能为空");
        }
        Concept concept =
                codeSystemQueryService.findConceptByCodeSystemNameAndCode(
                        KnowledgeConstants.EMERGENCY_KNOWLEDGE_CATEGORY_CODE_SYSTEM, category.trim());
        if (concept == null || !Boolean.TRUE.equals(concept.getIsActive())) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_KNOWLEDGE_RULE_CONFLICT, "分类术语未启用或不存在（CUS）");
        }
    }

    private void assertNameUniqueWithinType(String type, String name, Long excludeId) {
        if (StringUtils.isBlank(name)) {
            return;
        }
        String trimmed = name.trim();
        boolean dup;
        if (excludeId == null) {
            dup = knowledgeBaseItemRepository.existsByTypeAndNameIgnoreCaseAndIsDeletedFalse(type, trimmed);
        } else {
            dup = knowledgeBaseItemRepository.existsByTypeAndNameIgnoreCaseAndIdNotAndIsDeletedFalse(type, trimmed, excludeId);
        }
        if (dup) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_KNOWLEDGE_CONFLICT, "同类型下名称已存在（BR-EMG-KB-008）");
        }
    }

    private void assertSortOrderFree(String type, Integer sortOrder, Long excludeId) {
        Optional<KnowledgeBaseItem> other;
        if (excludeId == null) {
            other = knowledgeBaseItemRepository.findByTypeAndSortOrderAndIsDeletedFalse(type, sortOrder);
        } else {
            other = knowledgeBaseItemRepository.findByTypeAndSortOrderAndIdNotAndIsDeletedFalse(type, sortOrder, excludeId);
        }
        if (other.isPresent()) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_KNOWLEDGE_RULE_CONFLICT, "同类型下排序号冲突");
        }
    }

    private static String normalizeType(String type) {
        if (type == null) {
            return null;
        }
        return type.trim().toUpperCase(Locale.ROOT);
    }
}
