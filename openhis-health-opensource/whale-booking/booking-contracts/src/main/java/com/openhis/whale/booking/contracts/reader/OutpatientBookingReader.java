package com.openhis.whale.booking.contracts.reader;

import com.openhis.whale.booking.contracts.dto.BookableServiceOption;
import com.openhis.whale.booking.contracts.dto.BookableSlotHint;
import com.openhis.whale.booking.contracts.dto.OutpatientBookingSnapshot;
import com.openhis.whale.booking.contracts.command.BookingRegistrationValidateCommand;
import com.openhis.whale.booking.contracts.query.OutpatientBookingLookupQuery;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * health 挂号/医生站只读消费预约事实。
 *
 * <p>由 booking-adapter 实现；未装 booking 时不注册 Bean。</p>
 */
public interface OutpatientBookingReader {

    /**
     * IF-REG-12：按患者/预约单号查当日有效门诊预约。
     *
     * @param query 检索条件
     * @return 有效预约快照；无则 empty
     */
    Optional<OutpatientBookingSnapshot> findActiveOutpatientBooking(OutpatientBookingLookupQuery query);

    /**
     * 按预约主键加载有效门诊预约快照（IF-REG-13 回传 slotId 等）。
     *
     * @param appointmentId 预约主键
     * @return 快照；无则 empty
     */
    Optional<OutpatientBookingSnapshot> loadOutpatientBookingById(Long appointmentId);

    /**
     * IF-REG-01 号源下拉收窄：岗位 service 与当日有放号服务求交。
     *
     * @param organizationId 科室
     * @param practitionerId 医师；可空
     * @param date 挂号自然日
     * @return 可约号源类型选项
     */
    List<BookableServiceOption> listBookableServiceTypes(
        Long organizationId, Long practitionerId, LocalDate date);

    /**
     * IF-REG-14：可约时段摘要。
     */
    List<BookableSlotHint> listBookableSlots(
        Long organizationId, Long practitionerId, Long healthcareServiceId, LocalDate date);

    /**
     * IF-REG-13 前置校验（只读部分）。
     */
    void validateForRegistration(BookingRegistrationValidateCommand command);
}
