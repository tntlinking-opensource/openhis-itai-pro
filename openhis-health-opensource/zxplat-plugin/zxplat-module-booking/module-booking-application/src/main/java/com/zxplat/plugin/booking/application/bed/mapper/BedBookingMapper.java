/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.bed.mapper;

import com.zxplat.plugin.booking.application.bed.request.CancelBedReservationRequest;
import com.zxplat.plugin.booking.application.bed.request.ConfirmAdmissionRequest;
import com.zxplat.plugin.booking.application.bed.request.ReserveBedRequest;
import com.zxplat.plugin.booking.application.bed.response.BedMatrixCellDto;
import com.zxplat.plugin.booking.application.bed.response.BedMatrixRowDto;
import com.zxplat.plugin.booking.application.bed.response.BedReservationListItemDto;
import com.zxplat.plugin.booking.application.bed.response.LoadBedBookingFormInitResponse;
import com.zxplat.plugin.booking.application.bed.response.LoadWardTreeResponse;
import com.zxplat.plugin.booking.application.bed.response.ReserveBedResponse;
import com.zxplat.plugin.booking.application.bed.response.WardTreeNodeDto;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.plugin.booking.domain.bed.command.ReleaseBedReservationCommand;
import com.zxplat.plugin.booking.domain.bed.command.ReserveBedCommand;
import com.zxplat.plugin.booking.domain.masterdata.result.BedLocationSummaryDto;
import com.zxplat.plugin.booking.domain.masterdata.model.MasterDataOption;
import com.zxplat.plugin.booking.domain.appointment.command.CancelAppointmentCommand;
import com.zxplat.plugin.booking.domain.appointment.command.CreateAppointmentCommand;
import com.zxplat.plugin.booking.domain.appointment.entity.Appointment;
import com.zxplat.plugin.booking.domain.appointment.queries.model.AppointmentBookingDetailModel;
import com.zxplat.plugin.booking.domain.bed.entity.BedReservationExt;
import com.zxplat.plugin.booking.domain.share.AppointmentKindConstants;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 床位预约应用层映射器。
 */
@Component
public class BedBookingMapper {

    private static final String DEFAULT_ORIGIN_CHANNEL = "WINDOW";
    private static final String STATUS_RESERVED = "RESERVED";
    private static final String STATUS_NOT_IN_USE = "NOT_IN_USE";
    private static final String STATUS_IN_USE = "IN_USE";

    /**
     * 病区主数据 → 病区树响应。
     *
     * @param wards 病区主数据选项列表
     * @return 病区树响应
     */
    public LoadWardTreeResponse toLoadWardTreeResponse(List<MasterDataOption> wards) {
        LoadWardTreeResponse response = new LoadWardTreeResponse();
        List<WardTreeNodeDto> nodes = new ArrayList<>();
        if (wards != null) {
            for (MasterDataOption ward : wards) {
                WardTreeNodeDto node = new WardTreeNodeDto();
                node.setSort(ward.getSort());
                node.setValue(ward.getValue());
                node.setLabel(ward.getLabel());
                nodes.add(node);
            }
        }
        response.setWards(nodes);
        return response;
    }

    /**
     * 病区主数据 → 床位预约表单初始化响应。
     *
     * @param wards 病区主数据选项列表
     * @return 床位预约表单初始化响应
     */
    public LoadBedBookingFormInitResponse toLoadBedBookingFormInitResponse(List<MasterDataOption> wards) {
        LoadBedBookingFormInitResponse response = new LoadBedBookingFormInitResponse();
        LoadWardTreeResponse tree = this.toLoadWardTreeResponse(wards);
        if (tree.getWards() != null) {
            for (WardTreeNodeDto node : tree.getWards()) {
                OptionDto option = new OptionDto();
                option.setValue(node.getValue());
                option.setLabel(node.getLabel());
                response.getWards().add(option);
            }
        }
        return response;
    }

