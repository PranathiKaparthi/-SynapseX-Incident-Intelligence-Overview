import React, { useState, useEffect } from 'react';
import { Outlet, NavLink, useNavigate } from 'react-router-dom';
import {
  Activity,
  AlertTriangle,
  PlayCircle,
  BookOpen,
  Bell,
  LogOut,
  User as UserIcon,
  CheckCircle,
  Wifi,
  WifiOff
} from 'lucide-react';
import { useAuthStore } from '../store/useAuthStore';
import { useSocketStore } from '../store/useSocketStore';
import { ApiClient } from '../api/client';

export const Layout: React.FC = () => {
  const { user, workspace, logout } = useAuthStore();
  const { isConnected, connect, disconnect } = useSocketStore();
  const navigate = useNavigate();

  const [notifications, setNotifications] = useState<any[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [isNotifOpen, setIsNotifOpen] = useState(false);

  useEffect(() => {
    connect();
    fetchNotifications();
    const interval = setInterval(fetchNotifications, 15000);
    return () => {
      clearInterval(interval);
      disconnect();
    };
  }, []);

  const fetchNotifications = async () => {
    try {
      const data = await ApiClient.get('/notifications');
      setNotifications(data || []);
      const countRes = await ApiClient.get('/notifications/unread-count');
      setUnreadCount(countRes?.unreadCount || 0);
    } catch (e) {
      // offline fallback
    }
  };

  const handleMarkAsRead = async (id: string) => {
    try {
      await ApiClient.patch(`/notifications/${id}/read`);
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, read: true } : n))
      );
      setUnreadCount((c) => Math.max(0, c - 1));
    } catch (e) {
      console.error(e);
    }
  };

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const navItems = [
    { name: 'Dashboard', path: '/', icon: Activity },
    { name: 'Incidents', path: '/incidents', icon: AlertTriangle },
    { name: 'Simulations', path: '/simulations', icon: PlayCircle },
    { name: 'Knowledge Base', path: '/knowledge', icon: BookOpen },
  ];

  return (
    <div className="min-h-screen bg-[#0B0F19] text-gray-100 flex flex-col font-sans">
      {/* Top Navigation Bar */}
      <header className="border-b border-gray-800 bg-[#111827]/80 backdrop-blur sticky top-0 z-50 px-6 py-3">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          {/* Logo & Brand */}
          <div className="flex items-center space-x-6">
            <NavLink to="/" className="flex items-center space-x-2.5">
              <div className="w-8 h-8 rounded-lg bg-gradient-to-tr from-blue-600 to-indigo-500 flex items-center justify-center shadow-lg shadow-blue-500/20">
                <Activity className="w-5 h-5 text-white animate-pulse" />
              </div>
              <span className="text-xl font-bold tracking-tight bg-gradient-to-r from-white via-gray-200 to-gray-400 bg-clip-text text-transparent">
                Synapse<span className="text-blue-500">X</span>
              </span>
            </NavLink>

            {/* Workspace Badge */}
            <div className="hidden md:flex items-center space-x-2 px-3 py-1 rounded-full bg-gray-800/80 border border-gray-700/60 text-xs text-gray-300">
              <span className="w-2 h-2 rounded-full bg-emerald-400"></span>
              <span className="font-medium">{workspace?.name || 'Acme Production'}</span>
            </div>
          </div>

          {/* Navigation Links */}
          <nav className="hidden lg:flex items-center space-x-1">
            {navItems.map((item) => {
              const Icon = item.icon;
              return (
                <NavLink
                  key={item.path}
                  to={item.path}
                  className={({ isActive }) =>
                    `flex items-center space-x-2 px-3.5 py-1.5 rounded-lg text-sm font-medium transition-all ${
                      isActive
                        ? 'bg-blue-600/15 text-blue-400 border border-blue-500/30'
                        : 'text-gray-400 hover:text-gray-200 hover:bg-gray-800/50'
                    }`
                  }
                >
                  <Icon className="w-4 h-4" />
                  <span>{item.name}</span>
                </NavLink>
              );
            })}
          </nav>

          {/* Actions & Profile */}
          <div className="flex items-center space-x-4">
            {/* STOMP WebSocket Connection Pill */}
            <div
              className={`flex items-center space-x-1.5 px-2.5 py-1 rounded-full text-xs font-medium border ${
                isConnected
                  ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30'
                  : 'bg-amber-500/10 text-amber-400 border-amber-500/30'
              }`}
              title={isConnected ? 'Real-Time Telemetry & Chat Connected' : 'Reconnecting...'}
            >
              {isConnected ? <Wifi className="w-3.5 h-3.5" /> : <WifiOff className="w-3.5 h-3.5 animate-pulse" />}
              <span className="hidden sm:inline">{isConnected ? 'LIVE' : 'SYNCING'}</span>
            </div>

            {/* Notification Bell */}
            <div className="relative">
              <button
                onClick={() => setIsNotifOpen(!isNotifOpen)}
                className="relative p-2 rounded-lg text-gray-400 hover:text-white hover:bg-gray-800 transition"
                aria-label="Notifications"
              >
                <Bell className="w-5 h-5" />
                {unreadCount > 0 && (
                  <span className="absolute top-1 right-1 w-4 h-4 bg-red-500 text-white rounded-full text-[10px] flex items-center justify-center font-bold">
                    {unreadCount > 9 ? '9+' : unreadCount}
                  </span>
                )}
              </button>

              {/* Notification Popover */}
              {isNotifOpen && (
                <div className="absolute right-0 mt-2 w-80 sm:w-96 bg-[#111827] border border-gray-800 rounded-xl shadow-2xl z-50 overflow-hidden">
                  <div className="p-3 border-b border-gray-800 flex items-center justify-between">
                    <span className="font-semibold text-sm text-gray-200">Incident Alerts</span>
                    <span className="text-xs text-gray-500">{notifications.length} recent</span>
                  </div>
                  <div className="max-h-80 overflow-y-auto divide-y divide-gray-800/50">
                    {notifications.length === 0 ? (
                      <div className="p-6 text-center text-xs text-gray-500">No recent alerts</div>
                    ) : (
                      notifications.map((n) => (
                        <div
                          key={n.id}
                          className={`p-3 text-xs transition hover:bg-gray-800/40 ${
                            !n.read ? 'bg-blue-950/20' : ''
                          }`}
                        >
                          <div className="flex items-start justify-between gap-2">
                            <span className="font-semibold text-gray-200">{n.title}</span>
                            {!n.read && (
                              <button
                                onClick={() => handleMarkAsRead(n.id)}
                                className="text-blue-400 hover:text-blue-300 flex items-center space-x-1"
                              >
                                <CheckCircle className="w-3 h-3" />
                                <span>Read</span>
                              </button>
                            )}
                          </div>
                          <p className="text-gray-400 mt-1 line-clamp-2">{n.message}</p>
                          <span className="text-[10px] text-gray-500 mt-1.5 block">
                            {new Date(n.createdAt).toLocaleTimeString()}
                          </span>
                        </div>
                      ))
                    )}
                  </div>
                </div>
              )}
            </div>

            {/* User Profile */}
            <div className="flex items-center space-x-3 pl-2 border-l border-gray-800">
              <div className="flex items-center space-x-2">
                <div className="w-7 h-7 rounded-full bg-blue-600/30 border border-blue-500/40 flex items-center justify-center text-blue-400">
                  <UserIcon className="w-4 h-4" />
                </div>
                <div className="hidden sm:block text-left text-xs">
                  <p className="font-medium text-gray-200">{user?.fullName || 'Alice Eng'}</p>
                  <p className="text-[10px] text-blue-400 font-semibold">{user?.role || 'ENGINEER'}</p>
                </div>
              </div>

              <button
                onClick={handleLogout}
                title="Sign out"
                className="p-1.5 rounded-lg text-gray-500 hover:text-red-400 hover:bg-red-500/10 transition"
              >
                <LogOut className="w-4 h-4" />
              </button>
            </div>
          </div>
        </div>
      </header>

      {/* Main Content Viewport */}
      <main className="flex-1 max-w-7xl w-full mx-auto p-4 sm:p-6 lg:p-8">
        <Outlet />
      </main>

      {/* Footer */}
      <footer className="border-t border-gray-900 bg-[#0B0F19] py-4 text-center text-xs text-gray-600">
        SynapseX Autonomous Incident Intelligence Platform &copy; 2026. Real-Time Telemetry &bull; Kafka &bull; pgvector &bull; Multi-Agent AI
      </footer>
    </div>
  );
};
