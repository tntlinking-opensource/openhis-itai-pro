import type { PluginOption } from 'vite';

import { createRequire } from 'node:module';

import { defineConfig } from '@vben/vite-config';

import AutoImport from 'unplugin-auto-import/vite';
import ElementPlus from 'unplugin-element-plus/vite';

import { openHisWinDownloadsDevPlugin } from './vite.openhis-win-downloads';
import { createOpenHisProjectOverridePlugin } from './vite.project-override-plugin';
import { createWebEleResolveAliases } from './vite.resolve-aliases';

const require = createRequire(import.meta.url);

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
    useSource: true, // 使用源码以便支持 SCSS 变量覆盖
  }),
] as PluginOption[];

export default defineConfig(async () => {
  const projectOverridePlugin = createOpenHisProjectOverridePlugin(import.meta.dirname);

  return {
    application: {},
    vite: {
      assetsInclude: ['**/*.md'],
      css: {
        preprocessorOptions: {
          scss: {
            // 如果需要通过 SCSS 变量覆盖，可以在这里添加
            // 但由于 Element Plus 变量可能没有 !default，我们主要通过 CSS 变量覆盖
          },
        },
      },
      plugins: [
        ...(projectOverridePlugin ? [projectOverridePlugin] : []),
        openHisWinDownloadsDevPlugin(),
        ...extraPlugins,
      ],
      define: {
        // sockjs-client@1.x 引用了 Node.js 的 global 变量，Vite ESM 环境中不存在
        global: 'globalThis',
      },
      resolve: {
        // monorepo 本地打包：优先走各 workspace 包的 development/src 入口，避免 stub dist（含 jiti）进浏览器包
        conditions: ['development', 'module', 'browser', 'default'],
        alias: {
          ...createWebEleResolveAliases(import.meta.dirname),
          jquery: require.resolve('jquery/dist/jquery.min.js'),
        },
      },
      optimizeDeps: {
        include: ['jquery', 'vue-plugin-hiprint'],
      },
      server: {
        warmup: {
          // 与 @vben/vite-config 一致，但排除 views 内文档类 .md，避免被当作 JS 预转换
          clientFiles: [
            './index.html',
            './src/bootstrap.ts',
            './src/{views,layouts,router,store,api,adapter}/**/*.{vue,ts,tsx,js,scss,css}',
          ],
        },
        proxy: {
          // iMEC 监护仪 API 代理（必须放在 /api 之前，优先匹配）
          // 前端 requestClient baseURL=/api，实际请求路径为 /api/imec/xxx
          '/api/imec': {
            changeOrigin: true,
            rewrite: (path: string) => path.replace(/^\/api/, ''),
            target: 'http://127.0.0.1:8081',
          },
          // iMEC WebSocket STOMP 代理
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
