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

package com.openhis.health.domain.workflow.activitydefinition.repository;

import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinitionChild;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 诊疗项目子项仓储。
 *
 * <p>约束：禁止 {@code @Query}；调用方需要批量语义时，必须通过集合化接口完成，禁止循环逐条 I/O。</p>
 */
public interface ActivityDefinitionChildRepository extends JpaRepository<ActivityDefinitionChild, Long> {

    /**
     * 按主键加载未删除记录。
     *
     * @param id 主键
     * @return 子项实体
     */
    Optional<ActivityDefinitionChild> findById(Long id);

    /**
     * 按父项标识加载未删除子项行。
     *
     * @param activityId 父项标识
     * @return 子项集合
     */
    List<ActivityDefinitionChild> findByActivityId(Long activityId);

    /**
     * 按父项标识集合加载未删除子项行，用于批量停用/启用等需要预加载子项的场景。
     *
     * <p>注意：候选集规模可能较大，调用方需评估是否必须加载。</p>
     *
     * @param activityIds 父项标识集合
     * @return 子项集合
     */
    List<ActivityDefinitionChild> findByActivityIdIn(List<Long> activityIds);
}
