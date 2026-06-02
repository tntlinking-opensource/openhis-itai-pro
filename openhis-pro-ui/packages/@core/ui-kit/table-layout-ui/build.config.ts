import { copyFileSync, existsSync, mkdirSync, readdirSync } from 'node:fs';
import { join } from 'node:path';

import { defineBuildConfig } from 'unbuild';

function copyTypeDeclarationsToDist() {
  const srcDir = join('src', 'types');
  const distDir = join('dist', 'types');
  if (!existsSync(srcDir)) {
    return;
  }
  mkdirSync(distDir, { recursive: true });
  for (const name of readdirSync(srcDir)) {
    if (name.endsWith('.d.ts')) {
      copyFileSync(join(srcDir, name), join(distDir, name));
    }
  }
}

export default defineBuildConfig({
  clean: true,
  declaration: true,
  entries: [
    {
      builder: 'mkdist',
      input: './src',
      loaders: ['vue'],
      pattern: ['**/*.vue'],
    },
    {
      builder: 'mkdist',
      input: './src',
      loaders: ['vue'],
      pattern: ['**/*.tsx'],
    },
    {
      builder: 'mkdist',
      format: 'esm',
      input: './src',
      loaders: ['js'],
      pattern: ['**/*.ts'],
    },
  ],
  hooks: {
    'build:done': function () {
      copyTypeDeclarationsToDist();
    },
  },
});
