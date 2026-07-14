/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.session.repository;

import com.openhis.chs.domain.session.entity.YbPubSign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

/** 医保 9001 签到流水仓储 */
public interface YbPubSignRepository extends JpaRepository<YbPubSign, Long> {

    /**
     * 按契约、操作员、签到日期与状态查询最新签到流水。
     *
     * @param contractBusNo 契约 bus_no
     * @param opterNo       操作员编号
     * @param signDate      签到日期
     * @param status        签到状态
     * @param tenantId      租户 ID
     */
    Optional<YbPubSign> findFirstByContractBusNoAndOpterNoAndSignDateAndStatusAndTenantIdAndIsDeletedFalseOrderBySignTimeDesc(
        String contractBusNo, String opterNo, LocalDate signDate, String status, Long tenantId);

    /**
     * 按契约、签到日期与状态查询最新签到流水。
     *
     * @param contractBusNo 契约 bus_no
     * @param signDate      签到日期
     * @param status        签到状态
     * @param tenantId      租户 ID
     */
    Optional<YbPubSign> findFirstByContractBusNoAndSignDateAndStatusAndTenantIdAndIsDeletedFalseOrderBySignTimeDesc(
        String contractBusNo, LocalDate signDate, String status, Long tenantId);
}
