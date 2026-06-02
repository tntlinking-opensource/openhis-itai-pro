import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';

// ===================== 类型定义 =====================

/** 设备配置分页查询参数 */
export interface PageEmqConfigListParams {
  where?: string[];
  keyword?: string;
  pageNo?: number;
  pageSize?: number;
  name?: string;
  deviceTypeEnum?: number;
  protocolEnum?: number;
  statusEnum?: number;
}

/** 设备配置项 */
export interface EmqConfigListItem {
  id?: string;
  busNo?: string;
  name?: string;
  deviceTypeEnum?: number;
  deviceTypeEnumText?: string;
  brand?: string;
  modelNumber?: string;
  protocolEnum?: number;
  protocolEnumText?: string;
  ipAddress?: string;
  port?: number;
  localPort?: number;
  connectModeEnum?: number;
  connectModeEnumText?: string;
  orgId?: string;
  orgId_dictText?: string;
  locationId?: string;
  locationId_dictText?: string;
  bedNo?: string;
  statusEnum?: number;
  statusEnum_enumText?: string;
  connectStatusEnum?: number;
  connectStatusEnumText?: string;
  description?: string;
  reportInterval?: number;
  alarmEnabled?: number;
  hl7AppName?: string;
  hl7FacilityName?: string;
  logCount?: number;
  [key: string]: unknown;
}

/** 设备配置分页查询响应 */
export interface PageEmqConfigListResponse {
  content?: EmqConfigListItem[];
  totalElements?: number;
  totalPages?: number;
  [key: string]: unknown;
}

/** 创建设备配置参数 */
export interface CreateEmqConfigListParams {
  name: string;
  deviceTypeEnum: number;
  brand?: string;
  modelNumber?: string;
  protocolEnum: number;
  ipAddress?: string;
  port?: number;
  localPort?: number;
  connectModeEnum?: number;
  orgId?: number;
  locationId?: number;
  bedNo?: string;
  description?: string;
  reportInterval?: number;
  alarmEnabled?: number;
  hl7AppName?: string;
  hl7FacilityName?: string;
  extConfig?: string;
}

/** 更新设备配置参数 */
export interface UpdateEmqConfigListParams extends Partial<CreateEmqConfigListParams> {
  id?: string;
  [key: string]: unknown;
}

/** 设备配置初始化响应 */
export interface EmqConfigListInitResponse {
  [key: string]: unknown;
}

/** 采集日志项 */
export interface EmqConfigLogItem {
  id?: string;
  deviceId?: number;
  deviceName?: string;
  deviceBusNo?: string;
  dataXml?: string;
  vitalSignsJson?: string;
  success?: boolean;
  patientId?: string;
  patientGender?: string;
  birthDate?: string;
  bedNo?: string;
  patientClass?: string;
  visitType?: string;
  admitDate?: string;
  observeTime?: string;
  createdAt?: string;
  [key: string]: unknown;
}

/** 采集日志分页响应 */
export interface PageConfigLogResponse {
  content?: EmqConfigLogItem[];
  totalElements?: number;
  totalPages?: number;
  [key: string]: unknown;
}

// ===================== API 函数 =====================

/** 设备配置分页查询 */
export async function pageEmqConfigListApi(
  params: PageEmqConfigListParams,
): Promise<ApiResponse<PageEmqConfigListResponse>> {
  return requestClient.post<ApiResponse<PageEmqConfigListResponse>>(
    '/emqConfigList/pageEmqConfigList',
    params,
  );
}

/** 获取设备配置详情 */
export async function getEmqConfigListApi(id: string): Promise<ApiResponse<EmqConfigListItem>> {
  return requestClient.post<ApiResponse<EmqConfigListItem>>('/emqConfigList/getEmqConfigList', id);
}

/** 创建设备配置 */
export async function createEmqConfigListApi(
  params: CreateEmqConfigListParams,
): Promise<ApiResponse<EmqConfigListItem>> {
  return requestClient.post<ApiResponse<EmqConfigListItem>>(
    '/emqConfigList/createEmqConfigList',
    params,
  );
}

