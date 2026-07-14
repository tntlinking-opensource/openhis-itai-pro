/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.identity.infrastructure.repository;

import com.openhis.whale.identity.domain.UserLoginSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserLoginSessionRepository extends JpaRepository<UserLoginSession, Long> {
}
