import { PRINT_ERROR_CODE } from './types';

export class PrintRuntimeError extends Error {
  readonly code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = 'PrintRuntimeError';
    this.code = code;
  }
}

/** 将本地插件 / 运行时错误码映射为可读文案 */
export function resolvePrintErrorMessage(code: string, fallback?: string): string {
  switch (code) {
    case PRINT_ERROR_CODE.CLIENT_OFFLINE: {
      return '无法连接本机打印客户端';
    }
    case PRINT_ERROR_CODE.MAPPER_NOT_FOUND: {
      return fallback ?? '未配置打印数据映射';
    }
    case PRINT_ERROR_CODE.MISSING_PRINT_JSON: {
      return '打印模板为空，请联系管理员发布模板';
    }
    case PRINT_ERROR_CODE.PREPARE_FAILED: {
      return fallback ?? '获取打印模板失败';
    }
    case PRINT_ERROR_CODE.PRINT_BUSY: {
      return '打印机正忙，请稍后重试';
    }
    case PRINT_ERROR_CODE.PRINTER_NOT_FOUND: {
      return fallback ?? '未找到可用打印机';
    }
    default: {
      return fallback ?? '打印失败';
    }
  }
}
