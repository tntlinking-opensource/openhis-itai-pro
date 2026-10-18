<script setup lang="ts">
/**
 * 入口必须走 `#/`，院点 override 才能稳定命中（glob 相对路径不一定改道）。
 * Canvas 正文只在国基 override 的 EmrCanvasTemplatePage；主线同名文件仅为占位。
 */
import EmrCanvasTemplatePage from '#/views/basic/emr-canvas-template/EmrCanvasTemplatePage.vue';

defineOptions({
  name: 'EmrCanvasTemplate',
});
</script>

<template>
  <EmrCanvasTemplatePage />
</template>
