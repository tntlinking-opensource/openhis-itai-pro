/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.gateway.session;

import com.openhis.chs.application.config.ContractConfig;
import com.openhis.chs.domain.session.entity.YbPubSign;
import com.openhis.chs.domain.session.repository.YbPubSignRepository;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 9001 sign_no 持久化：当日有效记录写入 {@code yb_pub_sign}，避免重复调用签到。
 *
 * <p>由 {@link InsuranceSignSessionAppService} 在 9001 成功后调用 {@link #saveSignIn}；
 * UoW 由 AppServiceAspect 管理。</p>
 */
@Service
@RequiredArgsConstructor
public class YbSignPersistenceAppService {

    private final YbPubSignRepository repository;

    /**
     * 查询当日有效签到会话（先精确 opter，再放宽到同 bus_no 任意 opter）。
     *
     * @param contractBusNo 契约 bus_no
     * @param opterNo 签到操作员；可为 null
     * @return 含 signNo 与 opterNo 的会话；无记录时 empty
     */
    public Optional<ChsSignSession> findTodaySession(String contractBusNo, String opterNo) {
        if (contractBusNo == null || contractBusNo.isBlank()) {
            return Optional.empty();
        }
        Long tenantId = resolveTenantId();
        LocalDate today = LocalDate.now();
        // Pre：优先匹配指定 opter 的当日签到
        if (opterNo != null && !opterNo.isBlank()) {
            Optional<ChsSignSession> exact = mapSession(repository
                .findFirstByContractBusNoAndOpterNoAndSignDateAndStatusAndTenantIdAndIsDeletedFalseOrderBySignTimeDesc(
                    contractBusNo.trim(), opterNo.trim(), today, "1", tenantId));
            if (exact.isPresent()) {
                return exact;
            }
        }
        // Pre：放宽到同 bus_no 当日任意 opter（多操作员共用终端场景）
        return mapSession(repository
            .findFirstByContractBusNoAndSignDateAndStatusAndTenantIdAndIsDeletedFalseOrderBySignTimeDesc(
                contractBusNo.trim(), today, "1", tenantId));
    }

    private static Optional<ChsSignSession> mapSession(Optional<YbPubSign> row) {
        return row
            .filter(r -> r.getSignNo() != null && !r.getSignNo().isBlank())
            .map(r -> new ChsSignSession(r.getSignNo(), r.getOpterNo()));
    }

    /**
     * 仅返回 sign_no 字符串（{@link InsuranceSignSessionAppService#getSignNo} 便捷方法）。
     *
     * @param contractBusNo 契约 bus_no
     * @param opterNo 签到操作员
     */
    public Optional<String> findTodaySignNo(String contractBusNo, String opterNo) {
        return findTodaySession(contractBusNo, opterNo).map(ChsSignSession::signNo);
    }

    /**
     * 9001 成功后 upsert 当日签到记录。
     *
     * @param config 契约配置（bus_no、adm_vs、fixmedins_code）
     * @param opterNo 实际签到成功的操作员
     * @param signNo 平台返回的 sign_no
     * @param ybUsername OAuth 用户名（审计）
     */
    public void saveSignIn(ContractConfig config, String opterNo, String signNo, String ybUsername) {
        // Gate：缺 signNo 不落库
        if (config == null || signNo == null || signNo.isBlank()) {
            return;
        }
        Long tenantId = resolveTenantId();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        String busNo = config.getBusNo();
        String opter = "1";
        if (opterNo != null && !opterNo.isBlank()) {
            opter = opterNo.trim();
        }

        // Pre：查当日同 opter 是否已有记录（upsert）
        Optional<YbPubSign> existing = repository
            .findFirstByContractBusNoAndOpterNoAndSignDateAndStatusAndTenantIdAndIsDeletedFalseOrderBySignTimeDesc(
                busNo, opter, today, "1", tenantId);

        // Cmd：更新或新建 yb_pub_sign
        YbPubSign row = existing.orElseGet(YbPubSign::new);
        if (row.getId() == null) {
            row.setContractBusNo(busNo);
            row.setAdmVs(config.getAdmVs());
            row.setFixmedinsCode(config.getFixmedinsCode());
            row.setOpterNo(opter);
            row.setSignDate(today);
            row.setTenantId(tenantId);
            row.setIsDeleted(false);
        }
        row.setSignNo(signNo);
        row.setSignTime(now);
        row.setYbUsername(ybUsername);
        row.setStatus("1");
        row.setSignOutTime(null);
        repository.save(row);
    }

    private static Long resolveTenantId() {
        Long tenantId = TenantUtils.getCurrentTenantId();
        if (tenantId != null) {
            return tenantId;
        }
        return 0L;
    }
}
