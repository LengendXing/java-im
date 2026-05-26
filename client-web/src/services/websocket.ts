import { encodePacket, decodePacket, encodeJsonBody } from '../proto/im'
import type { DecodedPacket } from '../proto/im'
import { Cmd, MsgType } from '../proto/constants'

type MessageHandler = (packet: DecodedPacket) => void

class WebSocketService {
  private ws: WebSocket | null = null
  private seqId = 0
  private handlers: Map<number, MessageHandler[]> = new Map()
  private url = 'ws://localhost:8801'
  private heartbeatTimer: ReturnType<typeof setInterval> | null = null
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null
  private reconnectAttempts = 0
  private token = ''

  connect(token: string) {
    this.token = token
    this.ws = new WebSocket(this.url)
    this.ws.binaryType = 'arraybuffer'

    this.ws.onopen = () => {
      this.reconnectAttempts = 0
      this.sendAuth()
      this.startHeartbeat()
    }

    this.ws.onmessage = (event) => {
      const packet = decodePacket(event.data as ArrayBuffer)
      if (packet) this.dispatch(packet)
    }

    this.ws.onclose = () => {
      this.stopHeartbeat()
      this.scheduleReconnect()
    }

    this.ws.onerror = () => {
      this.ws?.close()
    }
  }

  disconnect() {
    this.stopHeartbeat()
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer)
    this.ws?.close()
    this.ws = null
  }

  send(cmd: number, msgType: number, body: Record<string, unknown>) {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return
    const seqId = ++this.seqId
    const encoded = encodePacket(cmd, msgType, seqId, encodeJsonBody(body))
    this.ws.send(encoded)
    return seqId
  }

  on(cmd: number, handler: MessageHandler) {
    if (!this.handlers.has(cmd)) this.handlers.set(cmd, [])
    this.handlers.get(cmd)!.push(handler)
  }

  off(cmd: number, handler: MessageHandler) {
    const list = this.handlers.get(cmd)
    if (list) {
      const idx = list.indexOf(handler)
      if (idx >= 0) list.splice(idx, 1)
    }
  }

  private dispatch(packet: DecodedPacket) {
    const list = this.handlers.get(packet.cmd)
    if (list) list.forEach(h => h(packet))
  }

  private sendAuth() {
    this.send(Cmd.AUTH, MsgType.REQUEST, { token: this.token, deviceId: 'web', platform: 3 })
  }

  private startHeartbeat() {
    this.heartbeatTimer = setInterval(() => {
      this.send(Cmd.HEARTBEAT, MsgType.REQUEST, {})
    }, 30000)
  }

  private stopHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
  }

  private scheduleReconnect() {
    const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 30000)
    this.reconnectAttempts++
    this.reconnectTimer = setTimeout(() => {
      this.connect(this.token)
    }, delay)
  }
}

export const wsService = new WebSocketService()
