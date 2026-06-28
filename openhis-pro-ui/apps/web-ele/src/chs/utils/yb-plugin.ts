/**
 * 医保读卡本地插件（OpenHIS.Win，默认 http://127.0.0.1:5000）。
 * 9001 签到：浏览器优先取 MAC/IP；失败时由服务端 sign_mac / yb_plugin_url 回退。
 */
const DEFAULT_PLUGIN_DATA_URL = 'http://127.0.0.1:5000/api/data/';
export const DEFAULT_CARD_READER_HOST = 'ddjk.jlhs.gov.cn';
export const DEFAULT_CARD_READER_PORT = 20215;
const PLUGIN_FETCH_TIMEOUT_MS = 8_000;
const PLUGIN_SIGN_MAC_TIMEOUT_MS = 65_000;
const PLUGIN_HEALTH_TIMEOUT_MS = 3_000;

export const YB_PLUGIN_FUNCTION_ID = {
  SOCIAL_CARD: 1,
  ID_CARD: 2,
  EC_QUERY: 3,
  DSD_CARD: 4,
  SIGN_MAC: 5,
  LOCAL_IP: 6,
} as const;

export interface YbPluginInitInput {
  FunctionId: number;
  IP?: string;
  PORT?: number;
  TIMEOUT?: number;
  LOG_PATH?: string;
  SFZ_DRIVER_TYPE?: number;
}

/**
 * 国家局电子凭证用码业务类型（code_biz_type，5 位）。
 * 见定点机构接口规范字典 9.1；02xxx 为药店，非住院。
 */
export const YB_EC_BUSINESS_TYPE = {
  /** 门诊挂号 */
  OUTPATIENT_REGISTER: '01101',
  /** 入院登记（住院登记） */
  INPATIENT_ADMISSION: '01103',
} as const;

/** FunctionId=3 医保电子凭证（ec.query） */
export interface YbEcVoucherQueryInput extends YbPluginInitInput {
  url: string;
  orgId: string;
  businessType?: string;
  businessInfo?: string;
  operatorId?: string;
  operatorName?: string;
  officeId?: string;
  officeName?: string;
  deviceType?: string;
}

export interface SignEndpointPayload {
  mac: string;
  ip: string;
}

/** 社保卡读卡结果（HeaSec ReadCardBas） */
export interface YbSocialCardReadResult {
  /** 发卡地区划（pipe 第 0 段）→ 1101 路由 / 费用性质推荐 */
  issuingAreaCode: string;
  /** 社会保障卡卡号 → mdtrt_cert_no */
  mdtrtCertNo: string;
  /** 卡识别码 → card_sn */
  cardSn: string;
  /** 社会保障号码 → certno */
  certno: string;
  psnName: string;
  /** 卡复位信息（历史字节，最多 26 位） */
  cardRest: string;
  /** ReadCardBas 业务扩展区（跨省 03 时部分平台要求 Base64 卡串） */
  busiCardInfo?: string;
}

/** 1101 就诊凭证（由社保卡读卡结果解析） */
export interface SocialCard1101Credential {
  cardSn?: string;
  certno?: string;
  mdtrtCertNo: string;
  mdtrtCertType: string;
  psnCertType: string;
}

function normalizeAdmdvsDigits(admdvs?: string): string {
  return String(admdvs ?? '').replace(/\D/g, '');
}

/** 吉林省省内发卡（含长春本地、省内异地）：1101 应走 02 身份证，勿用 03 卡号（东软会 Base64 解密卡串）。 */
export function isJilinInProvinceIssuingArea(issuingAreaCode?: string): boolean {
  const insu = normalizeAdmdvsDigits(issuingAreaCode);
  if (insu.length < 2) {
    return true;
  }
  return insu.startsWith('22');
}

/**
 * 社保卡 → 1101 凭证。
 * 省内/本地：mdtrt_cert_type=02 + 身份证号；跨省：03 + 卡号 + card_sn。
 */
