import { create } from 'zustand';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { ApiClient } from '../api/client';

export interface ChatMessage {
  id: string;
  roomId: string;
  seqNum: number;
  clientMessageId: string;
  senderName: string;
  content: string;
  messageType: 'TEXT' | 'AI_NOTE' | 'SYSTEM';
  createdAt: string;
}

export interface PresenceUser {
  userId: string;
  userName: string;
  status: 'ONLINE' | 'TYPING' | 'AWAY';
  lastSeen: string;
}

interface SocketState {
  client: Client | null;
  isConnected: boolean;
  messages: Record<string, ChatMessage[]>; // roomId -> messages
  presence: Record<string, PresenceUser[]>; // roomId -> users
  highestSeq: Record<string, number>; // roomId -> last known seqNum
  connect: () => void;
  disconnect: () => void;
  subscribeToRoom: (roomId: string) => void;
  unsubscribeFromRoom: (roomId: string) => void;
  sendMessage: (roomId: string, content: string, type?: 'TEXT' | 'AI_NOTE') => void;
  sendPresence: (roomId: string, userName: string, status?: 'ONLINE' | 'TYPING') => void;
}

export const useSocketStore = create<SocketState>((set, get) => {
  let subscriptions: Record<string, any> = {};

  return {
    client: null,
    isConnected: false,
    messages: {},
    presence: {},
    highestSeq: {},

    connect: () => {
      const existing = get().client;
      if (existing && existing.active) return;

      const client = new Client({
        webSocketFactory: () => {
          try {
            const SockJSClass = (SockJS as any).default || SockJS;
            return new SockJSClass('/ws/synapsex');
          } catch (e) {
            const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
            return new WebSocket(`${proto}//${window.location.host}/ws/synapsex/websocket`);
          }
        },
        connectHeaders: {
          Authorization: `Bearer ${ApiClient.getAccessToken() || ''}`,
        },
        reconnectDelay: 5000,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        onConnect: () => {
          set({ isConnected: true });
        },
        onDisconnect: () => {
          set({ isConnected: false });
        },
        onStompError: (frame) => {
          console.error('STOMP broker error:', frame.headers['message']);
        },
      });

      client.activate();
      set({ client });
    },

    disconnect: () => {
      const client = get().client;
      if (client) {
        Object.values(subscriptions).forEach((sub) => sub.unsubscribe());
        subscriptions = {};
        client.deactivate();
        set({ client: null, isConnected: false });
      }
    },

    subscribeToRoom: (roomId: string) => {
      const client = get().client;
      if (!client || !client.connected) return;

      if (subscriptions[`room_${roomId}`]) return;

      // Messages subscription
      const subMsg = client.subscribe(`/topic/room/${roomId}/messages`, (msg: IMessage) => {
        try {
          const chatMsg: ChatMessage = JSON.parse(msg.body);
          set((state) => {
            const list = state.messages[roomId] || [];
            // Client deduplication
            if (list.some((m) => m.id === chatMsg.id || (m.clientMessageId && m.clientMessageId === chatMsg.clientMessageId))) {
              return state;
            }
            const updated = [...list, chatMsg].sort((a, b) => a.seqNum - b.seqNum);
            const currentHighest = state.highestSeq[roomId] || 0;
            return {
              messages: { ...state.messages, [roomId]: updated },
              highestSeq: { ...state.highestSeq, [roomId]: Math.max(currentHighest, chatMsg.seqNum) },
            };
          });
        } catch (e) {
          console.error('Failed to parse STOMP message:', e);
        }
      });

      // Presence subscription
      const subPres = client.subscribe(`/topic/room/${roomId}/presence`, (msg: IMessage) => {
        try {
          const user: PresenceUser = JSON.parse(msg.body);
          set((state) => {
            const users = state.presence[roomId] || [];
            const filtered = users.filter((u) => u.userId !== user.userId);
            return {
              presence: { ...state.presence, [roomId]: [...filtered, user] },
            };
          });
        } catch (e) {
          console.error('Failed to parse STOMP presence:', e);
        }
      });

      subscriptions[`room_${roomId}`] = {
        unsubscribe: () => {
          subMsg.unsubscribe();
          subPres.unsubscribe();
        },
      };
    },

    unsubscribeFromRoom: (roomId: string) => {
      if (subscriptions[`room_${roomId}`]) {
        subscriptions[`room_${roomId}`].unsubscribe();
        delete subscriptions[`room_${roomId}`];
      }
    },

    sendMessage: (roomId: string, content: string, type = 'TEXT') => {
      const client = get().client;
      if (!client || !client.connected) return;

      const clientMessageId = `msg-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;
      client.publish({
        destination: `/app/room/${roomId}/chat`,
        body: JSON.stringify({
          clientMessageId,
          content,
          messageType: type,
        }),
      });
    },

    sendPresence: (roomId: string, userName: string, status = 'ONLINE') => {
      const client = get().client;
      if (!client || !client.connected) return;

      client.publish({
        destination: `/app/room/${roomId}/presence`,
        body: JSON.stringify({
          userName,
          status,
          timestamp: new Date().toISOString(),
        }),
      });
    },
  };
});
