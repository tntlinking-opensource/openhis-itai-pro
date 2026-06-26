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

package com.openhis.health.domain.diagnosis.vitalsign.repository;

import com.openhis.health.domain.diagnosis.vitalsign.entity.VitalSignDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 生命体征明细仓储（DM-DIA-002 v2.1 §4.5）。
 */
public interface VitalSignDetailRepository extends JpaRepository<VitalSignDetail, Long> {

    /**
     * 按主表 id 查询有效明细，术语体系与字典项升序。
     *
     * @param vitalSignId 主表 id
     * @return 明细列表
     */
    List<VitalSignDetail> findAllByVitalSignIdAndIsDeletedFalseOrderByTermCodeAscItemCodeAsc(Long vitalSignId);

    /**
     * 按主表 id 与自然键查询有效明细。
     *
     * @param vitalSignId 主表 id
     * @param termCode    术语体系 code
     * @param itemCode    字典项 code
     * @return 明细
     */
    Optional<VitalSignDetail> findByVitalSignIdAndTermCodeAndItemCodeAndIsDeletedFalse(
        Long vitalSignId, String termCode, String itemCode);

    /**
     * 按主表 id 集合查询有效明细。
     *
     * @param vitalSignIds 主表 id 集合
     * @return 明细列表
     */
    List<VitalSignDetail> findAllByVitalSignIdInAndIsDeletedFalse(Collection<Long> vitalSignIds);
}
