/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.queries;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 住院就诊 Criteria 地理收窄预解析结果（B.6.4：querysupport 经 Location QS 展开床位后传入 domain）。
 *
 * <p>避免 domain Criteria 直接依赖 Location 查询服务；病房/房屋筛选所需床位主键由 querysupport
 * {@code EncounterContextReader} 预展开后传入。</p>
 */
public final class InpatientEncounterLocationScope {

    /** 病房（house）筛选时下属床位主键；无房屋筛选时为 empty。 */
    private final List<Long> houseBedIds;

    /** 病区主键 → 该病区下全部床位主键（房间→床位逐级展开）。 */
    private final Map<Long, List<Long>> bedIdsByWardId;

    /**
     * @param houseBedIds    房屋下属床位；{@code null} 视为 empty
     * @param bedIdsByWardId 病区床位映射；{@code null} 视为 empty
     */
    public InpatientEncounterLocationScope(List<Long> houseBedIds, Map<Long, List<Long>> bedIdsByWardId) {
        if (houseBedIds == null) {
            // 构造缺省：无房屋床位收窄
            this.houseBedIds = List.of();
        } else {
            this.houseBedIds = List.copyOf(houseBedIds);
        }
        if (bedIdsByWardId == null) {
            this.bedIdsByWardId = Map.of();
        } else {
            this.bedIdsByWardId = Collections.unmodifiableMap(new HashMap<>(bedIdsByWardId));
        }
    }

    /** 无地理预解析（住院列表未按床位/病区收窄时）。 */
    public static InpatientEncounterLocationScope empty() {
        return new InpatientEncounterLocationScope(List.of(), Map.of());
    }

    /**
     * @return 房屋筛选下属床位主键（不可变）
     */
    public List<Long> houseBedIds() {
        return houseBedIds;
    }

    /**
     * @param wardId 病区位置主键
     * @return 该病区下床位主键；未预解析或 {@code wardId} 为 null 时 empty
     */
    public List<Long> bedIdsForWard(Long wardId) {
        if (wardId == null) {
            return List.of();
        }
        List<Long> ids = bedIdsByWardId.get(wardId);
        if (ids == null) {
            return List.of();
        }
        return ids;
    }

    /**
     * @param wardIds 病区主键集合
     * @return 多病区床位主键并集；入参 empty 时 empty
     */
    public List<Long> bedIdsForWards(Collection<Long> wardIds) {
        if (wardIds == null || wardIds.isEmpty()) {
            return List.of();
        }
        List<Long> all = new ArrayList<>();
        // 按病区合并床位，供 existsActiveWardIn 活动床口径
        for (Long wardId : wardIds) {
            all.addAll(this.bedIdsForWard(wardId));
        }
        return all;
    }
}
