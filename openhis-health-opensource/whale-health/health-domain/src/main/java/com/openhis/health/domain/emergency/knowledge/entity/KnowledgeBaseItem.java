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

package com.openhis.health.domain.emergency.knowledge.entity;

import com.openhis.health.domain.emergency.knowledge.KnowledgeConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 急诊知识库条目聚合根 {@code emg_knowledge_base_item}（DM-EMG-002 / SDD §4.2）。
 */
@Getter
@Setter
@Entity
@Table(name = "emg_knowledge_base_item")
public class KnowledgeBaseItem extends BaseAuditedEntity {

    private static final Set<String> ALLOWED_TYPES = new HashSet<>();

    static {
        ALLOWED_TYPES.add(KnowledgeConstants.TYPE_CHIEF_COMPLAINT);
        ALLOWED_TYPES.add(KnowledgeConstants.TYPE_SYMPTOM);
    }

    @Column(name = "name", nullable = false, length = 500)
    private String name;

    @Column(name = "pinyin_code", nullable = false, length = 100)
    private String pinyinCode;

    @Column(name = "triage_level", nullable = false)
    private Integer triageLevel;

    @Column(name = "type", nullable = false, length = 64)
    private String type;

    @Column(name = "category", nullable = false, length = 128)
    private String category;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "status", nullable = false)
    private Integer status;

    public void assertTypeAllowed() {
        if (type == null || !ALLOWED_TYPES.contains(type)) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_KNOWLEDGE_RULE_CONFLICT, "知识类型不在允许集合内");
        }
    }

    public void assertLevelRange() {
        if (triageLevel == null || triageLevel < 1 || triageLevel > 5) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_KNOWLEDGE_RULE_CONFLICT, "triageLevel 须在 1～5");
        }
    }

    public void assertSortOrderNonNegative() {
        if (sortOrder == null || sortOrder < 0) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_KNOWLEDGE_RULE_CONFLICT, "sortOrder 须为非负整数");
        }
    }

    public void assertStatusAllowed() {
        if (status == null || (status != KnowledgeConstants.STATUS_DISABLED && status != KnowledgeConstants.STATUS_ENABLED)) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_KNOWLEDGE_RULE_CONFLICT, "status 仅允许启用/禁用取值（DM §4.3）");
        }
    }

    public void assertNamePresent() {
        if (StringUtils.isBlank(name)) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_KNOWLEDGE_INVALID_ARGUMENT, "名称不能为空");
        }
        name = name.trim();
    }

    public static KnowledgeBaseItem createNew(
            String name,
            String pinyinCode,
            Integer triageLevel,
            String type,
            String category,
            Integer sortOrder,
            Integer status) {
        KnowledgeBaseItem root = new KnowledgeBaseItem();
        root.setIsDeleted(Boolean.FALSE);
        root.setName(name != null ? name.trim() : null);
        root.setPinyinCode(pinyinCode != null ? pinyinCode.trim() : "");
        root.setTriageLevel(triageLevel);
        root.setType(type != null ? type.trim().toUpperCase(Locale.ROOT) : null);
        root.setCategory(category != null ? category.trim() : null);
        root.setSortOrder(sortOrder);
        root.setStatus(status);
        root.assertNamePresent();
        root.assertTypeAllowed();
        root.assertLevelRange();
        root.assertSortOrderNonNegative();
        root.assertStatusAllowed();
        return root;
    }

    public void applySortOrder(Integer newSortOrder) {
        if (newSortOrder == null || newSortOrder < 0) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_KNOWLEDGE_RULE_CONFLICT, "sortOrder 须为非负整数");
        }
        this.sortOrder = newSortOrder;
    }

    public void markDeleted(Long operatorId) {
        this.setIsDeleted(Boolean.TRUE);
        this.setDeletedBy(operatorId);
        this.setDeletedAt(java.time.LocalDateTime.now());
    }
}
