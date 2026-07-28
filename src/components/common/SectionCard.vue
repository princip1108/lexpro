<template>
  <el-card class="section-card" shadow="never">
    <template v-if="title || $slots.extra" #header>
      <div class="section-header">
        <h3>{{ title }}</h3>
        <div class="section-extra">
          <slot name="extra" />
        </div>
      </div>
    </template>
    <slot />
  </el-card>
</template>

<script setup>
defineProps({
  title: {
    type: String,
    default: ''
  }
})
</script>

<style scoped>
.section-card {
  border: 0;
  box-shadow: 0 8px 24px rgba(31, 86, 216, 0.06);
}

.section-card :deep(.el-card__header) {
  padding: 15px 18px;
  border-bottom: 1px solid #eef2f7;
}

.section-card :deep(.el-card__body) {
  padding: 18px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.section-header h3 {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
}

.section-extra {
  color: var(--primary);
  font-size: 14px;
}
</style>
