/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.session.service;

import com.openhis.chs.domain.session.entity.YbPubSign;
import com.openhis.chs.domain.session.repository.YbPubSignRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

/** 9001 签到流水只读查询 */
@Service
@RequiredArgsConstructor
public class YbPubSignQueryService {

    private final YbPubSignRepository repository;

    /**
     * 按契约、操作员与签到日期查询当日有效签到流水。
     *
     * @param contractBusNo 契约 bus_no
     * @param opterNo       操作员编号
     * @param signDate      签到日期
     * @param tenantId      租户 ID
     * @return 最新签到流水
     */
    public Optional<YbPubSign> findTodayByOpter(
            String contractBusNo, String opterNo, LocalDate signDate, Long tenantId) {
        return repository.findFirstByContractBusNoAndOpterNoAndSignDateAndStatusAndTenantIdAndIsDeletedFalseOrderBySignTimeDesc(
            contractBusNo, opterNo, signDate, "1", tenantId);
    }

    /**
     * 按契约与签到日期查询当日有效签到流水（不限操作员）。
     *
     * @param contractBusNo 契约 bus_no
     * @param signDate      签到日期
     * @param tenantId      租户 ID
     * @return 最新签到流水
     */
    public Optional<YbPubSign> findTodayByBusNo(String contractBusNo, LocalDate signDate, Long tenantId) {
        return repository.findFirstByContractBusNoAndSignDateAndStatusAndTenantIdAndIsDeletedFalseOrderBySignTimeDesc(
            contractBusNo, signDate, "1", tenantId);
    }
}
