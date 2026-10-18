/** 住院护士站体温单（nurseStationTemperatureSheet）请求/响应类型 */

import type { OptionDto } from '#/api/inpatient/nurse-station/inpatient-nurse-station.types';

export type TemperatureSheetWindowRange = '14d' | '3d' | '7d';

export type TemperatureSheetTimeSlot = '02' | '06' | '10' | '14' | '18' | '22';

export interface TemperatureSheetDetailItemDto {
  itemCode?: null | string;
  itemValue?: null | string;
  remark?: null | string;
  termCode?: null | string;
}

export interface TemperatureSheetTimeSlotOptionDto {
  code?: null | string;
  displayText?: null | string;
}

export interface TemperatureSheetWindowOptionDto {
  code?: null | string;
  displayText?: null | string;
}

export interface TemperatureSheetTemperaturePointDto {
  recordedAt?: null | string;
  temperature?: null | string;
  vitalSignId?: null | string;
}

export interface TemperatureSheetGridSlotCellDto {
  detailItems?: TemperatureSheetDetailItemDto[] | null;
  /** 实际测量时分 HH:mm，描点按此就近落格 */
  recordTime?: null | string;
  timeSlot?: null | string;
  vitalSignId?: null | string;
}

export interface TemperatureSheetGridRowDto {
  recordDate?: null | string;
  slotCells?: TemperatureSheetGridSlotCellDto[] | null;
}

export interface TemperatureSheetPaginationDto {
  dateFrom?: null | string;
  dateTo?: null | string;
  pageIndex?: null | number;
  totalPages?: null | number;
}

export interface TemperatureSheetDerivedMetaDto {
  hospitalizationDaysByDate?: Record<string, number> | null;
  postoperativeDaysByDate?: Record<string, number> | null;
  weekNoByDate?: Record<string, number> | null;
}

export interface TemperatureSheetFillSlotDto {
  detailItems?: TemperatureSheetDetailItemDto[] | null;
  timeSlot?: null | string;
  vitalSignId?: null | string;
}

export interface LoadNurseStationTemperatureSheetInitResponse {
  defaultWindowRange?: null | string;
  specialEventOptions?: OptionDto[] | null;
  timeSlotOptions?: TemperatureSheetTimeSlotOptionDto[] | null;
  windowOptions?: TemperatureSheetWindowOptionDto[] | null;
}

export interface GetNurseStationTemperatureSheetDataRequest {
  encounterId: string;
  focusRecordDate?: string;
  pageIndex?: number;
  patientId: string;
  windowRange: TemperatureSheetWindowRange;
}

export interface GetNurseStationTemperatureSheetDataResponse {
  /** 死亡出院：图表竖排显示死亡 */
  deathDischarge?: boolean | null;
  derivedMeta?: TemperatureSheetDerivedMetaDto | null;
  fillSlots?: TemperatureSheetFillSlotDto[] | null;
  gridRows?: TemperatureSheetGridRowDto[] | null;
  pagination?: TemperatureSheetPaginationDto | null;
  temperaturePoints?: TemperatureSheetTemperaturePointDto[] | null;
}

export interface SaveNurseStationTemperatureSheetAtRecordedTimeRequest {
  detailItems?: TemperatureSheetDetailItemDto[] | null;
  encounterId: string;
  patientId: string;
  recordDate: string;
  timeSlot: string;
  vitalSignId?: null | string;
}

export interface SaveNurseStationTemperatureSheetAtRecordedTimeResponse {
  busNo?: null | string;
  nextTimeSlot?: null | string;
  vitalSignId?: null | string;
}

export interface DeleteNurseStationTemperatureSheetRequest {
  encounterId: string;
  patientId: string;
  vitalSignId: string;
}

export interface DeleteNurseStationTemperatureSheetResponse {
  success?: boolean;
}