/** 更新设备配置 */
export async function updateEmqConfigListApi(
  params: UpdateEmqConfigListParams,
): Promise<ApiResponse<EmqConfigListItem>> {
  return requestClient.post<ApiResponse<EmqConfigListItem>>(
    '/emqConfigList/editEmqConfigList',
    params,
  );
}

/** 启用设备配置 */
export async function startEmqConfigListApi(ids: string[]): Promise<ApiResponse<null>> {
  return requestClient.post<ApiResponse<null>>('/emqConfigList/startEmqConfigList', ids);
}

/** 停用设备配置 */
export async function stopEmqConfigListApi(ids: string[]): Promise<ApiResponse<null>> {
  return requestClient.post<ApiResponse<null>>('/emqConfigList/stopEmqConfigList', ids);
}

/** 删除设备配置 */
export async function deleteEmqConfigListApi(id: string): Promise<ApiResponse<null>> {
  return requestClient.post<ApiResponse<null>>('/emqConfigList/deleteEmqConfigList', id);
}

/** 设备配置初始化 */
export async function initEmqConfigListApi(): Promise<ApiResponse<EmqConfigListInitResponse>> {
  return requestClient.post<ApiResponse<EmqConfigListInitResponse>>('/emqConfigList/init', {});
}

/** 设备数据采集（超时60秒，因为Socket连接需要等待设备响应） */
export async function collectDeviceDataApi(id: string): Promise<ApiResponse<any>> {
  return requestClient.post<ApiResponse<any>>('/emqConfigList/collectDeviceData', id, {
    timeout: 60000,
  });
}

/** 采集日志分页查询 */
export async function pageConfigLogApi(params: {
  deviceId: string;
  pageNo: number;
  pageSize: number;
}): Promise<ApiResponse<PageConfigLogResponse>> {
  return requestClient.post<ApiResponse<PageConfigLogResponse>>(
    '/emqConfigList/pageConfigLog',
    params,
  );
}

/** 获取采集日志详情 */
export async function getConfigLogDetailApi(logId: string): Promise<ApiResponse<any>> {
  return requestClient.post<ApiResponse<any>>('/emqConfigList/getConfigLogDetail', logId);
}

// ===================== iMEC（迈瑞）专有类型与 API =====================

/** 生命体征数据 */
export interface ImecVitalSign {
  code: string;
  name: string;
  value: string;
  unit: string;
  referenceRange?: string;
}

/** 波形数据点 */
export interface ImecWaveformSample {
  timestamp: number;
  value: number;
}

/** 波形数据 */
export interface ImecWaveform {
  code: string; // ECG/SpO2/Resp
  name: string;
  samples: ImecWaveformSample[];
  sampleRate: number; // 采样率 Hz
  color?: string;
}

/** 报警级别 */
export type ImecAlarmLevel = 'low' | 'medium' | 'high';

/** 报警类型 */
export type ImecAlarmType = 'physiological' | 'technical';

/** 报警信息 */
export interface ImecAlarm {
  id: string;
  type: ImecAlarmType;
  level: ImecAlarmLevel;
  code: string;
  message: string;
  value?: string;
  unit?: string;
  timestamp: string;
  acknowledged: boolean;
}

/** WebSocket 连接状态 */
export type ImecWsStatus =
  | 'disconnected'
  | 'connecting'
  | 'connected'
  | 'heartbeat'
  | 'reconnecting'
  | 'error';

/** iMEC 实时监护数据（WebSocket 推送） */
export interface ImecMonitorData {
  deviceId: string;
  deviceName?: string;
  brand?: string;
  modelNumber?: string;
  bedNo?: string;
  observeTime?: string;
  /** 患者信息 */
  patientId?: string;
  patientName?: string;
  patientGender?: string;
  birthDate?: string;
  patientClass?: string;
  visitType?: string;
  admitDate?: string;
  /** 生命体征 */
  vitalSigns: ImecVitalSign[];
  /** 波形数据 */
  waveforms: ImecWaveform[];
  /** 报警列表 */
  alarms: ImecAlarm[];
  /** 连接状态 */
  wsStatus?: ImecWsStatus;
}

