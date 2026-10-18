/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.session.service;

import com.zxplat.plugin.chs.domain.session.entity.YbPubSign;
import com.zxplat.plugin.chs.domain.session.repository.YbPubSignRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/** 9001 签到流水写侧 */
@Service
@RequiredArgsConstructor
public class YbPubSignDomainService {

    private final YbPubSignRepository repository;
    private final YbPubSignQueryService queryService;

    /**
     * 9001 签到成功落库（同日同操作员 upsert）。
     *
     * @param contractBusNo  契约 bus_no
     * @param admVs          医保版本
     * @param fixmedinsCode  定点医药机构编号
     * @param opterNo        操作员编号
     * @param signNo         签到流水号
     * @param ybUsername     医保用户名
     * @param tenantId       租户 ID
     * @param signDate       签到日期
     * @param signTime       签到时间
     * @return 已保存签到流水
     */
    @Transactional
    public YbPubSign saveSignIn(
            String contractBusNo,
            String admVs,
            String fixmedinsCode,
            String opterNo,
            String signNo,
            String ybUsername,
            Long tenantId,
            LocalDate signDate,
            LocalDateTime signTime) {
        Optional<YbPubSign> existing = queryService.findTodayByOpter(contractBusNo, opterNo, signDate, tenantId);
        YbPubSign row = existing.orElseGet(YbPubSign::new);
        if (row.getId() == null) {
            row.setContractBusNo(contractBusNo);
            row.setAdmVs(admVs);
            row.setFixmedinsCode(fixmedinsCode);
            row.setOpterNo(opterNo);
            row.setSignDate(signDate);
            row.setTenantId(tenantId);
            row.setIsDeleted(false);
        }
        row.setSignNo(signNo);
        row.setSignTime(signTime);
        row.setYbUsername(ybUsername);
        row.setStatus("1");
        row.setSignOutTime(null);
        return repository.save(row);
    }
}