export function resolveSocialCard1101Credential(
  card: YbSocialCardReadResult,
): SocialCard1101Credential {
  const certno = (card.certno ?? '').trim().toUpperCase();
  const inProvince = isJilinInProvinceIssuingArea(card.issuingAreaCode);

  if (certno && inProvince) {
    return {
      mdtrtCertType: '02',
      mdtrtCertNo: certno,
      certno,
      psnCertType: '01',
    };
  }

  return {
    mdtrtCertType: '03',
    mdtrtCertNo: card.mdtrtCertNo.trim(),
    cardSn: card.cardSn?.trim() || undefined,
    certno: certno || undefined,
    psnCertType: '01',
  };
}

/** 医保电子凭证（ec.query）解析结果 */
export interface YbEcVoucherReadResult {
  ecToken?: string;
  idNo?: string;
  userName?: string;
  idType?: string;
  insuOrg?: string;
}

function tryParseJsonObject(raw: string): Record<string, unknown> | null {
  const trimmed = raw.trim();
  if (!trimmed.startsWith('{')) {
    return null;
  }
  try {
    const parsed = JSON.parse(trimmed) as unknown;
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      return parsed as Record<string, unknown>;
    }
  } catch {
    return null;
  }
  return null;
}

function pickRecordString(record: Record<string, unknown>, ...keys: string[]): string | undefined {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === 'string' && value.trim()) {
      return value.trim();
    }
    if (typeof value === 'number' && Number.isFinite(value)) {
      return String(value);
    }
  }
  return undefined;
}

function unwrapEcVoucherDataRecord(raw: string): Record<string, unknown> {
  let record = tryParseJsonObject(raw);
  if (!record) {
    return {};
  }
  for (let depth = 0; depth < 4; depth += 1) {
    if (
      pickRecordString(record, 'ecToken', 'ec_token', 'ecQrCode', 'ec_qr_code', 'idNo', 'id_no')
    ) {
      break;
    }
    const nestedRaw = record.data;
    if (typeof nestedRaw === 'string' && nestedRaw.trim()) {
      const nested = tryParseJsonObject(nestedRaw);
      if (nested) {
        record = nested;
        continue;
      }
    }
    if (nestedRaw && typeof nestedRaw === 'object' && !Array.isArray(nestedRaw)) {
      record = nestedRaw as Record<string, unknown>;
      continue;
    }
    break;
  }
  return record;
}

export function parseEcVoucherPlatformError(raw: string): string | undefined {
  const record = unwrapEcVoucherDataRecord(raw);
  const innerCode = record.code ?? record.Code;
  if (innerCode !== undefined && innerCode !== 0 && innerCode !== '0' && innerCode !== '0000') {
    return pickRecordString(record, 'message', 'msg', 'err_msg');
  }
  return undefined;
}

/** 解析 ec.query 返回（支持多层 code/data 包装） */
export function parseEcVoucherReadResult(raw: string): YbEcVoucherReadResult {
  const record = unwrapEcVoucherDataRecord(raw);
  const result = {
    ecToken: pickRecordString(
      record,
      'ecToken',
      'ec_token',
      'ectoken',
      'token',
      'ecQrCode',
      'ec_qr_code',
    ),
    idNo: pickRecordString(
      record,
      'idNo',
      'id_no',
      'masterIdNo',
      'master_id_no',
      'certno',
      'certNo',
      'idCard',
      'id_card',
      'psn_cert_no',
    ),
    userName: pickRecordString(
      record,
      'userName',
      'user_name',
      'username',
      'masterUserName',
      'master_user_name',
      'psnName',
      'psn_name',
      'name',
    ),
    idType: pickRecordString(
      record,
      'idType',
      'id_type',
      'masterIdType',
      'master_id_type',
      'psn_cert_type',
    ),
    insuOrg: pickRecordString(record, 'insuOrg', 'insu_org', 'insuplcAdmdvs', 'insuplc_admdvs'),
  };
  if (!result.ecToken && !result.idNo) {
    const trimmed = raw.trim();
    if (trimmed && !trimmed.startsWith('{')) {
      result.ecToken = trimmed;
    }
  }
  return result;
}

