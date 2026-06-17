package com.openhis.whale.booking.contracts.port;

import com.openhis.whale.booking.contracts.dto.BookableServiceRequestDto;

import java.util.List;

/**
 * 医技医嘱衔接 Port。
 */
public interface BookingServiceRequestPort {

    /**
     * 按就诊列出可预约的检验/检查类医嘱。
     *
     * @param encounterId 就诊主键
     * @return 可约医嘱列表
     */
    List<BookableServiceRequestDto> listBookableServiceRequestsByEncounter(Long encounterId);

    /**
     * 医嘱完成预约后回写关联（health 侧暂无写能力时由 adapter 占位）。
     *
     * @param serviceRequestId 医嘱主键
     * @param appointmentId    预约主键
     */
    void markServiceRequestBooked(Long serviceRequestId, Long appointmentId);

    /**
     * 解析医嘱关联的诊疗项目主键。
     *
     * @param serviceRequestId 医嘱主键
     * @return activityDefinitionId；不存在时 null
     */
    Long resolveActivityIdByServiceRequestId(Long serviceRequestId);

    /**
     * 解析医嘱所属就诊主键。
     *
     * @param serviceRequestId 医嘱主键
     * @return encounterId；不存在时 null
     */
    Long resolveEncounterIdByServiceRequestId(Long serviceRequestId);
}