    /**
     * 构建日期区间表头（含起止日）。
     *
     * @param rangeStart 区间起始日期
     * @param rangeEnd 区间结束日期
     * @return 日期字符串列表
     */
    public List<String> buildDateHeaders(LocalDate rangeStart, LocalDate rangeEnd) {
        List<String> dates = new ArrayList<>();
        LocalDate cursor = rangeStart;
        while (!cursor.isAfter(rangeEnd)) {
            dates.add(cursor.toString());
            cursor = cursor.plusDays(1);
        }
        return dates;
    }

    /**
     * 组装床位矩阵行（床位 × 日期格子）。
     *
     * @param beds 床位摘要列表
     * @param dates 日期表头
     * @param reservations 床位预留扩展列表
     * @param patientNames 患者主键 → 姓名映射
     * @param today 当日日期（用于判定占用状态）
     * @return 床位矩阵行构建结果
     */
    public PageBedMatrixRows buildBedMatrixRows(
        List<BedLocationSummaryDto> beds,
        List<String> dates,
        List<BedReservationExt> reservations,
        Map<Long, String> patientNames,
        LocalDate today) {
        Map<String, BedReservationExt> reservationIndex = new LinkedHashMap<>();
        for (BedReservationExt reservation : reservations) {
            if (reservation == null || reservation.getLocationId() == null || reservation.getIntendedAdmissionDate() == null) {
                continue;
            }
            reservationIndex.put(
                reservation.getLocationId() + ":" + reservation.getIntendedAdmissionDate(),
                reservation);
        }
        List<BedMatrixRowDto> rows = new ArrayList<>();
        for (BedLocationSummaryDto bed : beds) {
            BedMatrixRowDto row = new BedMatrixRowDto();
            row.setBedLocationId(bed.getBedLocationId());
            row.setBedName(bed.getBedName());
            List<BedMatrixCellDto> cells = new ArrayList<>();
            for (String dateText : dates) {
                LocalDate date = LocalDate.parse(dateText);
                BedReservationExt reservation = reservationIndex.get(bed.getBedLocationId() + ":" + date);
                BedMatrixCellDto cell = new BedMatrixCellDto();
                cell.setBedLocationId(bed.getBedLocationId());
                cell.setBedName(bed.getBedName());
                cell.setDate(dateText);
                if (reservation != null) {
                    cell.setOperationalCode(STATUS_RESERVED);
                    cell.setDisplayStatus("预留");
                    cell.setAppointmentId(reservation.getAppointmentId());
                    if (reservation.getPatientId() != null) {
                        cell.setPatientName(patientNames.get(reservation.getPatientId()));
                    }
                } else if (date.equals(today)) {
                    String operational;
                    if (reservation != null) {
                        operational = STATUS_RESERVED;
                    } else if (bed.getOperationalEnum() != null) {
                        operational = bed.getOperationalEnum();
                    } else {
                        operational = STATUS_NOT_IN_USE;
                    }
                    cell.setOperationalCode(operational);
                    cell.setDisplayStatus(this.toDisplayStatus(operational));
                } else {
                    cell.setOperationalCode(STATUS_NOT_IN_USE);
                    cell.setDisplayStatus("空");
                }
                cells.add(cell);
            }
            row.setCells(cells);
            rows.add(row);
        }
        return new PageBedMatrixRows(rows);
    }

    /**
     * 预留床位请求 → 创建预约命令。
     *
     * @param request 预留床位请求
     * @param operatorId 操作人主键
     * @return 创建预约命令
     */
    public CreateAppointmentCommand toCreateBedAppointmentCommand(ReserveBedRequest request, Long operatorId) {
        CreateAppointmentCommand command = new CreateAppointmentCommand();
        command.setOperatorId(operatorId);
        CreateAppointmentCommand.CreateAppointmentItemCommand item = new CreateAppointmentCommand.CreateAppointmentItemCommand();
        item.setAppointmentKind(AppointmentKindConstants.BED_BOOKING);
        item.setOrganizationId(request.getOrganizationId());
        item.setPatientId(request.getPatientId());
        item.setDestinationLocationId(request.getBedLocationId());
        item.setStartInstant(request.getIntendedAdmissionDate().atStartOfDay());
        item.setEndInstant(request.getIntendedAdmissionDate().plusDays(1).atStartOfDay().minusSeconds(1));
        CreateAppointmentCommand.AppointmentOriginCommand origin = new CreateAppointmentCommand.AppointmentOriginCommand();
        origin.setOriginChannel(DEFAULT_ORIGIN_CHANNEL);
        origin.setOriginOperatorId(operatorId);
        item.setOrigin(origin);
        command.setItem(item);
        return command;
    }

