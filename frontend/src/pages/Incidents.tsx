import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  AlertTriangle,
  Search,
  Filter,
  CheckCircle,
  RefreshCw,
  Clock,
  ArrowRight
} from 'lucide-react';
import { ApiClient } from '../api/client';

export const Incidents: React.FC = () => {
  const [incidents, setIncidents] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [severityFilter, setSeverityFilter] = useState('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);

  const fetchIncidents = async () => {
    try {
      setLoading(true);
      const params: any = { page, size: 10 };
      if (statusFilter !== 'ALL') params.status = statusFilter;
      if (severityFilter !== 'ALL') params.severity = severityFilter;

      const data = await ApiClient.get('/incidents', params);
      if (data && data.content) {
        setIncidents(data.content);
        setTotalPages(data.totalPages || 1);
      }
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchIncidents();
  }, [page, statusFilter, severityFilter]);

  const handleStatusTransition = async (id: string, newStatus: string) => {
    try {
      await ApiClient.patch(`/incidents/${id}/status`, { status: newStatus });
      fetchIncidents();
    } catch (err) {
      console.error('Failed to update incident status:', err);
    }
  };

  const filteredIncidents = incidents.filter((inc) => {
    if (!searchQuery) return true;
    const q = searchQuery.toLowerCase();
    return (
      inc.title?.toLowerCase().includes(q) ||
      inc.rootCauseServiceName?.toLowerCase().includes(q) ||
      inc.summary?.toLowerCase().includes(q)
    );
  });

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

  const statuses = ['ALL', 'DETECTED', 'INVESTIGATING', 'IDENTIFIED', 'MITIGATING', 'RESOLVED', 'CLOSED'];
  const severities = ['ALL', 'SEV1', 'SEV2', 'SEV3', 'SEV4'];

  return (
    <div className="space-y-6">
      {/* Title & Filter Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2">
            Incidents Management
            {loading && <RefreshCw className="w-4 h-4 text-gray-500 animate-spin" />}
          </h1>
          <p className="text-sm text-gray-400 mt-1">
            Correlated incidents, lifecycle triage, and autonomous investigation tracking
          </p>
        </div>

        <button
          onClick={fetchIncidents}
          className="flex items-center gap-1.5 px-3 py-2 rounded-lg bg-gray-900 border border-gray-800 text-gray-300 hover:text-white text-xs transition self-start sm:self-auto"
        >
          <RefreshCw className="w-3.5 h-3.5" />
          <span>Refresh</span>
        </button>
      </div>

      {/* Filter Controls */}
      <div className="p-4 rounded-xl bg-[#111827] border border-gray-800 flex flex-wrap items-center justify-between gap-4">
        {/* Search */}
        <div className="relative min-w-[260px] flex-1">
          <Search className="w-4 h-4 text-gray-500 absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            placeholder="Search by title, service, summary..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full bg-gray-900 border border-gray-800 rounded-lg pl-9 pr-3 py-1.5 text-xs text-gray-100 placeholder-gray-500 focus:outline-none focus:border-blue-500 transition"
          />
        </div>

        {/* Status Pills */}
        <div className="flex items-center gap-1 overflow-x-auto py-1">
          {statuses.map((st) => (
            <button
              key={st}
              onClick={() => {
                setStatusFilter(st);
                setPage(0);
              }}
              className={`px-2.5 py-1 rounded-lg text-xs font-medium transition whitespace-nowrap ${
                statusFilter === st
                  ? 'bg-blue-600 text-white'
                  : 'bg-gray-900 text-gray-400 hover:text-gray-200 border border-gray-800'
              }`}
            >
              {st}
            </button>
          ))}
        </div>

        {/* Severity Filter */}
        <div className="flex items-center gap-2">
          <Filter className="w-3.5 h-3.5 text-gray-400" />
          <select
            value={severityFilter}
            onChange={(e) => {
              setSeverityFilter(e.target.value);
              setPage(0);
            }}
            className="bg-gray-900 border border-gray-800 rounded-lg px-2.5 py-1.5 text-xs text-gray-300 focus:outline-none focus:border-blue-500"
          >
            {severities.map((sev) => (
              <option key={sev} value={sev}>
                {sev === 'ALL' ? 'All Severities' : sev}
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* Incidents Table */}
      <div className="p-5 rounded-xl bg-[#111827] border border-gray-800">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="bg-gray-900/60 text-gray-400 uppercase text-[10px] tracking-wider border-b border-gray-800">
              <tr>
                <th className="py-2.5 px-3 font-medium">Severity</th>
                <th className="py-2.5 px-3 font-medium">Title &amp; Summary</th>
                <th className="py-2.5 px-3 font-medium">Root Service</th>
                <th className="py-2.5 px-3 font-medium">Status</th>
                <th className="py-2.5 px-3 font-medium">Started At</th>
                <th className="py-2.5 px-3 font-medium text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-800/50">
              {filteredIncidents.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-gray-500">
                    <CheckCircle className="w-8 h-8 text-emerald-400 mx-auto mb-2 opacity-50" />
                    No incidents matching selected filters
                  </td>
                </tr>
              ) : (
                filteredIncidents.map((inc) => (
                  <tr key={inc.id} className="hover:bg-gray-800/30 transition">
                    <td className="py-3.5 px-3 align-top">
                      <span className={`px-2 py-0.5 rounded text-[10px] font-bold border ${getSeverityBadge(inc.severity)}`}>
                        {inc.severity}
                      </span>
                    </td>
                    <td className="py-3.5 px-3 align-top max-w-md">
                      <Link
                        to={`/incidents/${inc.id}`}
                        className="font-semibold text-gray-100 hover:text-blue-400 text-sm transition"
                      >
                        {inc.title}
                      </Link>
                      <p className="text-gray-400 mt-1 line-clamp-2 text-xs">
                        {inc.summary || 'Correlated anomaly events under active analysis.'}
                      </p>
                    </td>
                    <td className="py-3.5 px-3 align-top">
                      <span className="font-mono text-xs px-2 py-0.5 rounded bg-gray-900 border border-gray-800 text-blue-300">
                        {inc.rootCauseServiceName || 'unknown'}
                      </span>
                    </td>
                    <td className="py-3.5 px-3 align-top">
                      <span className="px-2.5 py-1 rounded-full bg-blue-500/10 text-blue-400 border border-blue-500/20 text-[10px] font-bold">
                        {inc.status}
                      </span>
                    </td>
                    <td className="py-3.5 px-3 align-top text-gray-500 text-[11px] whitespace-nowrap">
                      <div className="flex items-center gap-1">
                        <Clock className="w-3 h-3" />
                        <span>{new Date(inc.startedAt).toLocaleTimeString()}</span>
                      </div>
                      <span className="text-[10px] block mt-0.5">
                        {new Date(inc.startedAt).toLocaleDateString()}
                      </span>
                    </td>
                    <td className="py-3.5 px-3 align-top text-right whitespace-nowrap">
                      <div className="flex items-center justify-end gap-2">
                        {inc.status === 'DETECTED' && (
                          <button
                            onClick={() => handleStatusTransition(inc.id, 'INVESTIGATING')}
                            className="px-2.5 py-1 rounded bg-blue-600 hover:bg-blue-500 text-white text-[11px] font-semibold transition"
                          >
                            Investigate
                          </button>
                        )}
                        {inc.status === 'INVESTIGATING' && (
                          <button
                            onClick={() => handleStatusTransition(inc.id, 'MITIGATING')}
                            className="px-2.5 py-1 rounded bg-amber-600 hover:bg-amber-500 text-white text-[11px] font-semibold transition"
                          >
                            Mitigate
                          </button>
                        )}
                        {inc.status === 'MITIGATING' && (
                          <button
                            onClick={() => handleStatusTransition(inc.id, 'RESOLVED')}
                            className="px-2.5 py-1 rounded bg-emerald-600 hover:bg-emerald-500 text-white text-[11px] font-semibold transition"
                          >
                            Resolve
                          </button>
                        )}
                        <Link
                          to={`/incidents/${inc.id}`}
                          className="px-2.5 py-1 rounded bg-gray-800 hover:bg-gray-700 text-gray-200 text-[11px] font-medium border border-gray-700/60 transition inline-flex items-center gap-1"
                        >
                          <span>War Room</span>
                          <ArrowRight className="w-3 h-3" />
                        </Link>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="mt-4 pt-4 border-t border-gray-800 flex items-center justify-between text-xs text-gray-400">
            <span>
              Page {page + 1} of {totalPages}
            </span>
            <div className="flex items-center gap-2">
              <button
                disabled={page === 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                className="px-3 py-1 rounded bg-gray-800 disabled:opacity-40 hover:bg-gray-700 text-gray-300"
              >
                Previous
              </button>
              <button
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
                className="px-3 py-1 rounded bg-gray-800 disabled:opacity-40 hover:bg-gray-700 text-gray-300"
              >
                Next
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
