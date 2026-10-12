import React, { useState, useEffect } from 'react';
import {
  Sparkles,
  ShieldCheck,
  AlertOctagon,
  FileText,
  CheckCircle2,
  RefreshCw,
  Cpu,
  Layers,
  History,
  Info
} from 'lucide-react';
import { ApiClient } from '../api/client';

interface AiInvestigationViewProps {
  incidentId: string;
}

export const AiInvestigationView: React.FC<AiInvestigationViewProps> = ({ incidentId }) => {
  const [investigation, setInvestigation] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [investigating, setInvestigating] = useState(false);

  const fetchInvestigation = async () => {
    try {
      setLoading(true);
      const data = await ApiClient.get(`/incidents/${incidentId}/investigation`);
      setInvestigation(data);
    } catch {
      // none yet
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchInvestigation();
  }, [incidentId]);

  const handleRunInvestigation = async () => {
    try {
      setInvestigating(true);
      const result = await ApiClient.post(`/incidents/${incidentId}/investigate`);
      setInvestigation(result);
    } catch (err) {
      console.error('Failed to trigger investigation:', err);
    } finally {
      setInvestigating(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Investigation Controls & Header */}
      <div className="p-5 rounded-xl bg-[#111827] border border-gray-800 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-base font-bold text-gray-100">Multi-Agent AI Root-Cause Investigation</h2>
            {investigation?.mode === 'DEMO' && (
              <span className="px-2.5 py-0.5 rounded-full bg-amber-500/20 text-amber-400 border border-amber-500/30 text-[10px] font-extrabold tracking-wide">
                DEMO — not a real AI result
              </span>
            )}
            {investigation?.mode === 'LIVE' && (
              <span className="px-2.5 py-0.5 rounded-full bg-purple-500/20 text-purple-400 border border-purple-500/30 text-[10px] font-extrabold tracking-wide">
                LIVE AI (OpenAI / Ollama)
              </span>
            )}
          </div>
          <p className="text-xs text-gray-400 mt-1">
            Parallel subagents analyze metrics, logs, service topologies, and RAG knowledge memories.
          </p>
        </div>

        <button
          onClick={handleRunInvestigation}
          disabled={investigating}
          className="flex items-center gap-2 px-4 py-2.5 rounded-lg bg-gradient-to-r from-purple-600 to-indigo-600 hover:from-purple-500 hover:to-indigo-500 disabled:opacity-50 text-white text-xs font-semibold shadow-lg shadow-purple-600/20 transition self-start sm:self-auto"
        >
          {investigating ? (
            <>
              <RefreshCw className="w-4 h-4 animate-spin" />
              <span>Agents Investigating...</span>
            </>
          ) : (
            <>
              <Sparkles className="w-4 h-4" />
              <span>Execute AI Investigation</span>
            </>
          )}
        </button>
      </div>

      {loading ? (
        <div className="p-12 text-center text-xs text-gray-500">Loading AI investigation data...</div>
      ) : !investigation ? (
        <div className="p-12 rounded-xl bg-[#111827] border border-gray-800 text-center space-y-3">
          <Cpu className="w-10 h-10 text-gray-600 mx-auto" />
          <h3 className="text-sm font-semibold text-gray-300">No Investigation Run Yet</h3>
          <p className="text-xs text-gray-500 max-w-md mx-auto">
            Click 'Execute AI Investigation' above to deploy parallel subagents that correlate raw telemetry, trace upstream bottlenecks, and query past post-mortems.
          </p>
        </div>
      ) : (
        <div className="space-y-6">
          {/* Transparency Disclaimer Notice */}
          {investigation.mode === 'DEMO' && (
            <div className="p-3.5 rounded-lg bg-amber-950/30 border border-amber-800/40 flex items-start gap-3 text-xs text-amber-300">
              <Info className="w-4 h-4 text-amber-400 shrink-0 mt-0.5" />
              <div>
                <p className="font-semibold">Demo Mode Disclosure</p>
                <p className="text-amber-300/80 mt-0.5">
                  External AI API keys were not configured; this investigation was produced deterministically by SynapseX's DemoProvider. All evidence citations map to real database event records.
                </p>
              </div>
            </div>
          )}

          {/* Subagents Grid */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
            {[
              { name: 'Metric Anomaly Agent', icon: Cpu, desc: 'Evaluated CPU, memory, & latency sliding windows' },
              { name: 'Log Analyzer Agent', icon: FileText, desc: 'Extracted connection pool exceptions & timeouts' },
              { name: 'Topology Tracer Agent', icon: Layers, desc: 'Traversed service graph BFS reachability' },
              { name: 'RAG Memory Agent', icon: History, desc: 'Compared embeddings with past post-mortems' },
            ].map((ag) => {
              const Icon = ag.icon;
              return (
                <div key={ag.name} className="p-3.5 rounded-xl bg-gray-900/60 border border-gray-800 text-xs">
                  <div className="flex items-center gap-2 mb-1">
                    <Icon className="w-4 h-4 text-purple-400" />
                    <span className="font-semibold text-gray-200">{ag.name}</span>
                  </div>
                  <p className="text-gray-400 text-[11px]">{ag.desc}</p>
                  <div className="mt-2 flex items-center gap-1 text-[10px] text-emerald-400 font-medium">
                    <CheckCircle2 className="w-3 h-3" />
                    <span>Completed &bull; Validated</span>
                  </div>
                </div>
              );
            })}
          </div>

          {/* Root-Cause Hypotheses */}
          <div className="p-5 rounded-xl bg-[#111827] border border-gray-800 space-y-4">
            <h3 className="text-sm font-semibold text-gray-200 flex items-center gap-2">
              <ShieldCheck className="w-4 h-4 text-emerald-400" />
              <span>Root-Cause Hypotheses &amp; Verified Evidence</span>
            </h3>

            <div className="space-y-3">
              {(investigation.hypotheses || []).map((hyp: any, i: number) => (
                <div
                  key={i}
                  className="p-4 rounded-xl bg-gray-900/70 border border-gray-800 space-y-2 text-xs"
                >
                  <div className="flex items-center justify-between gap-2">
                    <h4 className="font-semibold text-gray-100 text-sm">{hyp.title}</h4>
                    <span className="px-2.5 py-0.5 rounded-full bg-purple-500/20 text-purple-300 border border-purple-500/30 text-[10px] font-bold">
                      Confidence: {Math.round(hyp.confidence * 100)}%
                    </span>
                  </div>

                  <p className="text-gray-300 leading-relaxed">{hyp.explanation}</p>

                  {/* Supporting Evidence Citations */}
                  <div className="pt-2 border-t border-gray-800/80">
                    <span className="text-[10px] text-gray-400 font-semibold uppercase tracking-wider block mb-1">
                      Cited Telemetry Evidence (Verified against DB):
                    </span>
                    <div className="flex flex-wrap gap-1.5">
                      {(hyp.evidenceCitations || []).map((cite: string) => (
                        <span
                          key={cite}
                          className="px-2 py-0.5 rounded bg-blue-950/40 border border-blue-800/50 text-blue-300 font-mono text-[10px]"
                        >
                          {cite}
                        </span>
                      ))}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Actionable Recommendations */}
          <div className="p-5 rounded-xl bg-[#111827] border border-gray-800 space-y-3">
            <h3 className="text-sm font-semibold text-gray-200 flex items-center gap-2">
              <AlertOctagon className="w-4 h-4 text-blue-400" />
              <span>Recommended Remediation &amp; Mitigation</span>
            </h3>

            <div className="space-y-2">
              {(investigation.recommendations || []).map((rec: string, i: number) => (
                <div
                  key={i}
                  className="p-3 rounded-lg bg-gray-900/50 border border-gray-800/80 flex items-start gap-2.5 text-xs text-gray-300"
                >
                  <span className="w-5 h-5 rounded-full bg-blue-600/20 text-blue-400 font-bold flex items-center justify-center shrink-0 text-[11px]">
                    {i + 1}
                  </span>
                  <span className="leading-relaxed">{rec}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
