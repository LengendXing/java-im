<template>
  <div class="h-full flex flex-col bg-[var(--im-bg-secondary)]">
    <!-- Profile card -->
    <div class="bg-[var(--im-surface)] p-6">
      <div class="flex items-center gap-4">
        <div class="w-16 h-16 rounded-lg bg-[var(--im-green)] text-white flex items-center justify-center text-2xl font-bold">
          {{ (auth.nickname || auth.username || '?').charAt(0).toUpperCase() }}
        </div>
        <div>
          <h2 class="text-lg font-medium">{{ auth.nickname || auth.username }}</h2>
          <p class="text-xs text-[var(--im-text-secondary)]">{{ $t('profile.username') }}: {{ auth.username }}</p>
          <div class="flex items-center gap-1 mt-1">
            <span class="w-2 h-2 rounded-full" :class="isConnected ? 'bg-[var(--im-green)]' : 'bg-gray-400'"></span>
            <span class="text-[10px] text-[var(--im-text-secondary)]">{{ isConnected ? $t('chat.online') : $t('chat.offline') }}</span>
          </div>
        </div>
      </div>
    </div>
    <!-- Settings list -->
    <div class="mt-2 bg-[var(--im-surface)]">
      <div class="flex items-center justify-between px-6 py-3.5 border-b border-[var(--im-border)]">
        <span class="text-sm">{{ $t('profile.darkMode') }}</span>
        <button @click="toggleDark" class="w-10 h-6 rounded-full transition-colors relative"
          :class="isDark ? 'bg-[var(--im-green)]' : 'bg-gray-300'">
          <span class="absolute w-5 h-5 bg-white rounded-full top-0.5 transition-transform shadow"
            :class="isDark ? 'translate-x-[18px]' : 'translate-x-0.5'"></span>
        </button>
      </div>
      <div class="flex items-center justify-between px-6 py-3.5 border-b border-[var(--im-border)] cursor-pointer hover:bg-[var(--im-bg-secondary)]" @click="toggleLocale">
        <span class="text-sm">{{ $t('profile.language') }}</span>
        <span class="text-xs text-[var(--im-text-secondary)]">{{ locale === 'zh' ? '中文' : 'English' }}</span>
      </div>
    </div>
    <!-- Logout -->
    <div class="mt-2 bg-[var(--im-surface)]">
      <div class="px-6 py-3.5 text-center text-red-500 text-sm cursor-pointer hover:bg-[var(--im-bg-secondary)]" @click="handleLogout">
        {{ $t('profile.logout') }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const { locale } = useI18n()

const isDark = ref(true)
const isConnected = ref(true)

onMounted(() => {
  isDark.value = document.documentElement.classList.contains('dark')
  if (!isDark.value) {
    isDark.value = true
    document.documentElement.classList.add('dark')
  }
})

function toggleDark() {
  isDark.value = !isDark.value
  document.documentElement.classList.toggle('dark', isDark.value)
}

function toggleLocale() {
  locale.value = locale.value === 'zh' ? 'en' : 'zh'
  localStorage.setItem('im_locale', locale.value)
}

function handleLogout() {
  auth.logout()
  router.push('/login')
}
</script>
