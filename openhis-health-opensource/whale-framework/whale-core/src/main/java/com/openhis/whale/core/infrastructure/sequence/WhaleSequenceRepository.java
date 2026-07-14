/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.sequence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 业务序号 Repository。
 *
 * @author Whale Framework
 */
@Repository
public interface WhaleSequenceRepository extends JpaRepository<WhaleSequence, String> {
}