/** iMEC 连接状态响应 */
export interface ImecConnectionStatus {
  deviceId: string;
  connected: boolean;
  wsStatus: ImecWsStatus;
  lastHeartbeat?: string;
  uptime?: number;
}

/** iMEC 采集日志项 */
export interface ImecConfigLogItem {
  id?: string;
  deviceId?: string;
  deviceName?: string;
  deviceBusNo?: string;
  startTime?: string;
  endTime?: string;
  vitalSignsJson?: string;
  alarmCount?: number;
  patientId?: string;
  patientName?: string;
  bedNo?: string;
  observeTime?: string;
  createdAt?: string;
  [key: string]: unknown;
}

/** iMEC 采集日志分页响应 */
export interface ImecPageConfigLogResponse {
  content?: ImecConfigLogItem[];
  totalElements?: number;
  totalPages?: number;
  [key: string]: unknown;
}

/** iMEC 体征趋势查询参数 */
export interface ImecTrendParams {
  deviceId: string;
  code: string;
  startTime: string;
  endTime: string;
  interval?: number;
}

/** iMEC 体征趋势数据点 */
export interface ImecTrendPoint {
  time: string;
  value: number;
  unit: string;
}

// ===================== iMEC API 函数 =====================

/** 启动 iMEC 长连接采集 */
export async function startImecCollectApi(
  deviceId: string,
): Promise<ApiResponse<ImecConnectionStatus>> {
  return requestClient.post<ApiResponse<ImecConnectionStatus>>('/imec/startCollect', { deviceId });
}

/** 停止 iMEC 长连接采集 */
export async function stopImecCollectApi(deviceId: string): Promise<ApiResponse<null>> {
  return requestClient.post<ApiResponse<null>>('/imec/stopCollect', { deviceId });
}

/** 获取 iMEC 连接状态 */
export async function getImecStatusApi(
  deviceId: string,
): Promise<ApiResponse<ImecConnectionStatus>> {
  return requestClient.post<ApiResponse<ImecConnectionStatus>>('/imec/getStatus', { deviceId });
}

/** 获取 iMEC 最新监护数据（HTTP 回退，非 WebSocket 时使用） */
export async function getImecLatestDataApi(
  deviceId: string,
): Promise<ApiResponse<ImecMonitorData>> {
  return requestClient.post<ApiResponse<ImecMonitorData>>('/imec/getLatestData', { deviceId });
}

/** iMEC 采集日志分页查询 */
export async function pageImecConfigLogApi(params: {
  deviceId: string;
  pageNo: number;
  pageSize: number;
}): Promise<ApiResponse<ImecPageConfigLogResponse>> {
  return requestClient.post<ApiResponse<ImecPageConfigLogResponse>>('/imec/pageConfigLog', params);
}

/** 获取 iMEC 采集日志详情 */
export async function getImecConfigLogDetailApi(
  logId: string,
): Promise<ApiResponse<ImecConfigLogItem>> {
  return requestClient.post<ApiResponse<ImecConfigLogItem>>('/imec/getConfigLogDetail', { logId });
}

/** 确认 iMEC 报警 */
export async function acknowledgeImecAlarmApi(
  deviceId: string,
  alarmId: string,
): Promise<ApiResponse<null>> {
  return requestClient.post<ApiResponse<null>>('/imec/acknowledgeAlarm', { deviceId, alarmId });
}

/** 获取 iMEC 体征趋势数据 */
export async function getImecTrendApi(
  params: ImecTrendParams,
): Promise<ApiResponse<ImecTrendPoint[]>> {
  return requestClient.post<ApiResponse<ImecTrendPoint[]>>('/imec/getTrend', params);
}

/** 注入测试数据（开发调试用，绕过 Socket 通信） */
export async function injectImecTestDataApi(
  deviceId: string,
  hl7Message: string,
): Promise<ApiResponse<ImecMonitorData>> {
  return requestClient.post<ApiResponse<ImecMonitorData>>('/imec/injectTestData', {
    deviceId,
    hl7Message,
  });
}