/** 电子凭证 → 1101 凭证（优先 01+ecToken，无 token 时退化为 02+身份证号） */
export function resolveEcVoucher1101Credential(
  ec: YbEcVoucherReadResult,
): SocialCard1101Credential | null {
  const ecToken = ec.ecToken?.trim();
  const certno = ec.idNo?.trim().toUpperCase();
  const psnCertType = ec.idType?.trim() || '01';
  if (ecToken) {
    return {
      mdtrtCertType: '01',
      mdtrtCertNo: ecToken,
      certno: certno || undefined,
      psnCertType,
    };
  }
  if (certno) {
    return {
      mdtrtCertType: '02',
      mdtrtCertNo: certno,
      certno,
      psnCertType,
    };
  }
  return null;
}

export type YbPluginErrorCode = 'NETWORK' | 'HTTP' | 'EMPTY' | 'DLL' | 'PARSE';

export class YbPluginError extends Error {
  readonly code: YbPluginErrorCode;

  readonly detail?: string;

  constructor(message: string, code: YbPluginErrorCode, detail?: string) {
    super(message);
    this.name = 'YbPluginError';
    this.code = code;
    this.detail = detail;
  }
}

export interface ResolveSignEndpointResult {
  fromPlugin: boolean;
  /** 健康检查是否通过（与 MAC 获取失败区分） */
  pluginOnline: boolean;
  mac?: string;
  ip?: string;
  /** 插件不可用时的简要原因（用于页面提示） */
  fallbackReason?: string;
}

/** 读卡插件数据接口地址（可通过 VITE_GLOB_YB_PLUGIN_URL 覆盖） */
export function getYbPluginDataUrl(): string {
  const fromEnv = import.meta.env.VITE_GLOB_YB_PLUGIN_URL;
  if (typeof fromEnv === 'string' && fromEnv.trim()) {
    return normalizeDataUrl(fromEnv.trim());
  }
  return DEFAULT_PLUGIN_DATA_URL;
}

/** 医保电子凭证接口地址（VITE_GLOB_YB_EC_VOUCHER_URL） */
export function getYbEcVoucherUrl(): string {
  const fromEnv = import.meta.env.VITE_GLOB_YB_EC_VOUCHER_URL;
  return typeof fromEnv === 'string' ? fromEnv.trim() : '';
}

/** 医保电子凭证定点机构编码（VITE_GLOB_YB_EC_ORG_ID，通常与 fixmedins_code 一致） */
export function getYbEcOrgId(): string {
  const fromEnv = import.meta.env.VITE_GLOB_YB_EC_ORG_ID;
  return typeof fromEnv === 'string' ? fromEnv.trim() : '';
}

/** 健康检查地址（OpenHIS.Win GET /api/health） */
export function getYbPluginHealthUrl(dataUrl = getYbPluginDataUrl()): string {
  try {
    const origin = new URL(dataUrl).origin;
    return `${origin}/api/health`;
  } catch {
    return 'http://127.0.0.1:5000/api/health';
  }
}

function normalizeDataUrl(url: string): string {
  if (url.endsWith('/api/data') || url.endsWith('/api/data/')) {
    return url.endsWith('/') ? url : `${url}/`;
  }
  if (!url.endsWith('/')) {
    return `${url}/`;
  }
  return url;
}

async function fetchWithTimeout(
  url: string,
  init: RequestInit,
  timeoutMs: number,
): Promise<Response> {
  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { ...init, signal: controller.signal });
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw new YbPluginError(
        `连接读卡插件超时（${Math.round(timeoutMs / 1000)} 秒）`,
        'NETWORK',
        url,
      );
    }
    throw error;
  } finally {
    window.clearTimeout(timer);
  }
}

/** 探测 OpenHIS.Win 是否在线 */
export async function isYbPluginOnline(pluginUrl = getYbPluginDataUrl()): Promise<boolean> {
  try {
    const response = await fetchWithTimeout(
      getYbPluginHealthUrl(pluginUrl),
      { method: 'GET' },
      PLUGIN_HEALTH_TIMEOUT_MS,
    );
    return response.ok;
  } catch {
    return false;
  }
}

