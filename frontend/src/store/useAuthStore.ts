import { create } from 'zustand';
import { ApiClient } from '../api/client';

export interface User {
  id: string;
  email: string;
  fullName: string;
  role: 'ADMIN' | 'ENGINEER' | 'INCIDENT_MANAGER' | 'VIEWER';
}

export interface Workspace {
  id: string;
  name: string;
  slug: string;
}

interface AuthState {
  user: User | null;
  workspace: Workspace | null;
  isAuthenticated: boolean;
  login: (access: string, refresh: string, user: User, workspace: Workspace) => void;
  logout: () => void;
  switchWorkspace: (workspace: Workspace) => void;
}

export const useAuthStore = create<AuthState>((set) => {
  let user = null;
  let workspace = null;
  try {
    const savedUser = localStorage.getItem('synapsex_user');
    if (savedUser) user = JSON.parse(savedUser);
  } catch {}
  try {
    const savedWs = localStorage.getItem('synapsex_ws');
    if (savedWs) workspace = JSON.parse(savedWs);
  } catch {}

  const token = localStorage.getItem('synapsex_token');

  return {
    user,
    workspace: workspace || {
      id: '00000000-0000-0000-0000-000000000001',
      name: 'Acme Corp Production',
      slug: 'acme-prod'
    },
    isAuthenticated: !!token,

    login: (access, refresh, user, workspace) => {
      ApiClient.setTokens(access, refresh);
      localStorage.setItem('synapsex_user', JSON.stringify(user));
      localStorage.setItem('synapsex_ws', JSON.stringify(workspace));
      set({ user, workspace, isAuthenticated: true });
    },

    logout: () => {
      ApiClient.clearTokens();
      localStorage.removeItem('synapsex_user');
      localStorage.removeItem('synapsex_ws');
      set({ user: null, isAuthenticated: false });
    },

    switchWorkspace: (workspace) => {
      localStorage.setItem('synapsex_ws', JSON.stringify(workspace));
      set({ workspace });
    },
  };
});
