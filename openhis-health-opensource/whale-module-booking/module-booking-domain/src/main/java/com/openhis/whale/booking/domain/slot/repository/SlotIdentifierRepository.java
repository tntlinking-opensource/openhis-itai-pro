/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