/** OpenHIS.Win 未启动时的统一提示文案 */
export function formatYbPluginOfflineMessage(pluginUrl = getYbPluginDataUrl()): string {
  try {
    const origin = new URL(getYbPluginHealthUrl(pluginUrl)).origin;
    return `本机 OpenHIS.Win 未启动，请先启动托盘程序（${origin}）`;
  } catch {
    return '本机 OpenHIS.Win 未启动，请先启动托盘程序（默认 http://127.0.0.1:5000）';
  }
}

/** 将异常转为面向用户的简短说明 */
export function formatYbPluginError(error: unknown, pluginUrl = getYbPluginDataUrl()): string {
  if (error instanceof YbPluginError) {
    return enrichHeaSecPluginError(error.message);
  }
  if (error instanceof TypeError) {
    return `无法连接 ${pluginUrl}，请确认 OpenHIS.Win 已启动并监听 5000 端口`;
  }
  if (error instanceof Error) {
    return enrichHeaSecPluginError(error.message);
  }
  return '读卡插件调用失败';
}

/** HeaSecReadInfo ReadCardBas / ReadSFZ 常见返回码说明（吉林社保卡 SDK） */
const HEASEC_RETURN_CODE_HINT: Record<number, string> = {
  [-1]: '卡类型不对',
  [-2]: '无卡，请将社保卡插入读卡器',
  [-3]: '有卡未上电',
  [-4]: '卡无应答',
  [-5]: '加载动态库失败，请检查读卡器驱动及 SSSE32.dll、SSCardDriver.dll 是否已部署到 OpenHIS.Win 目录',
  [-11]: '读卡器连接错',
  [-12]: '未建立连接',
  [-13]: '动态库不支持该命令',
  [-14]: '命令参数错',
  [-2201]: '无 PSAM 卡（三代社保卡读基本信息需要 PSAM 卡）',
  [-2202]: 'PSAM 卡算法不支持',
  [-2203]: 'PSAM 卡内缺少 RKSSSE 密钥',
};

/** NationECCode.dll 电子凭证常见返回码（与社保卡 SDK 含义不同） */
const NATION_EC_RETURN_CODE_HINT: Record<string, string> = {
  '-1': '电子凭证动态库调用失败',
  '-2': '用户取消扫码',
  '-3': '电子凭证平台调用失败（多为本机无法连接医保电子凭证服务，少数为入参错误）',
  '-4': '网络异常，无法连接电子凭证服务',
  '-5': '电子凭证服务地址不可达',
};

function parseNationEcOutMessage(message: string): string | undefined {
  const jsonMatch =
    message.match(/[；;]\s*(\{[\s\S]*\})\s*(?:（|$)/) ??
    message.match(/(\{[\s\S]*"message"[\s\S]*\})/);
  if (!jsonMatch?.[1]) {
    return undefined;
  }
  try {
    const payload = JSON.parse(jsonMatch[1]) as { message?: string };
    const inner = String(payload.message ?? '').trim();
    return inner || undefined;
  } catch {
    return undefined;
  }
}

function lookupNationEcHint(returnCode: string, outMessage?: string): string | undefined {
  if (outMessage?.includes('网络连接失败')) {
    const url = getYbEcVoucherUrl();
    return url
      ? `本机 OpenHIS.Win 无法连接电子凭证服务（${url}），请检查专网/防火墙或联系信息科`
      : '本机 OpenHIS.Win 无法连接电子凭证服务，请检查 VITE_GLOB_YB_EC_VOUCHER_URL 与网络';
  }
  const text = returnCode.trim();
  return NATION_EC_RETURN_CODE_HINT[text];
}

