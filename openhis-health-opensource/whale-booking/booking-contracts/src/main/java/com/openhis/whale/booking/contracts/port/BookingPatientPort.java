package com.openhis.whale.booking.contracts.port;

import com.openhis.whale.booking.contracts.dto.BookingPatientPageResult;
import com.openhis.whale.booking.contracts.query.BookingPatientSearchQuery;

import java.util.List;
import java.util.Map;

/**
 * 患者主数据读 Port（预约登记台选患者）。
 */
public interface BookingPatientPort {

    /**
     * 分页检索可选患者。
     *
     * @param query 检索条件
     * @return 分页结果
     */
    BookingPatientPageResult pagePatientsForSelect(BookingPatientSearchQuery query);

    /**
     * 批量解析患者姓名；未知 id 不写入 map。
     *
     * @param patientIds 患者主键列表
     * @return 患者主键 → 姓名
     */
    Map<Long, String> findPatientNamesByIds(List<Long> patientIds);
}
