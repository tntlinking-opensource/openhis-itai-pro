/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.prescriptionreview.repository;

import com.zxplat.domain.custom.prescriptionreview.entity.PrescriptionReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 处方点评仓储（仅本聚合；禁 {@code @Query}）。
 */
public interface PrescriptionReviewRepository extends JpaRepository<PrescriptionReview, Long> {

    /**
     * 是否存在该处方的有效点评（BR-PRV-001）。
     *
     * @param prescriptionNo 处方号
     * @return 存在则为 true
     */
    boolean existsByPrescriptionNoAndIsDeletedFalse(String prescriptionNo);

    /**
     * 按处方号加载有效点评。
     *
     * @param prescriptionNo 处方号
     * @return 有效点评
     */
    Optional<PrescriptionReview> findByPrescriptionNoAndIsDeletedFalse(String prescriptionNo);

    /**
     * 按处方号集合批量加载有效点评。
     *
     * @param prescriptionNos 处方号集合
     * @return 有效点评列表
     */
    List<PrescriptionReview> findByPrescriptionNoInAndIsDeletedFalse(Collection<String> prescriptionNos);

    /**
     * 按主键加载有效点评。
     *
     * @param id 点评主键
     * @return 有效点评
     */
    Optional<PrescriptionReview> findByIdAndIsDeletedFalse(Long id);

    /**
     * 列出全部有效点评（未软删）。
     *
     * @return 有效点评列表；无则空列表
     */
    List<PrescriptionReview> findByIsDeletedFalse();

    /**
     * 按是否合理列出有效点评。
     *
     * @param reasonableFlag 是否合理
     * @return 有效点评列表；无则空列表
     */
    List<PrescriptionReview> findByIsDeletedFalseAndReasonableFlag(Boolean reasonableFlag);
}
