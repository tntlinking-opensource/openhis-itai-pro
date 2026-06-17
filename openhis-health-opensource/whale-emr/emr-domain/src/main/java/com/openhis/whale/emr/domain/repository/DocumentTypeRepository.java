/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.whale.emr.domain.repository;

import com.openhis.whale.emr.domain.entity.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 文书类型仓储。表 emr_document_type；code 为业务唯一键。
 */
@Repository
public interface DocumentTypeRepository extends JpaRepository<DocumentType, Long>, JpaSpecificationExecutor<DocumentType> {

    /** 按编码查类型（含已软删，慎用）。 */
    Optional<DocumentType> findByCode(String code);

    /** 编码是否已占用。 */
    boolean existsByCode(String code);

    /** 按编码查未软删类型。 */
    Optional<DocumentType> findByCodeAndIsDeletedFalse(String code);

    /** 按主键查未软删类型。 */
    Optional<DocumentType> findByIdAndIsDeletedFalse(Long id);
}