/** 为读卡/电子凭证错误补充可读说明（幂等） */
export function enrichHeaSecPluginError(message: string): string {
  const outMessage = parseNationEcOutMessage(message);

  const ecMatch = message.match(/返回码[：:]\s*(-?\d+)/);
  if (ecMatch) {
    const ecHint = lookupNationEcHint(ecMatch[1] ?? '', outMessage ?? message);
    if (ecHint && !message.includes(ecHint)) {
      const hintStem = ecHint.split('（')[0]?.trim();
      if (hintStem && message.includes(hintStem)) {
        return message;
      }
      if (outMessage && message.includes(outMessage)) {
        return `${message}（${ecHint}）`;
      }
      return `${message}（${ecHint}）`;
    }
    if (outMessage && outMessage !== '网络连接失败') {
      return message;
    }
  }

  if (message.includes('网络连接失败')) {
    const url = getYbEcVoucherUrl();
    const hint = url
      ? `本机 OpenHIS.Win 无法连接电子凭证服务（${url}），请检查专网/防火墙`
      : '本机 OpenHIS.Win 无法连接电子凭证服务';
    if (!message.includes(hint)) {
      return `${message}（${hint}）`;
    }
  }

  const match = message.match(/返回值:\s*(-?\d+)/);
  if (!match) {
    return message;
  }
  const code = Number.parseInt(match[1] ?? '', 10);
  const hint = HEASEC_RETURN_CODE_HINT[code];
  if (!hint) {
    return message;
  }
  // 插件侧已在「返回值: -N（…）」中附带说明时不再重复追加
  if (/返回值:\s*-?\d+（[^）]+）/.test(message)) {
    return message;
  }
  if (message.includes(hint)) {
    return message;
  }
  const hintStem = hint.split('（')[0]?.trim();
  if (hintStem && message.includes(hintStem)) {
    return message;
  }
  return `${message}（${hint}）`;
}

const CARD_REST_MAX_LEN = 26;

function pickJsonString(json: Record<string, unknown>, ...keys: string[]): string {
  for (const key of keys) {
    const value = json[key];
    if (typeof value === 'string' && value.trim()) {
      return value.trim();
    }
  }
  return '';
}

/** 卡复位信息仅取历史字节（国家平台 CARD_REST 最大 26 位）。 */
export function truncateCardRestInfo(value: string): string {
  const trimmed = value.trim();
  if (!trimmed || trimmed.length <= CARD_REST_MAX_LEN) {
    return trimmed;
  }
  return trimmed.slice(-CARD_REST_MAX_LEN);
}

/**
 * 社保卡 pipe 字段（与 SSCardDriver / SiCore 一致）：
 * 0区划|1证件号(certno)|2卡号(mdtrt_cert_no)|3识别码(card_sn)|4姓名|5复位信息|…
 * 1101 使用 parts[2] + parts[3] + parts[1]，不复位信息当卡号。
 */
function parseSocialCardPipe(text: string, busiCardInfo = ''): YbSocialCardReadResult {
  const normalized = text.endsWith('|') ? text.slice(0, -1) : text;
  const parts = normalized.split('|');
  if (parts.length < 6) {
    throw new YbPluginError('社保卡读卡数据项不足', 'PARSE');
  }
  const mdtrtCertNo = (parts[2] ?? '').trim();
  if (!mdtrtCertNo) {
    throw new YbPluginError('读卡未返回社会保障卡卡号', 'EMPTY');
  }
  return {
    issuingAreaCode: (parts[0] ?? '').trim(),
    mdtrtCertNo,
    cardSn: (parts[3] ?? '').trim(),
    certno: (parts[1] ?? '').trim(),
    psnName: (parts[4] ?? '').trim(),
    cardRest: truncateCardRestInfo(parts[5] ?? ''),
    busiCardInfo: busiCardInfo.trim() || undefined,
  };
}