    /**
     * 预留床位请求 → 床位预留扩展实体。
     *
     * @param request 预留床位请求
     * @param appointmentId 预约主键
     * @param wardLocationId 病区位置主键
     * @param operatorId 操作人主键
     * @return 床位预留扩展实体
     */
    public BedReservationExt toBedReservationExt(
        ReserveBedRequest request, Long appointmentId, Long wardLocationId, Long operatorId) {
        BedReservationExt ext = new BedReservationExt();
        ext.setAppointmentId(appointmentId);
        ext.setLocationId(request.getBedLocationId());
        ext.setPatientId(request.getPatientId());
        ext.setWardLocationId(wardLocationId);
        ext.setIntendedAdmissionDate(request.getIntendedAdmissionDate());
        ext.setRemark(request.getCommentText());
        return ext;
    }

    /**
     * 预约实体 → 预留床位响应。
     *
     * @param appointment 预约实体
     * @return 预留床位响应
     */
    public ReserveBedResponse toReserveBedResponse(Appointment appointment) {
        ReserveBedResponse response = new ReserveBedResponse();
        response.setAppointmentId(appointment.getId());
        response.setBusNo(appointment.getBusNo());
        return response;
    }

    /**
     * 床位位置 → 预留床位契约命令。
     *
     * @param bedLocationId 床位位置主键
     * @param operatorId 操作人主键
     * @param reason 预留原因
     * @return 预留床位契约命令
     */
    public ReserveBedCommand toReserveBedCommand(Long bedLocationId, Long operatorId, String reason) {
        ReserveBedCommand command = new ReserveBedCommand();
        command.setOperatorId(operatorId);
        ReserveBedCommand.ReserveBedItemCommand item = new ReserveBedCommand.ReserveBedItemCommand();
        item.setLocationId(bedLocationId);
        item.setReason(reason);
        command.setItems(List.of(item));
        return command;
    }

    /**
     * 床位位置 → 释放床位预留契约命令。
     *
     * @param bedLocationId 床位位置主键
     * @param operatorId 操作人主键
     * @return 释放床位预留契约命令
     */
    public ReleaseBedReservationCommand toReleaseBedReservationCommand(Long bedLocationId, Long operatorId) {
        ReleaseBedReservationCommand command = new ReleaseBedReservationCommand();
        command.setOperatorId(operatorId);
        ReleaseBedReservationCommand.ReleaseBedReservationItemCommand item =
            new ReleaseBedReservationCommand.ReleaseBedReservationItemCommand();
        item.setLocationId(bedLocationId);
        command.setItems(List.of(item));
        return command;
    }

    /**
     * 取消床位预留请求 → 取消预约命令。
     *
     * @param request 取消床位预留请求
     * @param operatorId 操作人主键
     * @return 取消预约命令
     */
    public CancelAppointmentCommand toCancelAppointmentCommand(CancelBedReservationRequest request, Long operatorId) {
        CancelAppointmentCommand command = new CancelAppointmentCommand();
        command.setOperatorId(operatorId);
        CancelAppointmentCommand.CancelAppointmentItemCommand item = new CancelAppointmentCommand.CancelAppointmentItemCommand();
        item.setAppointmentId(request.getAppointmentId());
        item.setReason(request.getReason());
        command.setItem(item);
        return command;
    }

