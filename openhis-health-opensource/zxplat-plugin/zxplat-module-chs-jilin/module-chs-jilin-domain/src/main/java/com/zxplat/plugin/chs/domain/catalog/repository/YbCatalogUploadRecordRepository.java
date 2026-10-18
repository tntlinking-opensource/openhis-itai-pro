/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.repository;

import com.zxplat.plugin.chs.domain.catalog.entity.YbCatalogUploadRecord;
import org.springframework.data.jpa.repository.JpaRepository;

/** {@code yb_catalog_upload_record} JPA 仓储 */
public interface YbCatalogUploadRecordRepository extends JpaRepository<YbCatalogUploadRecord, Long> {
}
