declare module 'vue-plugin-hiprint' {
  export const hiprint: {
    init: (options?: Record<string, unknown>) => void;
    PrintElementTypeManager: {
      buildByHtml: (elements: unknown) => void;
    };
    PrintTemplate: new (options: Record<string, unknown>) => unknown;
    setConfig: (config: Record<string, unknown>) => void;
    /** 按 tid 替换/修补 provider 中的元素类型（勿用 named export，Vite CJS 抽不齐） */
    updateElementType: (
      tid: string,
      patch: (type: Record<string, unknown>) => Record<string, unknown>,
    ) => Record<string, unknown> | undefined;
  };
  export const defaultElementTypeProvider: new () => unknown;
  export const disAutoConnect: () => void;
  export const autoConnect: (callback?: (connected: boolean, msg?: string) => void) => void;
  export const hiPrintPlugin: {
    disAutoConnect: () => void;
  };
}