    /**
     * 入院确认请求 → 取消预约命令（默认原因「入院确认」）。
     *
     * @param request 入院确认请求
     * @param operatorId 操作人主键
     * @return 取消预约命令
     */
    public CancelAppointmentCommand toConfirmAdmissionCancelCommand(ConfirmAdmissionRequest request, Long operatorId) {
        CancelBedReservationRequest cancel = new CancelBedReservationRequest();
        cancel.setAppointmentId(request.getAppointmentId());
        if (request.getReason() == null) {
            cancel.setReason("入院确认");
        } else {
            cancel.setReason(request.getReason());
        }
        return this.toCancelAppointmentCommand(cancel, operatorId);
    }

    /**
     * 床位预留扩展 + 预约详情 → 列表行 DTO。
     *
     * @param ext 床位预留扩展
     * @param appointment 预约详情模型
     * @param patientNames 患者主键 → 姓名映射
     * @param bedNames 床位位置主键 → 床位名称映射
     * @return 床位预留列表行 DTO
     */
    public BedReservationListItemDto toBedReservationListItemDto(
        BedReservationExt ext,
        AppointmentBookingDetailModel appointment,
        Map<Long, String> patientNames,
        Map<Long, String> bedNames) {
        BedReservationListItemDto dto = new BedReservationListItemDto();
        if (ext != null) {
            dto.setBedLocationId(ext.getLocationId());
            dto.setBedName(bedNames.get(ext.getLocationId()));
            dto.setPatientId(ext.getPatientId());
            if (ext.getPatientId() != null) {
                dto.setPatientName(patientNames.get(ext.getPatientId()));
            }
            if (ext.getIntendedAdmissionDate() != null) {
                dto.setIntendedAdmissionDate(ext.getIntendedAdmissionDate().toString());
            }
        }
        if (appointment != null) {
            dto.setAppointmentId(appointment.getId());
            dto.setBusNo(appointment.getBusNo());
            dto.setStatusView(appointment.getStatusView());
        }
        return dto;
    }

    /**
     * 从床位预留列表提取患者主键。
     *
     * @param rows 床位预留扩展列表
     * @return 患者主键列表
     */
    public List<Long> collectPatientIds(List<BedReservationExt> rows) {
        List<Long> ids = new ArrayList<>();
        if (rows == null) {
            return ids;
        }
        for (BedReservationExt row : rows) {
            if (row != null && row.getPatientId() != null) {
                ids.add(row.getPatientId());
            }
        }
        return ids;
    }

    /**
     * 从床位预留列表提取床位位置主键。
     *
     * @param rows 床位预留扩展列表
     * @return 床位位置主键列表
     */
    public List<Long> collectBedLocationIds(List<BedReservationExt> rows) {
        List<Long> ids = new ArrayList<>();
        if (rows == null) {
            return ids;
        }
        for (BedReservationExt row : rows) {
            if (row != null && row.getLocationId() != null) {
                ids.add(row.getLocationId());
            }
        }
        return ids;
    }

    /**
     * 床位摘要列表 → 床位位置主键 → 名称索引。
     *
     * @param beds 床位摘要列表
     * @return 床位位置主键 → 床位名称映射
     */
    public Map<Long, String> indexBedNames(List<BedLocationSummaryDto> beds) {
        Map<Long, String> map = new LinkedHashMap<>();
        if (beds == null) {
            return map;
        }
        for (BedLocationSummaryDto bed : beds) {
            if (bed != null && bed.getBedLocationId() != null) {
                map.put(bed.getBedLocationId(), bed.getBedName());
            }
        }
        return map;
    }

    private String toDisplayStatus(String operationalCode) {
        if (STATUS_IN_USE.equals(operationalCode)) {
            return "占用";
        }
        if (STATUS_RESERVED.equals(operationalCode)) {
            return "预留";
        }
        return "空";
    }

    /**
     * 床位矩阵行构建结果。
     */
    public static final class PageBedMatrixRows {
        private final List<BedMatrixRowDto> rows;

        /**
         * @param rows 床位矩阵行列表
         */
        public PageBedMatrixRows(List<BedMatrixRowDto> rows) {
            this.rows = rows;
        }

        /**
         * @return 床位矩阵行列表
         */
        public List<BedMatrixRowDto> getRows() {
            return rows;
        }
    }
}
