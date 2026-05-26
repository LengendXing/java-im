import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { wsService } from '../services/websocket'
import { Cmd, MsgType } from '../proto/constants'
import { decodeJsonBody } from '../proto/im'
import type { SessionInfo, C2CMsgNotify, GroupMsgNotify, Message, SyncPoint } from '../proto/im'
import { useAuthStore } from './auth'

export const useChatStore = defineStore('chat', () => {
  const sessions = ref<SessionInfo[]>([])
  const currentSessionId = ref('')
  const messages = ref<Map<string, Message[]>>(new Map())

  const currentMessages = computed(() => messages.value.get(currentSessionId.value) || [])
  const currentSession = computed(() => sessions.value.find(s => s.sessionId === currentSessionId.value))

  function initListeners() {
    wsService.on(Cmd.C2C_MSG_NOTIFY, (packet) => {
      const notify = decodeJsonBody<C2CMsgNotify>(packet.body)
      const msg: Message = { msgId: notify.msgId, senderId: notify.senderId, seq: notify.seq, content: notify.content, serverTime: notify.serverTime }
      addMessage(notify.sessionId, msg)
      updateSession(notify.sessionId, msg)
      wsService.send(Cmd.MSG_ACK, MsgType.REQUEST, { msgId: notify.msgId, sessionId: notify.sessionId, seq: notify.seq })
    })

    wsService.on(Cmd.GROUP_MSG_NOTIFY, (packet) => {
      const notify = decodeJsonBody<GroupMsgNotify>(packet.body)
      const msg: Message = { msgId: notify.msgId, senderId: notify.senderId, seq: notify.seq, content: notify.content, serverTime: notify.serverTime }
      addMessage(notify.sessionId, msg)
      updateSession(notify.sessionId, msg)
    })

    wsService.on(Cmd.SESSION_LIST_ACK, (packet) => {
      const resp = decodeJsonBody<{ sessions: SessionInfo[] }>(packet.body)
      sessions.value = resp.sessions
    })

    wsService.on(Cmd.SYNC_ACK, (packet) => {
      const resp = decodeJsonBody<{ sessions: { sessionId: string; messages: Message[]; hasMore: boolean }[] }>(packet.body)
      for (const s of resp.sessions) {
        for (const m of s.messages) addMessage(s.sessionId, m)
      }
    })

    wsService.on(Cmd.C2C_MSG_ACK, (packet) => {
      const resp = decodeJsonBody<{ code: number; msgId: number; seq: number; serverTime: number }>(packet.body)
      if (resp.code === 0) {
        // Update optimistic message with server-assigned IDs
      }
    })

    wsService.on(Cmd.GROUP_MSG_ACK, (_packet) => {
      // ACK received - optimistic message already displayed
    })
  }

  function addMessage(sessionId: string, msg: Message) {
    if (!messages.value.has(sessionId)) messages.value.set(sessionId, [])
    const list = messages.value.get(sessionId)!
    if (!list.find(m => m.msgId === msg.msgId)) {
      list.push(msg)
      list.sort((a, b) => a.seq - b.seq)
    }
  }

  function updateSession(sessionId: string, msg: Message) {
    const s = sessions.value.find(s => s.sessionId === sessionId)
    if (s) {
      s.lastMsg = msg.content?.text?.substring(0, 50) || ''
      s.lastMsgTime = msg.serverTime
      s.unreadCount++
      // Move to top
      sessions.value = [s, ...sessions.value.filter(x => x.sessionId !== sessionId)]
    }
  }

  function requestSessions() {
    wsService.send(Cmd.SESSION_LIST, MsgType.REQUEST, { lastUpdateTime: 0, limit: 50 })
  }

  function requestSync(points: SyncPoint[]) {
    wsService.send(Cmd.SYNC, MsgType.REQUEST, { points, limit: 50 })
  }

  function selectSession(sessionId: string) {
    currentSessionId.value = sessionId
    const s = sessions.value.find(s => s.sessionId === sessionId)
    if (s) s.unreadCount = 0
    if (!messages.value.has(sessionId) || messages.value.get(sessionId)!.length === 0) {
      requestSync([{ sessionId, lastSeq: 0 }])
    }
  }

  function sendC2CMessage(receiverId: number, text: string) {
    const auth = useAuthStore()
    const sessionId = receiverId < auth.userId ? `c2c_${receiverId}_${auth.userId}` : `c2c_${auth.userId}_${receiverId}`
    const clientMsgId = `${Date.now()}-${Math.random().toString(36).substring(2, 8)}`

    // Optimistic add
    const optimistic: Message = { msgId: -Date.now(), senderId: auth.userId, seq: 0, content: { msgType: 1, text, url: '', fileName: '', fileSize: 0, width: 0, height: 0, duration: 0, extra: '' }, serverTime: Date.now() }
    addMessage(sessionId, optimistic)

    wsService.send(Cmd.C2C_MSG, MsgType.REQUEST, { receiverId, content: { msgType: 1, text }, clientMsgId })
  }

  function sendGroupMessage(groupId: number, text: string) {
    const clientMsgId = `${Date.now()}-${Math.random().toString(36).substring(2, 8)}`
    const sessionId = `group_${groupId}`
    const auth = useAuthStore()

    const optimistic: Message = { msgId: -Date.now(), senderId: auth.userId, seq: 0, content: { msgType: 1, text, url: '', fileName: '', fileSize: 0, width: 0, height: 0, duration: 0, extra: '' }, serverTime: Date.now() }
    addMessage(sessionId, optimistic)

    wsService.send(Cmd.GROUP_MSG, MsgType.REQUEST, { groupId, content: { msgType: 1, text }, clientMsgId })
  }

  return { sessions, currentSessionId, messages, currentMessages, currentSession, initListeners, requestSessions, requestSync, selectSession, sendC2CMessage, sendGroupMessage }
})
