/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.infrastructure.sequence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 业务序号 Repository。
 *
 * @author OpenHIS Contributors
 */
@Repository
public interface ZxplatSequenceRepository extends JpaRepository<ZxplatSequence, String> {
}
