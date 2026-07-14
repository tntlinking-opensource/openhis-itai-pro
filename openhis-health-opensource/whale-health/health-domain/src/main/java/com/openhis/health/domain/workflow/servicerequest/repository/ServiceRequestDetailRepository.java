/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.servicerequest.repository;

import com.openhis.health.domain.workflow.servicerequest.entity.ServiceRequestDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 服务申请明细仓储。
 *
 * <p>约束：禁止 {@code @Query}；读取明细必须限定 serviceReqId，禁止全表扫描后内存过滤。</p>
 */
public interface ServiceRequestDetailRepository extends JpaRepository<ServiceRequestDetail, Long> {

    /**
     * 按条件查询。
     * @param serviceReqId 条件
     * @return 查询结果
     */
    List<ServiceRequestDetail> findByServiceReqId(Long serviceReqId);

    /**
     * 按条件查询。
     * @param serviceReqIds 条件
     * @return 查询结果
     */
    List<ServiceRequestDetail> findByServiceReqIdIn(List<Long> serviceReqIds);
}
