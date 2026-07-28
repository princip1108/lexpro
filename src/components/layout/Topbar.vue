<template>
  <el-header class="topbar">
    <div class="nav-tabs">
      <span class="top-icon"><el-icon><Fold /></el-icon></span>
      <router-link to="/dashboard" class="top-link" :class="{ active: route.path === '/dashboard' }">
        个人中心
      </router-link>
      <router-link
        v-if="hasPermission('USER_MANAGE')"
        to="/organization"
        class="top-link"
        :class="{ active: route.path === '/organization' }"
      >
        组织管理
      </router-link>
      <router-link
        v-if="hasPermission('CONTENT_MANAGE')"
        to="/content-management"
        class="top-link"
        :class="{ active: route.path === '/content-management' }"
      >
        内容管理
      </router-link>
      <router-link to="/pending-tasks" class="top-link danger-dot" :class="{ active: route.path === '/pending-tasks' }">
        待办任务({{ pendingCount }})
      </router-link>
    </div>
    <div class="top-actions">
      <span class="run-status">
        <el-icon><Monitor /></el-icon>
        系统运行管理
      </span>
      <el-input
        v-model="keyword"
        class="quick-search"
        placeholder="快速检索内容"
        :prefix-icon="Search"
        clearable
      />
      <el-dropdown>
        <span class="user">
          {{ currentUser?.realName || currentUser?.username }}
          <el-icon><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item disabled>{{ currentUser?.role?.name }}</el-dropdown-item>
            <el-dropdown-item divided :disabled="loggingOut" @click="handleLogout">
              退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </el-header>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, Fold, Monitor, Search } from '@element-plus/icons-vue'
import { logout } from '../../api/auth'
import { clearSession, currentUser, hasPermission } from '../../auth/session'
import todoCases from '../../mock/todoCases.json'

const route = useRoute()
const router = useRouter()
const keyword = ref('')
const loggingOut = ref(false)
const pendingCount = todoCases.filter((item) => item.status !== '已完成').length

const handleLogout = async () => {
  if (loggingOut.value) return
  loggingOut.value = true
  try {
    await logout()
  } catch {
    // Local logout must still complete when the backend is unavailable.
  } finally {
    clearSession()
    loggingOut.value = false
    await router.replace('/login')
  }
}
</script>

<style scoped>
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 20px;
  background: #fff;
  border-bottom: 1px solid var(--border);
}

.nav-tabs,
.top-actions,
.run-status,
.user {
  display: flex;
  align-items: center;
}

.nav-tabs {
  gap: 22px;
  height: 100%;
}

.top-icon {
  display: inline-flex;
  color: #2c3e50;
  font-size: 20px;
}

.top-link {
  position: relative;
  height: 56px;
  color: #3f5268;
  font-size: 14px;
  line-height: 56px;
}

.top-link.active {
  color: var(--primary);
  font-weight: 700;
}

.top-link.active::after {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  height: 3px;
  background: var(--primary);
  content: "";
}

.danger-dot::before {
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-right: 4px;
  vertical-align: middle;
  background: #f04f5f;
  border-radius: 50%;
  content: "";
}

.top-actions {
  gap: 18px;
  color: #4e5f73;
  font-size: 14px;
}

.run-status {
  gap: 5px;
}

.quick-search {
  width: 260px;
}

.user {
  gap: 4px;
  cursor: pointer;
}
</style>
