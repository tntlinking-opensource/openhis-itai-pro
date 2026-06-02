import type { Linter } from 'eslint';

import {
  command,
  comments,
  disableds,
  ignores,
  importPluginConfig,
  javascript,
  jsdoc,
  jsonc,
  node,
  perfectionist,
  prettier,
  regexp,
  test,
  turbo,
  typescript,
  unicorn,
  vue,
} from './configs';
import { customConfig } from './custom-config';
import { interopDefault } from './util';

type FlatConfig = Linter.Config;

type FlatConfigPromise = FlatConfig | FlatConfig[] | Promise<FlatConfig> | Promise<FlatConfig[]>;

async function defineConfig(config: FlatConfig[] = []) {
  const configs: FlatConfigPromise[] = [
    vue(),
    javascript(),
    ignores(),
    prettier(),
    typescript(),
    jsonc(),
    disableds(),
    importPluginConfig(),
    node(),
    perfectionist(),
    comments(),
    jsdoc(),
    unicorn(),
    test(),
    regexp(),
    command(),
    turbo(),
    ...customConfig,
    ...config,
  ];

  const resolved = await Promise.all(configs);
  const flat = resolved.flat();

  // 确保 .vue 始终用 vue-eslint-parser，避免被其他 config 覆盖导致 Parsing error
  const [parserVue, parserTs] = await Promise.all([
    interopDefault(import('vue-eslint-parser')),
    interopDefault(import('@typescript-eslint/parser')),
  ]);
  flat.push({
    files: ['**/*.vue'],
    languageOptions: {
      parser: parserVue,
      parserOptions: {
        ecmaFeatures: { jsx: true },
        extraFileExtensions: ['.vue'],
        parser: parserTs,
        sourceType: 'module',
      },
    },
  });

  return flat;
}

export { defineConfig };
