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

package com.openhis.health.domain.clinical.condition.repository;

import com.openhis.health.domain.clinical.condition.entity.Condition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 诊断（Condition）病情事实持久化仓储。
 *
 * <p>派生查询由方法名表达过滤条件；跨聚合复杂统计（如医嘱引用计数）不在此接口用 {@code @Query}
 * 扩展，见 {@link #countAdviceRefByConditionId(Long)} 说明。</p>
 */
public interface ConditionRepository extends JpaRepository<Condition, Long> {

    /**
     * 按主键加载单条病情事实，不存在时返回空 {@link Optional}（便于调用方 {@code orElseThrow}，
     * 避免使用会抛异常的按 id 直取 API）。
     */
    Optional<Condition> findById(Long id);

    /**
     * 按主键集合批量加载（A.9.1 批量读）；{@code ids} 为空时行为由调用方约束，建议不入库。
     *
     * @param ids 主键集合
     * @return 命中实体列表；顺序与实现相关，勿依赖与入参 id 顺序一致
     */
    List<Condition> findByIdIn(List<Long> ids);

    /**
     * 按就诊加载该次就诊下全部病情事实（含软删与否由实体/底座全局过滤策略决定，调用方勿假设已排除软删）。
     *
     * @param encounterId 就诊主键
     * @return 列表；无记录时为空集合
     */
    List<Condition> findByEncounterId(Long encounterId);

    /**
     * 按患者加载其历史诊断事实（用于医生站「病人历史诊断」快捷数据源）。
     *
     * @param patientId 患者主键
     * @return 诊断事实列表；无记录时为空集合
     */
    List<Condition> findByPatientId(Long patientId);

    /**
     * 统计指定诊断被医嘱引用的数量（BR-CND-003 前置门控）。
     *
     * <p>DOC-CONFLICT: 此计数需跨表查询 wkf_service_request / wkf_medication_request 等医嘱表，
     * JPA 派生方法名无法自动生成跨表查询，@Query 被架构规范禁止；
     * 当前以 default 方法返回 0 作为安全占位，实际跨表计数应由应用服务层聚合各医嘱仓储结果后
     * 传入领域服务，或由架构决策放开 @Query 限制后补充实现。
     * 在此占位实现下，BR-CND-003 形同虚设，删除前不会拦截医嘱引用——待架构决策后必须补全。</p>
     *
     * @param conditionId 诊断记录标识
     * @return 引用该诊断的医嘱数量
     */
    default int countAdviceRefByConditionId(Long conditionId) {
        // DOC-CONFLICT: 跨表计数暂不可用，见上方说明。
        return 0;
    }
}
