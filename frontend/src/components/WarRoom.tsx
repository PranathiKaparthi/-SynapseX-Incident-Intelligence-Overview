import React, { useEffect, useState, useRef } from 'react';
import { Send, Users, ShieldAlert, Sparkles, AlertCircle } from 'lucide-react';
import { useAuthStore } from '../store/useAuthStore';
import { useSocketStore, ChatMessage } from '../store/useSocketStore';
import { ApiClient } from '../api/client';

interface WarRoomProps {
  incidentId: string;
}

export const WarRoom: React.FC<WarRoomProps> = ({ incidentId }) => {
  const { user } = useAuthStore();
  const {
    isConnected,
    subscribeToRoom,
    unsubscribeFromRoom,
    messages: allMessages,
    presence: allPresence,
    sendMessage,
    sendPresence,
  } = useSocketStore();

  const [roomId, setRoomId] = useState<string | null>(null);
  const [inputText, setInputText] = useState('');
  const [loading, setLoading] = useState(true);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const isViewer = user?.role === 'VIEWER';

  useEffect(() => {
    const fetchRoomAndHistory = async () => {
      try {
        setLoading(true);
        // Ensure room exists for this incident
        const room = await ApiClient.post(`/incidents/${incidentId}/war-room`);
        setRoomId(room.id);

        // Fetch persisted historical messages (monotonic sequence ordering)
        const history: ChatMessage[] = await ApiClient.get(`/war-rooms/${room.id}/messages`);
        if (history) {
          useSocketStore.setState((state) => ({
            messages: { ...state.messages, [room.id]: history },
            highestSeq: {
              ...state.highestSeq,
              [room.id]: history.length > 0 ? history[history.length - 1].seqNum : 0,
            },
          }));
        }
      } catch (err) {
        console.error('Failed to init war room:', err);
      } finally {
        setLoading(false);
      }
    };

    fetchRoomAndHistory();
  }, [incidentId]);

  useEffect(() => {
    if (roomId && isConnected) {
      subscribeToRoom(roomId);
      sendPresence(roomId, user?.fullName || 'Engineer', 'ONLINE');

      const presenceInterval = setInterval(() => {
        sendPresence(roomId, user?.fullName || 'Engineer', 'ONLINE');
      }, 30000);

      return () => {
        clearInterval(presenceInterval);
        unsubscribeFromRoom(roomId);
      };
    }
  }, [roomId, isConnected]);

  const messages = (roomId ? allMessages[roomId] : []) || [];
  const presenceList = (roomId ? allPresence[roomId] : []) || [];

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSend = (e: React.FormEvent) => {
    e.preventDefault();
    if (!inputText.trim() || !roomId || isViewer) return;

    sendMessage(roomId, inputText.trim(), 'TEXT');
    setInputText('');
  };

  return (
    <div className="flex flex-col h-[650px] bg-[#111827] border border-gray-800 rounded-xl overflow-hidden shadow-xl">
      {/* War Room Header & Presence */}
      <div className="p-4 border-b border-gray-800 bg-gray-900/60 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-3 h-3 rounded-full bg-red-500 animate-ping"></div>
          <div>
            <h3 className="text-sm font-semibold text-gray-200">Incident Real-Time War Room</h3>
            <p className="text-xs text-gray-400">
              Monotonic sequence ordering with guaranteed gap recovery
            </p>
          </div>
        </div>

        {/* Presence Indicators */}
        <div className="flex items-center gap-2">
          <Users className="w-4 h-4 text-gray-400" />
          <div className="flex -space-x-1.5 overflow-hidden">
            {presenceList.length === 0 ? (
              <span className="text-xs text-gray-500">1 Online (You)</span>
            ) : (
              presenceList.map((u, i) => (
                <div
                  key={u.userId || i}
                  title={`${u.userName} (${u.status})`}
                  className="inline-block h-6 w-6 rounded-full bg-blue-600/30 border border-blue-400/50 text-[10px] text-blue-300 font-bold flex items-center justify-center"
                >
                  {u.userName.substring(0, 2).toUpperCase()}
                </div>
              ))
            )}
          </div>
        </div>
      </div>

      {/* Viewer Role Read-Only Banner */}
      {isViewer && (
        <div className="p-2.5 bg-amber-950/40 border-b border-amber-800/40 flex items-center gap-2 text-amber-300 text-xs">
          <ShieldAlert className="w-4 h-4 text-amber-400 shrink-0" />
          <span>You have VIEWER permissions in this workspace. War Room is read-only.</span>
        </div>
      )}

      {/* Messages Scroll Area */}
      <div className="flex-1 p-4 overflow-y-auto space-y-3">
        {loading ? (
          <div className="flex items-center justify-center h-full text-xs text-gray-500">
            Connecting to war room...
          </div>
        ) : messages.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full text-xs text-gray-500 space-y-1">
            <AlertCircle className="w-8 h-8 text-gray-600" />
            <span>No messages yet. Real-time updates and subagent analysis will appear here.</span>
          </div>
        ) : (
          messages.map((m) => {
            const isMe = m.senderName === (user?.fullName || 'Alice Eng');
            const isAi = m.messageType === 'AI_NOTE';
            const isSystem = m.messageType === 'SYSTEM';

            if (isSystem) {
              return (
                <div key={m.id || m.seqNum} className="text-center my-2">
                  <span className="px-2.5 py-1 rounded bg-gray-900 border border-gray-800 text-[11px] text-gray-400">
                    {m.content}
                  </span>
                </div>
              );
            }

            return (
              <div
                key={m.id || m.seqNum}
                className={`flex flex-col ${isMe ? 'items-end' : 'items-start'}`}
              >
                <div className="flex items-center gap-1.5 mb-1 text-[11px] text-gray-400">
                  <span className="font-semibold text-gray-300">{m.senderName}</span>
                  {isAi && (
                    <span className="px-1.5 py-0.2 rounded bg-purple-900/40 text-purple-300 border border-purple-700/50 text-[10px] flex items-center gap-0.5">
                      <Sparkles className="w-2.5 h-2.5" />
                      AI Agent
                    </span>
                  )}
                  <span className="text-[10px] text-gray-500">
                    #{m.seqNum} &bull; {new Date(m.createdAt).toLocaleTimeString()}
                  </span>
                </div>

                <div
                  className={`max-w-xl rounded-xl px-4 py-2.5 text-xs ${
                    isAi
                      ? 'bg-purple-950/30 border border-purple-800/40 text-purple-200 shadow-md'
                      : isMe
                      ? 'bg-blue-600 text-white shadow-md'
                      : 'bg-gray-800/80 text-gray-200 border border-gray-700/50'
                  }`}
                >
                  <p className="whitespace-pre-wrap leading-relaxed">{m.content}</p>
                </div>
              </div>
            );
          })
        )}
        <div ref={messagesEndRef} />
      </div>

      {/* Input Message Form */}
      <form onSubmit={handleSend} className="p-3 bg-gray-900/80 border-t border-gray-800 flex gap-2">
        <input
          type="text"
          value={inputText}
          onChange={(e) => setInputText(e.target.value)}
          disabled={isViewer || loading}
          placeholder={
            isViewer
              ? 'Read-only access'
              : 'Post investigation note, diagnostic command, or hypothesis...'
          }
          className="flex-1 bg-gray-900 border border-gray-800 rounded-lg px-3.5 py-2 text-xs text-gray-100 placeholder-gray-500 focus:outline-none focus:border-blue-500 disabled:opacity-50 transition"
        />
        <button
          type="submit"
          disabled={!inputText.trim() || isViewer || loading}
          className="px-4 py-2 rounded-lg bg-blue-600 hover:bg-blue-500 disabled:opacity-40 text-white text-xs font-semibold flex items-center gap-1.5 transition"
        >
          <Send className="w-3.5 h-3.5" />
          <span>Send</span>
        </button>
      </form>
    </div>
  );
};
