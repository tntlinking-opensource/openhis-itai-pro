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

package com.openhis.whale.booking.domain.slot.repository;

import com.openhis.whale.booking.domain.slot.entity.SlotIdentifier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 号源时段外部标识仓储接口（DM-WKF-010）。
 */
public interface SlotIdentifierRepository extends JpaRepository<SlotIdentifier, Long> {

    /**
     * 查询时段下的全部标识行（按 id 顺序返回，便于幂等对齐）。
     *
     * @param slotId 时段标识
     * @return 标识行
     */
    List<SlotIdentifier> findBySlotIdOrderByIdAsc(Long slotId);

    /**
     * 查询时段下指定 (system,value) 的标识行。
     *
     * @param slotId 时段标识
     * @param idSystem 标识体系
     * @param idValue 标识值
     * @return 标识行
     */
    List<SlotIdentifier> findBySlotIdAndIdSystemAndIdValue(Long slotId, String idSystem, String idValue);
}
