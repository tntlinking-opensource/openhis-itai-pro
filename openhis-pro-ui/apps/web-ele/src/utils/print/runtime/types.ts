import type { InvFormPaperKind } from '../form-print-preference';

export interface PrintTransformContext {
  formPaperKind?: InvFormPaperKind;
}

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
  /**
   * 针式联单：把同一份 PrintData 连打 N 页，不用打印机驱动 copies。
   * 避免连续纸多走纸、夹空白页。
   */
  repeatPrintData?: boolean;
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
  /**
   * prepare 拿到库模板后、送 hiprint 前改 PrintJson（如报表动态列、库房纸张）。
   * 入参为深拷贝，可就地改；返回值作为最终 PrintJson。
   */
  transformPrintJson?: (
    printJson: Record<string, unknown>,
    ctx?: PrintTransformContext,
  ) => Record<string, unknown>;
  /** true 时打开屏幕预览，不走静默打印与审计 */
  preview?: boolean;
  /** 库房单据当前纸张（三联单 / A4） */
  formPaperKind?: InvFormPaperKind;
  /** 预览标题栏展示纸张切换，切换后按所选纸张重渲染 */
  showFormPaperKind?: boolean;
  /** 库房打印：打印机弹窗同时选纸张 */
  showPaperKind?: boolean;
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
