/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.miniapp.domain.session.repository;

import java.util.Optional;

import com.openhis.miniapp.domain.session.entity.MapSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MapSessionRepository extends JpaRepository<MapSession, Long> {

    Optional<MapSession> findFirstByTokenHashAndRevokedAtIsNullAndIsDeletedFalse(String tokenHash);
}
