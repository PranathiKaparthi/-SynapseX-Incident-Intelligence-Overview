import React, { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  AlertTriangle,
  ArrowLeft,
  Clock,
  MessageSquare,
  Sparkles,
  RotateCcw,
  List,
  CheckCircle,
  RefreshCw
} from 'lucide-react';
import { ApiClient } from '../api/client';
import { WarRoom } from '../components/WarRoom';
import { AiInvestigationView } from '../components/AiInvestigationView';
import { TimeMachine } from '../components/TimeMachine';

export const IncidentDetails: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const [incident, setIncident] = useState<any>(null);
  const [timeline, setTimeline] = useState<any[]>([]);
  const [activeTab, setActiveTab] = useState<'warroom' | 'ai' | 'timemachine' | 'timeline'>('warroom');
  const [loading, setLoading] = useState(true);

  const fetchIncidentData = async () => {
    if (!id) return;
    try {
      setLoading(true);
      const [inc, events] = await Promise.all([
        ApiClient.get(`/incidents/${id}`),
        ApiClient.get(`/incidents/${id}/timeline`).catch(() => []),
      ]);
      setIncident(inc);
      setTimeline(events || []);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchIncidentData();
  }, [id]);

  const handleStatusTransition = async (newStatus: string) => {
    if (!id) return;
    try {
      await ApiClient.patch(`/incidents/${id}/status`, { status: newStatus });
      fetchIncidentData();
    } catch (err) {
      console.error(err);
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

  if (loading && !incident) {
    return (
      <div className="py-20 text-center text-xs text-gray-500 flex items-center justify-center gap-2">
        <RefreshCw className="w-4 h-4 animate-spin text-blue-500" />
        <span>Loading incident details...</span>
      </div>
    );
  }

  if (!incident) {
    return (
      <div className="py-20 text-center text-gray-400">
        <AlertTriangle className="w-10 h-10 text-amber-500 mx-auto mb-2" />
        <h2 className="text-base font-semibold">Incident Not Found</h2>
        <Link to="/incidents" className="text-blue-400 text-xs mt-2 inline-block">
          &larr; Back to incidents
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Back button */}
      <div>
        <Link
          to="/incidents"
          className="inline-flex items-center gap-1.5 text-xs text-gray-400 hover:text-gray-200 transition"
        >
          <ArrowLeft className="w-3.5 h-3.5" />
          <span>Back to All Incidents</span>
        </Link>
      </div>

      {/* Incident Header Card */}
      <div className="p-6 rounded-xl bg-[#111827] border border-gray-800 shadow-xl space-y-4">
        <div className="flex flex-col lg:flex-row lg:items-start lg:justify-between gap-4">
          <div className="space-y-2">
            <div className="flex flex-wrap items-center gap-2">
              <span className={`px-2.5 py-0.5 rounded text-xs font-bold border ${getSeverityBadge(incident.severity)}`}>
                {incident.severity}
              </span>
              <span className="px-2.5 py-0.5 rounded-full bg-blue-500/10 text-blue-400 border border-blue-500/20 text-xs font-semibold">
                {incident.status}
              </span>
              <span className="text-xs text-gray-500 font-mono">
                ID: {incident.id?.substring(0, 8)}
              </span>
            </div>

            <h1 className="text-2xl font-bold text-white tracking-tight">{incident.title}</h1>

            <p className="text-sm text-gray-300 max-w-3xl leading-relaxed">
              {incident.summary || 'Correlated anomaly events under active investigation.'}
            </p>
          </div>

          {/* Quick Lifecycle Action Buttons */}
          <div className="flex items-center gap-2 self-start">
            {incident.status === 'DETECTED' && (
              <button
                onClick={() => handleStatusTransition('INVESTIGATING')}
                className="px-3.5 py-2 rounded-lg bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold shadow-lg shadow-blue-600/20 transition"
              >
                Acknowledge / Investigate
              </button>
            )}
            {incident.status === 'INVESTIGATING' && (
              <button
                onClick={() => handleStatusTransition('MITIGATING')}
                className="px-3.5 py-2 rounded-lg bg-amber-600 hover:bg-amber-500 text-white text-xs font-semibold transition"
              >
                Declare Mitigating
              </button>
            )}
            {incident.status === 'MITIGATING' && (
              <button
                onClick={() => handleStatusTransition('RESOLVED')}
                className="px-3.5 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-semibold transition"
              >
                Mark Resolved
              </button>
            )}
            {incident.status === 'RESOLVED' && (
              <button
                onClick={() => handleStatusTransition('CLOSED')}
                className="px-3.5 py-2 rounded-lg bg-gray-800 hover:bg-gray-700 text-gray-300 text-xs font-medium border border-gray-700 transition"
              >
                Close Incident
              </button>
            )}
          </div>
        </div>

        {/* Metadata Strip */}
        <div className="pt-4 border-t border-gray-800/80 flex flex-wrap items-center gap-6 text-xs text-gray-400">
          <div>
            <span className="text-gray-500">Root Cause Service: </span>
            <span className="font-mono text-gray-200 font-semibold">
              {incident.rootCauseServiceName || 'unknown'}
            </span>
          </div>
          <div className="flex items-center gap-1.5">
            <Clock className="w-3.5 h-3.5 text-gray-500" />
            <span className="text-gray-500">Started: </span>
            <span className="text-gray-200">
              {new Date(incident.startedAt).toLocaleString()}
            </span>
          </div>
          {incident.resolvedAt && (
            <div className="flex items-center gap-1.5 text-emerald-400">
              <CheckCircle className="w-3.5 h-3.5" />
              <span>Resolved: {new Date(incident.resolvedAt).toLocaleTimeString()}</span>
            </div>
          )}
        </div>
      </div>

      {/* Tabs Navigation */}
      <div className="flex items-center gap-2 border-b border-gray-800">
        <button
          onClick={() => setActiveTab('warroom')}
          className={`flex items-center gap-2 px-4 py-2.5 text-xs font-semibold border-b-2 transition ${
            activeTab === 'warroom'
              ? 'border-blue-500 text-blue-400'
              : 'border-transparent text-gray-400 hover:text-gray-200'
          }`}
        >
          <MessageSquare className="w-4 h-4" />
          <span>Real-Time War Room</span>
        </button>

        <button
          onClick={() => setActiveTab('ai')}
          className={`flex items-center gap-2 px-4 py-2.5 text-xs font-semibold border-b-2 transition ${
            activeTab === 'ai'
              ? 'border-purple-500 text-purple-400'
              : 'border-transparent text-gray-400 hover:text-gray-200'
          }`}
        >
          <Sparkles className="w-4 h-4" />
          <span>AI Root-Cause Investigation</span>
        </button>

        <button
          onClick={() => setActiveTab('timemachine')}
          className={`flex items-center gap-2 px-4 py-2.5 text-xs font-semibold border-b-2 transition ${
            activeTab === 'timemachine'
              ? 'border-amber-500 text-amber-400'
              : 'border-transparent text-gray-400 hover:text-gray-200'
          }`}
        >
          <RotateCcw className="w-4 h-4" />
          <span>Time Machine Replay</span>
        </button>

        <button
          onClick={() => setActiveTab('timeline')}
          className={`flex items-center gap-2 px-4 py-2.5 text-xs font-semibold border-b-2 transition ${
            activeTab === 'timeline'
              ? 'border-gray-400 text-gray-200'
              : 'border-transparent text-gray-400 hover:text-gray-200'
          }`}
        >
          <List className="w-4 h-4" />
          <span>Audit Timeline</span>
        </button>
      </div>

      {/* Active Tab Viewport */}
      <div>
        {activeTab === 'warroom' && <WarRoom incidentId={incident.id} />}
        {activeTab === 'ai' && <AiInvestigationView incidentId={incident.id} />}
        {activeTab === 'timemachine' && <TimeMachine incidentId={incident.id} />}
        {activeTab === 'timeline' && (
          <div className="p-5 rounded-xl bg-[#111827] border border-gray-800 space-y-4">
            <h3 className="text-sm font-semibold text-gray-200">Incident Event Audit Trail</h3>
            <div className="space-y-3">
              {timeline.length === 0 ? (
                <p className="text-xs text-gray-500">No events logged yet.</p>
              ) : (
                timeline.map((evt, idx) => (
                  <div key={idx} className="p-3 rounded-lg bg-gray-900/60 border border-gray-800 text-xs">
                    <div className="flex items-center justify-between text-[11px] text-gray-400 mb-1">
                      <span className="font-semibold text-blue-400">{evt.eventType}</span>
                      <span>{new Date(evt.createdAt).toLocaleString()} &bull; Actor: {evt.actor}</span>
                    </div>
                    <p className="text-gray-300">{evt.description}</p>
                  </div>
                ))
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
