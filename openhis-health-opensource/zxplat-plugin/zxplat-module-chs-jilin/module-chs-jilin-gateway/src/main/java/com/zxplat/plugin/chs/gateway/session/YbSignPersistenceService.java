/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.session;

import com.zxplat.plugin.chs.domain.session.entity.YbPubSign;
import com.zxplat.plugin.chs.domain.session.service.YbPubSignDomainService;
import com.zxplat.plugin.chs.domain.session.service.YbPubSignQueryService;
import com.zxplat.plugin.chs.gateway.config.ContractConfig;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 9001 sign_no 持久化：当日有效记录写入 {@code yb_pub_sign}，避免重复调用签到。
 *
 * <p>由 {@link InsuranceSignSessionService} 在 9001 成功后调用 {@link #saveSignIn}；
 * UoW 由 AppServiceAspect 管理。<。</p> */
@Service
@RequiredArgsConstructor
public class YbSignPersistenceService {

    private final YbPubSignQueryService queryService;
    private final YbPubSignDomainService domainService;

    /**
     * 查询当日有效签到会话（先精确 opter，再放宽到同 bus_no 任意 opter） */
    public Optional<ChsSignSession> findTodaySession(String contractBusNo, String opterNo) {
        if (contractBusNo == null || contractBusNo.isBlank()) {
            return Optional.empty();
        }
        Long tenantId = resolveTenantId();
        LocalDate today = LocalDate.now();
        if (opterNo != null && !opterNo.isBlank()) {
            Optional<ChsSignSession> exact = mapSession(
                queryService.findTodayByOpter(contractBusNo.trim(), opterNo.trim(), today, tenantId));
            if (exact.isPresent()) {
                return exact;
            }
        }
        return mapSession(queryService.findTodayByBusNo(contractBusNo.trim(), today, tenantId));
    }

    private static Optional<ChsSignSession> mapSession(Optional<YbPubSign> row) {
        return row
            .filter(r -> r.getSignNo() != null && !r.getSignNo().isBlank())
            .map(r -> new ChsSignSession(r.getSignNo(), r.getOpterNo()));
    }

    public Optional<String> findTodaySignNo(String contractBusNo, String opterNo) {
        return findTodaySession(contractBusNo, opterNo).map(ChsSignSession::signNo);
    }

    /**
     * 9001 成功后 upsert 当日签到记录 */
    public void saveSignIn(ContractConfig config, String opterNo, String signNo, String ybUsername) {
        if (config == null || signNo == null || signNo.isBlank()) {
            return;
        }
        Long tenantId = resolveTenantId();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        String opter = "1";
        if (opterNo != null && !opterNo.isBlank()) {
            opter = opterNo.trim();
        }
        domainService.saveSignIn(
            config.getBusNo(),
            config.getAdmVs(),
            config.getFixmedinsCode(),
            opter,
            signNo,
            ybUsername,
            tenantId,
            today,
            now);
    }

    private static Long resolveTenantId() {
        Long tenantId = TenantUtils.getCurrentTenantId();
        if (tenantId != null) {
            return tenantId;
        }
        return 0L;
    }
}