function parseSocialCardJson(json: Record<string, unknown>): YbSocialCardReadResult {
  const dataField = pickJsonString(json, 'data', 'Data');
  if (dataField.includes('|')) {
    return parseSocialCardPipe(dataField, pickJsonString(json, 'BusiCardInfo', 'busiCardInfo'));
  }

  const mdtrtCertNo = pickJsonString(json, 'CardNumber', 'cardNumber');
  if (!mdtrtCertNo) {
    throw new YbPluginError('读卡未返回社会保障卡卡号', 'EMPTY');
  }
  return {
    issuingAreaCode: pickJsonString(json, 'IssuingAreaCode', 'issuingAreaCode'),
    mdtrtCertNo,
    cardSn: pickJsonString(json, 'CardIdentificationCode', 'cardIdentificationCode'),
    certno: pickJsonString(json, 'SocialSecurityNumber', 'socialSecurityNumber'),
    psnName: pickJsonString(json, 'Name', 'name'),
    cardRest: truncateCardRestInfo(pickJsonString(json, 'CardResetInfo', 'cardResetInfo')),
    busiCardInfo: pickJsonString(json, 'BusiCardInfo', 'busiCardInfo') || undefined,
  };
}

function parseSocialCardResponse(text: string): YbSocialCardReadResult {
  const trimmed = text.trim();
  if (!trimmed) {
    throw new YbPluginError('读卡未返回有效信息', 'EMPTY');
  }
  if (!trimmed.startsWith('{')) {
    return parseSocialCardPipe(trimmed);
  }

  let json: Record<string, unknown>;
  try {
    json = JSON.parse(trimmed) as Record<string, unknown>;
  } catch {
    throw new YbPluginError('读卡插件返回了无法解析的 JSON', 'PARSE');
  }

  const errText =
    (typeof json.error === 'string' && json.error) ||
    (typeof json.ErrorMessage === 'string' && json.ErrorMessage) ||
    (typeof json.message === 'string' && json.message && json.code !== 0 && json.code !== '0'
      ? json.message
      : '');

  if (errText) {
    throw new YbPluginError(`读卡插件：${errText}`, 'DLL');
  }

  const code = json.Code ?? json.code;
  if (code !== undefined && code !== 0 && code !== '0') {
    const msg =
      (typeof json.ErrorMessage === 'string' && json.ErrorMessage) ||
      (typeof json.message === 'string' && json.message) ||
      '调用失败';
    throw new YbPluginError(`读卡插件：${msg}`, 'DLL');
  }

  return parseSocialCardJson(json);
}

function parsePluginPlainText(text: string, functionId: number): string {
  const trimmed = text.trim();
  if (!trimmed || trimmed === ' ') {
    throw new YbPluginError(
      functionId === YB_PLUGIN_FUNCTION_ID.SIGN_MAC ? '未获取到签到 MAC' : '未获取到本机 IP',
      'EMPTY',
    );
  }

  if (!trimmed.startsWith('{')) {
    return trimmed;
  }

  let json: Record<string, unknown>;
  try {
    json = JSON.parse(trimmed) as Record<string, unknown>;
  } catch {
    throw new YbPluginError('读卡插件返回了无法解析的 JSON', 'PARSE');
  }

  const errText =
    (typeof json.error === 'string' && json.error) ||
    (typeof json.ErrorMessage === 'string' && json.ErrorMessage) ||
    (typeof json.message === 'string' && json.message && json.code !== 0 && json.code !== '0'
      ? json.message
      : '');

  if (errText) {
    throw new YbPluginError(`读卡插件：${errText}`, 'DLL');
  }

  const code = json.Code ?? json.code;
  if (code !== undefined && code !== 0 && code !== '0') {
    const msg =
      (typeof json.ErrorMessage === 'string' && json.ErrorMessage) ||
      (typeof json.message === 'string' && json.message) ||
      '调用失败';
    throw new YbPluginError(`读卡插件：${msg}`, 'DLL');
  }

  if (functionId === YB_PLUGIN_FUNCTION_ID.EC_QUERY) {
    if (json.data && typeof json.data === 'object' && !Array.isArray(json.data)) {
      return JSON.stringify(json.data);
    }
  }

  if (typeof json.data === 'string' && json.data.trim()) {
    return json.data.trim();
  }

  throw new YbPluginError('读卡插件未返回有效的 MAC/IP', 'EMPTY');
}

