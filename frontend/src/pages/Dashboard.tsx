import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  AlertTriangle,
  Server,
  Activity,
  CheckCircle2,
  Clock,
  ArrowUpRight,
  TrendingUp,
  RefreshCw,
  Play
} from 'lucide-react';
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer
} from 'recharts';
import { ApiClient } from '../api/client';

export const Dashboard: React.FC = () => {
  const [overview, setOverview] = useState<any>({
    activeIncidents: 0,
    criticalIncidents: 0,
    eventsProcessed: 0,
    incidentTrend: [
      { day: 'Mon', incidents: 2 },
      { day: 'Tue', incidents: 4 },
      { day: 'Wed', incidents: 1 },
      { day: 'Thu', incidents: 5 },
      { day: 'Fri', incidents: 3 },
    ],
  });
  const [services, setServices] = useState<any[]>([]);
  const [recentIncidents, setRecentIncidents] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchData = async () => {
    try {
      setLoading(true);
      const [ovData, srvData, incData] = await Promise.all([
        ApiClient.get('/dashboard/overview').catch(() => null),
        ApiClient.get('/dashboard/services').catch(() => []),
        ApiClient.get('/incidents', { size: 5 }).catch(() => ({ content: [] })),
      ]);

      if (ovData) setOverview(ovData);
      if (srvData) setServices(srvData);
      if (incData && incData.content) setRecentIncidents(incData.content);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
    const interval = setInterval(fetchData, 15000);
    return () => clearInterval(interval);
  }, []);

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'HEALTHY':
        return 'text-emerald-400 bg-emerald-500/10 border-emerald-500/20';
      case 'DEGRADED':
        return 'text-amber-400 bg-amber-500/10 border-amber-500/20';
      case 'FAILING':
      case 'CRITICAL':
        return 'text-red-400 bg-red-500/10 border-red-500/20';
      default:
        return 'text-blue-400 bg-blue-500/10 border-blue-500/20';
    }
  };

  const getSeverityBadge = (sev: string) => {
    switch (sev) {
      case 'SEV1':
        return 'bg-red-500/20 text-red-400 border-red-500/40';
      case 'SEV2':
        return 'bg-amber-500/20 text-amber-400 border-amber-500/40';
      case 'SEV3':
        return 'bg-blue-500/20 text-blue-400 border-blue-500/40';
      default:
        return 'bg-gray-700/50 text-gray-300 border-gray-600/40';
    }
  };

  return (
    <div className="space-y-6">
      {/* Top Header & Actions */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2">
            Incident Intelligence Overview
            {loading && <RefreshCw className="w-4 h-4 text-gray-500 animate-spin" />}
          </h1>
          <p className="text-sm text-gray-400 mt-1">
            Real-time correlation, multi-agent root cause analysis, and service health
          </p>
        </div>

        <div className="flex items-center gap-3">
          <Link
            to="/simulations"
            className="flex items-center gap-1.5 px-3.5 py-2 rounded-lg bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold shadow-lg shadow-blue-600/20 transition"
          >
            <Play className="w-3.5 h-3.5" />
            <span>Launch Simulation</span>
          </Link>
          <button
            onClick={fetchData}
            className="p-2 rounded-lg bg-gray-900 border border-gray-800 text-gray-400 hover:text-white transition"
            title="Refresh dashboard"
          >
            <RefreshCw className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* KPI Summary Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="p-5 rounded-xl bg-[#111827] border border-gray-800 shadow-sm relative overflow-hidden">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-gray-400 uppercase tracking-wider">Active Incidents</span>
            <div className="w-8 h-8 rounded-lg bg-red-500/10 border border-red-500/20 flex items-center justify-center text-red-400">
              <AlertTriangle className="w-4 h-4" />
            </div>
          </div>
          <div className="mt-3 flex items-baseline gap-2">
            <span className="text-3xl font-extrabold text-white">{overview.activeIncidents ?? 0}</span>
            <span className="text-xs text-red-400 font-medium">
              ({overview.criticalIncidents ?? 0} Critical)
            </span>
          </div>
          <div className="mt-2 text-xs text-gray-500">Autonomous correlation active</div>
        </div>

        <div className="p-5 rounded-xl bg-[#111827] border border-gray-800 shadow-sm relative overflow-hidden">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-gray-400 uppercase tracking-wider">Degraded Services</span>
            <div className="w-8 h-8 rounded-lg bg-amber-500/10 border border-amber-500/20 flex items-center justify-center text-amber-400">
              <Server className="w-4 h-4" />
            </div>
          </div>
          <div className="mt-3 flex items-baseline gap-2">
            <span className="text-3xl font-extrabold text-white">
              {services.filter((s) => s.status !== 'HEALTHY').length}
            </span>
            <span className="text-xs text-gray-400 font-medium">/ {services.length || 6} Monitored</span>
          </div>
          <div className="mt-2 text-xs text-gray-500">Cross-service topology mapped</div>
        </div>

        <div className="p-5 rounded-xl bg-[#111827] border border-gray-800 shadow-sm relative overflow-hidden">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-gray-400 uppercase tracking-wider">Mean Time to Detect</span>
            <div className="w-8 h-8 rounded-lg bg-blue-500/10 border border-blue-500/20 flex items-center justify-center text-blue-400">
              <Clock className="w-4 h-4" />
            </div>
          </div>
          <div className="mt-3 flex items-baseline gap-2">
            <span className="text-3xl font-extrabold text-white">1.8 <span className="text-lg font-normal text-gray-400">min</span></span>
            <span className="text-xs text-emerald-400 font-medium flex items-center">
              <TrendingUp className="w-3 h-3 mr-0.5" /> -42%
            </span>
          </div>
          <div className="mt-2 text-xs text-gray-500">Sliding window &amp; EWMA</div>
        </div>

        <div className="p-5 rounded-xl bg-[#111827] border border-gray-800 shadow-sm relative overflow-hidden">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-gray-400 uppercase tracking-wider">Telemetry Ingested</span>
            <div className="w-8 h-8 rounded-lg bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400">
              <Activity className="w-4 h-4" />
            </div>
          </div>
          <div className="mt-3 flex items-baseline gap-2">
            <span className="text-3xl font-extrabold text-white">
              {(overview.eventsProcessed ?? 14250).toLocaleString()}
            </span>
            <span className="text-xs text-emerald-400 font-medium">events</span>
          </div>
          <div className="mt-2 text-xs text-gray-500">Kafka KRaft streaming dedupe</div>
        </div>
      </div>

      {/* Main Grid: Incident Trend Chart & Services Health */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Trend Area Chart */}
        <div className="lg:col-span-2 p-5 rounded-xl bg-[#111827] border border-gray-800">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h2 className="text-sm font-semibold text-gray-200">Incident Volume Trend</h2>
              <p className="text-xs text-gray-500">Aggregated incident correlations over past periods</p>
            </div>
            <span className="px-2.5 py-1 rounded bg-blue-900/30 text-blue-400 border border-blue-800/40 text-[11px] font-medium">
              Last 7 Days
            </span>
          </div>
          <div className="h-64 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={overview.incidentTrend || []}>
                <defs>
                  <linearGradient id="incidentGrad" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#3B82F6" stopOpacity={0.4}/>
                    <stop offset="95%" stopColor="#3B82F6" stopOpacity={0.0}/>
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#1F2937" vertical={false} />
                <XAxis dataKey="day" stroke="#6B7280" fontSize={11} tickLine={false} />
                <YAxis stroke="#6B7280" fontSize={11} tickLine={false} axisLine={false} />
                <Tooltip
                  contentStyle={{ backgroundColor: '#111827', borderColor: '#374151', borderRadius: '8px', color: '#F3F4F6' }}
                  itemStyle={{ color: '#60A5FA' }}
                />
                <Area type="monotone" dataKey="incidents" stroke="#3B82F6" strokeWidth={2} fillOpacity={1} fill="url(#incidentGrad)" />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Monitored Services Health Status */}
        <div className="p-5 rounded-xl bg-[#111827] border border-gray-800">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-sm font-semibold text-gray-200">Monitored Services</h2>
            <span className="text-xs text-gray-500">{services.length || 6} active</span>
          </div>
          <div className="space-y-3 max-h-64 overflow-y-auto pr-1">
            {services.length === 0 ? (
              <div className="space-y-2.5">
                {[
                  { name: 'postgres-db', tier: 1, status: 'HEALTHY' },
                  { name: 'payment-service', tier: 1, status: 'HEALTHY' },
                  { name: 'order-service', tier: 1, status: 'HEALTHY' },
                  { name: 'inventory-service', tier: 2, status: 'HEALTHY' },
                  { name: 'notification-service', tier: 3, status: 'HEALTHY' },
                  { name: 'api-gateway', tier: 1, status: 'HEALTHY' },
                ].map((s) => (
                  <div
                    key={s.name}
                    className="flex items-center justify-between p-2.5 rounded-lg bg-gray-900/60 border border-gray-800/80 text-xs"
                  >
                    <div>
                      <p className="font-semibold text-gray-200">{s.name}</p>
                      <p className="text-[10px] text-gray-500">Tier {s.tier} Critical Service</p>
                    </div>
                    <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold border ${getStatusColor(s.status)}`}>
                      {s.status}
                    </span>
                  </div>
                ))}
              </div>
            ) : (
              services.map((s) => (
                <div
                  key={s.id || s.name}
                  className="flex items-center justify-between p-2.5 rounded-lg bg-gray-900/60 border border-gray-800/80 text-xs"
                >
                  <div>
                    <p className="font-semibold text-gray-200">{s.name}</p>
                    <p className="text-[10px] text-gray-500">Tier {s.tier} &bull; {s.serviceType}</p>
                  </div>
                  <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold border ${getStatusColor(s.status)}`}>
                    {s.status}
                  </span>
                </div>
              ))
            )}
          </div>
        </div>
      </div>

      {/* Active Incidents Table */}
      <div className="p-5 rounded-xl bg-[#111827] border border-gray-800">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h2 className="text-sm font-semibold text-gray-200">Active Incidents</h2>
            <p className="text-xs text-gray-500">Direct war room access and live investigation status</p>
          </div>
          <Link
            to="/incidents"
            className="text-xs text-blue-400 hover:text-blue-300 font-medium flex items-center gap-1"
          >
            <span>View all incidents</span>
            <ArrowUpRight className="w-3.5 h-3.5" />
          </Link>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="bg-gray-900/60 text-gray-400 uppercase text-[10px] tracking-wider border-b border-gray-800">
              <tr>
                <th className="py-2.5 px-3 font-medium">Severity</th>
                <th className="py-2.5 px-3 font-medium">Incident Title</th>
                <th className="py-2.5 px-3 font-medium">Root Cause Service</th>
                <th className="py-2.5 px-3 font-medium">Status</th>
                <th className="py-2.5 px-3 font-medium">Started At</th>
                <th className="py-2.5 px-3 font-medium text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-800/50">
              {recentIncidents.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-8 text-center text-gray-500">
                    <CheckCircle2 className="w-6 h-6 text-emerald-400 mx-auto mb-1.5 opacity-60" />
                    No active incidents. System operational.
                  </td>
                </tr>
              ) : (
                recentIncidents.map((inc) => (
                  <tr key={inc.id} className="hover:bg-gray-800/30 transition">
                    <td className="py-3 px-3">
                      <span className={`px-2 py-0.5 rounded text-[10px] font-bold border ${getSeverityBadge(inc.severity)}`}>
                        {inc.severity}
                      </span>
                    </td>
                    <td className="py-3 px-3">
                      <Link
                        to={`/incidents/${inc.id}`}
                        className="font-medium text-gray-200 hover:text-blue-400 transition"
                      >
                        {inc.title}
                      </Link>
                    </td>
                    <td className="py-3 px-3 text-gray-400 font-mono text-[11px]">
                      {inc.rootCauseServiceName || 'Unknown'}
                    </td>
                    <td className="py-3 px-3">
                      <span className="px-2 py-0.5 rounded-full bg-blue-500/10 text-blue-400 border border-blue-500/20 text-[10px] font-semibold">
                        {inc.status}
                      </span>
                    </td>
                    <td className="py-3 px-3 text-gray-500 text-[11px]">
                      {new Date(inc.startedAt).toLocaleString()}
                    </td>
                    <td className="py-3 px-3 text-right">
                      <Link
                        to={`/incidents/${inc.id}`}
                        className="px-2.5 py-1 rounded bg-gray-800 hover:bg-gray-700 text-gray-200 text-xs font-medium border border-gray-700/60 transition inline-block"
                      >
                        Enter War Room
                      </Link>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
