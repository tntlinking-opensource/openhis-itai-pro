declare module 'vue-plugin-hiprint' {
  export const hiprint: {
    init: (options?: Record<string, unknown>) => void;
    PrintElementTypeManager: {
      buildByHtml: (elements: unknown) => void;
    };
    PrintTemplate: new (options: Record<string, unknown>) => unknown;
    setConfig: (config: Record<string, unknown>) => void;
  };
  export const defaultElementTypeProvider: new () => unknown;
  export const disAutoConnect: () => void;
  export const autoConnect: (callback?: (connected: boolean, msg?: string) => void) => void;
  export const hiPrintPlugin: {
    disAutoConnect: () => void;
  };
}
