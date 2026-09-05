<template>
  <aside class="sidebar">
    <div class="sidebar-logo">
      <div class="t1">法律大模型与检察业务融合应用系统</div>
      <div class="t2">检察业务智能辅助平台</div>
    </div>
    <nav class="side-nav">
      <router-link class="nav-item" to="/dashboard"><LegalIcon name="dash" /><span>工作台</span></router-link>

      <div class="grp">案件管理</div>
      <router-link class="nav-item" to="/todo-cases"><LegalIcon name="case" /><span>案件管理</span></router-link>
      <router-link v-if="hasPermission('RECOMMENDATION_USE')" class="nav-item" :to="{ path: '/case-recommend', query: { view: 'library' } }"><LegalIcon name="book" /><span>典型案例库</span></router-link>

      <div class="grp">核心功能</div>
      <router-link v-if="hasPermission('CASE_READ')" class="nav-item" to="/document-entities"><LegalIcon name="entity" /><span>询问讯问笔录实体识别</span></router-link>
      <router-link v-if="hasPermission('CASE_READ')" class="nav-item" to="/legal-elements"><LegalIcon name="elem" /><span>法律要素识别</span></router-link>
      <router-link v-if="hasPermission('CASE_READ')" class="nav-item" to="/summary"><LegalIcon name="summary" /><span>案例摘要生成</span></router-link>
      <router-link v-if="hasPermission('RECOMMENDATION_USE')" class="nav-item" :to="{ path: '/case-recommend', query: { view: 'recommend' } }"><LegalIcon name="push" /><span>典型案例推送</span></router-link>

      <div class="grp">业务融合</div>
      <router-link v-if="hasPermission('CASE_READ')" class="nav-item" to="/case-cards"><LegalIcon name="card" /><span>案卡回填</span></router-link>
      <router-link v-if="hasPermission('CASE_READ')" class="nav-item" to="/review-report"><LegalIcon name="report" /><span>审查报告</span></router-link>

      <div class="grp">系统</div>
      <router-link class="nav-item" to="/model-config"><LegalIcon name="model" /><span>模型配置</span></router-link>

      <template v-if="hasPermission('USER_MANAGE')">
        <div class="grp">系统管理</div>
        <router-link class="nav-item" to="/users"><LegalIcon name="user" /><span>用户管理</span></router-link>
        <router-link class="nav-item" to="/operation-logs"><LegalIcon name="log" /><span>操作日志</span></router-link>
      </template>
    </nav>
    <div class="sidebar-user">
      <div class="avatar">{{ avatar }}</div>
      <div class="identity"><div class="uname">{{ currentUser?.realName || currentUser?.username }}</div><div class="urole">{{ currentUser?.role?.name || '工作账号' }}</div></div>
      <button class="btn ghost sm" type="button" :disabled="loggingOut" @click="handleLogout">退出</button>
    </div>
  </aside>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import LegalIcon from '../common/LegalIcon.vue'
import { logout } from '../../api/auth'
import { clearSession, currentUser, hasPermission } from '../../auth/session'

const router = useRouter()
const loggingOut = ref(false)
const avatar = computed(() => (currentUser.value?.realName || currentUser.value?.username || '检').slice(0, 1))

async function handleLogout() {
  if (loggingOut.value) return
  loggingOut.value = true
  try { await logout() } catch { /* local logout remains authoritative */ }
  finally { clearSession(); loggingOut.value = false; await router.replace('/') }
}
</script>

<style scoped>
.sidebar{height:100vh;min-height:0;position:sticky;top:0}.side-nav{min-height:0;overscroll-behavior:contain}.identity{min-width:0;flex:1}.sidebar-user .btn{margin-left:auto;color:#d3ddec;background:transparent;border-color:rgba(255,255,255,.25)}.sidebar-user .btn:hover{color:#fff;background:rgba(255,255,255,.09)}
</style>
