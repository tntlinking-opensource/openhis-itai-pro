/**
 * Hiprint 全局类型声明
 */

declare global {
  interface Window {
    hiprint?: {
      [key: string]: any;
      hiwebSocket?: {
        host?: string;
        opened?: boolean;
        send?: (payload: Record<string, unknown>) => void;
        socket?: {
          connected?: boolean;
          on?: (event: string, handler: (...args: unknown[]) => void) => void;
        };
      };
      init: () => void;
      PrintTemplate: new (options?: any) => any;
    };
    $hiprint?: {
      hiprint?: Window['hiprint'];
      [key: string]: any;
    };
  }
}

/**
 * vue-plugin-hiprint 类型声明
 */
declare module 'vue-plugin-hiprint' {
  import type { App } from 'vue';

  export interface HiPrintPlugin {
    install: (app: App, ...args: any[]) => void;
    [key: string]: any;
  }

  export interface DefaultElementTypeProvider {
    install: (app: App, ...args: any[]) => void;
    [key: string]: any;
  }

  export const hiPrintPlugin: HiPrintPlugin;
  export const defaultElementTypeProvider: DefaultElementTypeProvider;
}
