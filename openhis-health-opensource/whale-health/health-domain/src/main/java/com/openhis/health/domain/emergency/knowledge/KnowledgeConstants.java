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

package com.openhis.health.domain.emergency.knowledge;

/**
 * 急诊知识库跨类常量（DM-EMG-002）。
 *
 * <p>{@link #EMERGENCY_KNOWLEDGE_CATEGORY_CODE_SYSTEM} 对应术语端口/DM-CUS-009 下的编码系统名称，须与 Flyway 种子一致。</p>
 */
public final class KnowledgeConstants {

    private KnowledgeConstants() {
    }

    /**
     * 急诊知识库「分类」概念所处的编码系统名称（CUS / CodeSystem.name）。
     */
    public static final String EMERGENCY_KNOWLEDGE_CATEGORY_CODE_SYSTEM = "emg-knowledge-category";

    /** 主诉词条（分诊多选 → chiefComplaintJson）。 */
    public static final String TYPE_CHIEF_COMPLAINT = "CHIEF_COMPLAINT";

    /** 症状词条（分诊多选 → symptomJson）。 */
    public static final String TYPE_SYMPTOM = "SYMPTOM";

    public static final int STATUS_DISABLED = 0;
    public static final int STATUS_ENABLED = 1;
}
