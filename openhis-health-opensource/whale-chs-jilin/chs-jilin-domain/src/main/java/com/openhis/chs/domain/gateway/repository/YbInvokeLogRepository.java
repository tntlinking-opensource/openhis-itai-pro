/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.gateway.repository;

import com.openhis.chs.domain.gateway.entity.YbInvokeLog;
import org.springframework.data.jpa.repository.JpaRepository;

/** {@link YbInvokeLog} 仓储。 */
public interface YbInvokeLogRepository extends JpaRepository<YbInvokeLog, Long> {
}
