/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.domain.repository;

import com.zxplat.plugin.wi.domain.entity.YbWiInvokeSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** {@link YbWiInvokeSession} 仓储。 */
public interface YbWiInvokeSessionRepository extends JpaRepository<YbWiInvokeSession, Long> {

    /**
     * 按 sessionId 查 ACTIVE 会话。
     *
     * @param sessionId UUID
     * @return 会话
     */
    Optional<YbWiInvokeSession> findBySessionId(String sessionId);
}
