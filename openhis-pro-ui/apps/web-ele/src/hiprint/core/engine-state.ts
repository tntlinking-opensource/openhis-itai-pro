let engineInited = false;

/** 打印运行时或设计器任一端完成 hiprint.init 后置位 */
export function isHiprintEngineInited(): boolean {
  return engineInited;
}

/** 标记 hiprint 引擎已 init，避免对端再 init 一次 */
export function markHiprintEngineInited(): void {
  engineInited = true;
}