/** 调用本地医保读卡插件 */
export async function invokeYbPlugin(
  data: YbEcVoucherQueryInput | YbPluginInitInput,
  pluginUrl = getYbPluginDataUrl(),
  timeoutMs = PLUGIN_FETCH_TIMEOUT_MS,
): Promise<string> {
  const url = normalizeDataUrl(pluginUrl);
  let response: Response;
  try {
    response = await fetchWithTimeout(
      url,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data),
      },
      timeoutMs,
    );
  } catch (error) {
    if (error instanceof YbPluginError) {
      throw error;
    }
    throw new YbPluginError(formatYbPluginError(error, url), 'NETWORK', url);
  }

  const text = await response.text();
  if (!response.ok) {
    throw new YbPluginError(
      `读卡插件 HTTP ${response.status}`,
      'HTTP',
      text.slice(0, 200) || undefined,
    );
  }

  return parsePluginPlainText(text, data.FunctionId);
}

/** 读社保卡并解析 1101 凭证（省内 02 / 跨省 03） */
export async function invokeSocialCardRead(
  data: YbPluginInitInput,
  pluginUrl = getYbPluginDataUrl(),
  timeoutMs = PLUGIN_FETCH_TIMEOUT_MS,
): Promise<YbSocialCardReadResult> {
  const url = normalizeDataUrl(pluginUrl);
  let response: Response;
  try {
    response = await fetchWithTimeout(
      url,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...data, FunctionId: YB_PLUGIN_FUNCTION_ID.SOCIAL_CARD }),
      },
      timeoutMs,
    );
  } catch (error) {
    if (error instanceof YbPluginError) {
      throw error;
    }
    throw new YbPluginError(formatYbPluginError(error, url), 'NETWORK', url);
  }

  const text = await response.text();
  if (!response.ok) {
    throw new YbPluginError(
      `读卡插件 HTTP ${response.status}`,
      'HTTP',
      text.slice(0, 200) || undefined,
    );
  }

  return parseSocialCardResponse(text);
}

/** 获取 9001 签到 mac/ip（FunctionId 5/6） */
export async function fetchSignEndpointFromPlugin(
  host = DEFAULT_CARD_READER_HOST,
  port = DEFAULT_CARD_READER_PORT,
  pluginUrl = getYbPluginDataUrl(),
): Promise<SignEndpointPayload> {
  const mac = await invokeYbPlugin(
    {
      FunctionId: YB_PLUGIN_FUNCTION_ID.SIGN_MAC,
      IP: host,
      PORT: port,
      TIMEOUT: 60,
      LOG_PATH: 'C:/neu_log/',
      SFZ_DRIVER_TYPE: 0,
    },
    pluginUrl,
    PLUGIN_SIGN_MAC_TIMEOUT_MS,
  );
  const ip = await invokeYbPlugin({ FunctionId: YB_PLUGIN_FUNCTION_ID.LOCAL_IP }, pluginUrl);
  return { mac, ip };
}

/**
 * 签到前解析终端 MAC/IP：优先本机插件；失败则交给服务端（sign_mac / 服务端调插件）。
 */
export async function resolveSignEndpointForSignIn(options?: {
  pluginUrl?: string;
  cardReaderHost?: string;
  cardReaderPort?: number;
}): Promise<ResolveSignEndpointResult> {
  const pluginUrl = options?.pluginUrl ?? getYbPluginDataUrl();

  const online = await isYbPluginOnline(pluginUrl);
  if (!online) {
    return {
      fromPlugin: false,
      pluginOnline: false,
      fallbackReason: `OpenHIS.Win 未响应（${getYbPluginHealthUrl(pluginUrl)}）`,
    };
  }

  try {
    const endpoint = await fetchSignEndpointFromPlugin(
      options?.cardReaderHost ?? DEFAULT_CARD_READER_HOST,
      options?.cardReaderPort ?? DEFAULT_CARD_READER_PORT,
      pluginUrl,
    );
    return {
      fromPlugin: true,
      pluginOnline: true,
      mac: endpoint.mac,
      ip: endpoint.ip,
    };
  } catch (error) {
    return {
      fromPlugin: false,
      pluginOnline: true,
      fallbackReason: formatYbPluginError(error, pluginUrl),
    };
  }
}
