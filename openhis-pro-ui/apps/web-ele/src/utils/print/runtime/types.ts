export interface PrintOptions<TSource = unknown> {
  /** 业务场景码 scenarioCode */
  scenarioCode: string;
  /** 业务对象，由注册表 mapper 转为 PrintData */
  source?: TSource;
  /** 已映射好的 PrintData（与 source 二选一） */
  printData?: Record<string, unknown>;
  /** 审计业务引用 */
  bizRef?: Record<string, unknown>;
  copies?: number;
  printer?: string;
  jobName?: string;
  /** 场景展示名（打印机弹窗） */
  scenarioLabel?: string;
  /** 默认 true */
  silent?: boolean;
  /** auto=按偏好自动；always=强制弹窗；never=不弹窗（默认 auto） */
  selectPrinter?: 'always' | 'auto' | 'never';
  /** 伴随/静默打印：失败时不弹排查指引（默认 false） */
  background?: boolean;
}

export interface PrintResult {
  ok: boolean;
  traceId?: string;
  busNo?: string;
  scenarioCode?: string;
  printer?: string;
  copies?: number;
  errorCode?: string;
  errorMessage?: string;
}

export const PRINT_CLIENT_TYPE = 'electron-hiprint';

export const PRINT_ERROR_CODE = {
  CLIENT_OFFLINE: 'CLIENT_OFFLINE',
  INVALID_OPTIONS: 'INVALID_OPTIONS',
  MAPPER_NOT_FOUND: 'MAPPER_NOT_FOUND',
  MISSING_PRINT_JSON: 'MISSING_PRINT_JSON',
  PREPARE_FAILED: 'PREPARE_FAILED',
  PRINT_BUSY: 'PRINT_BUSY',
  PRINT_FAILED: 'PRINT_FAILED',
  PRINTER_NOT_FOUND: 'PRINTER_NOT_FOUND',
  USER_CANCELLED: 'USER_CANCELLED',
} as const;
