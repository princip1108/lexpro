<template>
  <el-aside width="216px" class="sidebar">
    <div class="brand">
      <div class="brand-mark">L</div>
      <div>
        <strong>LexPro</strong>
        <span>检察业务融合应用</span>
      </div>
    </div>
    <el-menu
      :default-active="activePath"
      router
      class="side-menu"
      background-color="#2f72f6"
      text-color="#dfeaff"
      active-text-color="#ffffff"
    >
      <el-menu-item index="/dashboard">
        <el-icon><House /></el-icon>
        <span>首页</span>
      </el-menu-item>
      <el-sub-menu index="case-manage">
        <template #title>
          <el-icon><FolderOpened /></el-icon>
          <span>案件管理</span>
        </template>
        <el-menu-item index="/todo-cases">待办案件</el-menu-item>
        <el-menu-item index="/review-report">审查报告生成</el-menu-item>
      </el-sub-menu>
      <el-menu-item index="/legal-elements">
        <el-icon><Aim /></el-icon>
        <span>法律要素识别</span>
      </el-menu-item>
      <el-menu-item index="/document-entities">
        <el-icon><Tickets /></el-icon>
        <span>文书实体识别</span>
      </el-menu-item>
      <el-menu-item index="/summary">
        <el-icon><Document /></el-icon>
        <span>案例摘要生成</span>
      </el-menu-item>
      <el-menu-item index="/case-recommend">
        <el-icon><CollectionTag /></el-icon>
        <span>典型案例推送</span>
      </el-menu-item>
      <el-sub-menu index="user-manage">
        <template #title>
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </template>
        <el-menu-item index="/dashboard">用户列表</el-menu-item>
      </el-sub-menu>
      <el-sub-menu index="system-manage">
        <template #title>
          <el-icon><Setting /></el-icon>
          <span>系统管理</span>
        </template>
        <el-menu-item index="/dashboard">运行监控</el-menu-item>
      </el-sub-menu>
    </el-menu>
  </el-aside>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import {
  Aim,
  CollectionTag,
  Document,
  FolderOpened,
  House,
  Setting,
  Tickets,
  User
} from '@element-plus/icons-vue'

const route = useRoute()
const activePath = computed(() => route.path)
</script>

<style scoped>
.sidebar {
  min-height: 100vh;
  background: linear-gradient(180deg, #2f72f6 0%, #2362e4 100%);
  box-shadow: 4px 0 18px rgba(29, 85, 190, 0.16);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 66px;
  padding: 0 18px;
  color: #fff;
}

.brand-mark {
  display: grid;
  width: 34px;
  height: 34px;
  font-weight: 800;
  color: #2f72f6;
  background: #fff;
  border-radius: 6px;
  place-items: center;
}

.brand strong,
.brand span {
  display: block;
}

.brand strong {
  font-size: 18px;
  line-height: 1.2;
}

.brand span {
  margin-top: 3px;
  font-size: 12px;
  color: #dfeaff;
}

.side-menu {
  border-right: 0;
}

.side-menu :deep(.el-menu-item),
.side-menu :deep(.el-sub-menu__title) {
  height: 48px;
  font-size: 15px;
}

.side-menu :deep(.el-menu-item.is-active) {
  background: rgba(255, 255, 255, 0.16);
  border-left: 4px solid #fff;
}

.side-menu :deep(.el-sub-menu .el-menu-item) {
  min-width: 0;
  padding-left: 52px !important;
  background: #2868ea;
}
</style>
