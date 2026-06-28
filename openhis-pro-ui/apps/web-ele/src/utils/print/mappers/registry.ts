import type { PrintData, PrintMapper } from './types';

const registry = new Map<string, PrintMapper>();

/** 注册 scenarioCode 对应的 PrintData 映射函数 */
export function registerPrintMapper<TSource>(scenarioCode: string, mapper: PrintMapper<TSource>) {
  registry.set(scenarioCode, mapper as PrintMapper);
}

/** 按 scenarioCode 将业务对象转为模板 PrintData */
export function resolvePrintData(scenarioCode: string, source: unknown): PrintData {
  const mapper = registry.get(scenarioCode);
  if (!mapper) {
    throw new Error(`未注册打印映射：${scenarioCode}`);
  }
  return mapper(source);
}

/** 是否已注册 scenarioCode 映射 */
export function hasPrintMapper(scenarioCode: string): boolean {
  return registry.has(scenarioCode);
}
