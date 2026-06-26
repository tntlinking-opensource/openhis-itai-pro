package com.openhis.whale.einvoice.boss.domain.invokelog.repository;

import com.openhis.whale.einvoice.boss.domain.invokelog.entity.EinvBossInvokeLog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 博思 HTTP 调用日志仓储。
 */
public interface EinvBossInvokeLogRepository extends JpaRepository<EinvBossInvokeLog, Long> {
}
