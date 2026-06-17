import type { PluginOption } from 'vite';

import path from 'node:path';

import { defineConfig } from '@vben/vite-config';

import AutoImport from 'unplugin-auto-import/vite';
import ElementPlus from 'unplugin-element-plus/vite';

const extraPlugins = [
  AutoImport({
    imports: [
      'vue',
      'vue-router',
      'pinia',
      {
        '@vueuse/core': [
          'useMouse',
          'useWindowSize',
          'useLocalStorage',
          'useSessionStorage',
          'useClipboard',
          'useDebounceFn',
          'useThrottleFn',
        ],
      },
    ],
    dts: true,
    eslintrc: {
      enabled: true,
      filepath: './.eslintrc-auto-import.json',
      globalsPropValue: true,
    },
  }),
  ElementPlus({
    format: 'esm',
    useSource: true,
  }),
] as PluginOption[];

/** 本地后台联调：proxy 全部指向 127.0.0.1:8081，与 vite.config.mts 互不影响 */
export default defineConfig(async () => {
  return {
    application: {},
    vite: {
      assetsInclude: ['**/*.md'],
      css: {
        preprocessorOptions: {
          scss: {},
        },
      },
      plugins: extraPlugins,
      define: {
        global: 'globalThis',
      },
      resolve: {
        conditions: ['development', 'module', 'browser', 'default'],
        alias: {
          '@': path.resolve(import.meta.dirname, './src'),
          '@inventory': path.resolve(import.meta.dirname, './src/views/inventory'),
          '@pharmacy': path.resolve(import.meta.dirname, './src/views/inventory/pharmacy'),
          '@warehouse': path.resolve(import.meta.dirname, './src/views/inventory-bk/warehouse'),
        },
      },
      server: {
        warmup: {
          clientFiles: [
            './index.html',
            './src/bootstrap.ts',
            './src/{views,layouts,router,store,api,adapter}/**/*.{vue,ts,tsx,js,scss,css}',
          ],
        },
        proxy: {
          '/api/imec': {
            changeOrigin: true,
            rewrite: (path: string) => path.replace(/^\/api/, ''),
            target: 'http://127.0.0.1:8081',
          },
          '/ws': {
            changeOrigin: true,
            target: 'http://127.0.0.1:8081',
            ws: true,
          },
          '/api': {
            changeOrigin: true,
            rewrite: (path: string) => path.replace(/^\/api/, ''),
            target: 'http://127.0.0.1:8081/api/app',
            ws: true,
          },
          '/uploads': {
            changeOrigin: true,
            target: 'http://127.0.0.1:8081',
          },
        },
      },
    },
  };
});
