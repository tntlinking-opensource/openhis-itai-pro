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
