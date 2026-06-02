import { defineBuildConfig } from 'unbuild';

export default defineBuildConfig({
  clean: true,
  declaration: true,
  entries: ['src/index'],
  // @iconify-json/mdi 隐式打包会触发 unbuild 警告，不应阻断整仓 build:ele
  failOnWarn: false,
});
