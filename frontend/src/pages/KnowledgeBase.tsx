import React, { useState, useEffect } from 'react';
import {
  BookOpen,
  Search,
  Sparkles,
  CheckCircle,
  FileText,
  Sliders,
  Plus,
  RefreshCw,
  ExternalLink
} from 'lucide-react';
import { ApiClient } from '../api/client';

export const KnowledgeBase: React.FC = () => {
  const [query, setQuery] = useState('HikariCP connection pool timeout');
  const [cutoff, setCutoff] = useState(0.70);
  const [matches, setMatches] = useState<any[]>([]);
  const [allKnowledge, setAllKnowledge] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [isSearching, setIsSearching] = useState(false);

  // New post-mortem form modal
  const [showModal, setShowModal] = useState(false);
  const [newTitle, setNewTitle] = useState('');
  const [newSummary, setNewSummary] = useState('');
  const [newRootCause, setNewRootCause] = useState('');
  const [newProcedure, setNewProcedure] = useState('');

  const fetchAll = async () => {
    try {
      setLoading(true);
      const data = await ApiClient.get('/knowledge');
      setAllKnowledge(data || []);
    } catch {} finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAll();
    handleSearch('HikariCP connection pool timeout');
  }, []);

  const handleSearch = async (searchQuery?: string) => {
    const q = searchQuery !== undefined ? searchQuery : query;
    if (!q.trim()) return;

    try {
      setIsSearching(true);
      const results = await ApiClient.get('/knowledge/search', {
        query: q,
        cutoff: cutoff,
      });
      setMatches(results || []);
    } catch (err) {
      console.error(err);
    } finally {
      setIsSearching(false);
    }
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await ApiClient.post('/knowledge', {
        title: newTitle,
        summary: newSummary,
        rootCause: newRootCause,
        resolutionProcedure: newProcedure,
      });
      setShowModal(false);
      setNewTitle('');
      setNewSummary('');
      setNewRootCause('');
      setNewProcedure('');
      fetchAll();
    } catch (err) {
      console.error(err);
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2">
            RAG Incident Memory &amp; Runbook Knowledge Base
          </h1>
          <p className="text-sm text-gray-400 mt-1">
            Semantic vector embeddings (pgvector) over past incident post-mortems and verified remediation procedures
          </p>
        </div>

        <button
          onClick={() => setShowModal(true)}
          className="flex items-center gap-1.5 px-3.5 py-2 rounded-lg bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold shadow-lg shadow-blue-600/20 transition self-start sm:self-auto"
        >
          <Plus className="w-4 h-4" />
          <span>Index Post-Mortem</span>
        </button>
      </div>

      {/* Semantic Search Box */}
      <div className="p-5 rounded-xl bg-[#111827] border border-gray-800 shadow-xl space-y-4">
        <div className="flex flex-col md:flex-row items-center gap-3">
          <div className="relative flex-1 w-full">
            <Search className="w-4 h-4 text-gray-500 absolute left-3.5 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
              placeholder="Query semantic memory (e.g. 'HikariCP pool timeout', 'Kafka consumer lag rebalance')..."
              className="w-full bg-gray-900 border border-gray-800 rounded-lg pl-10 pr-4 py-2.5 text-xs text-gray-100 placeholder-gray-500 focus:outline-none focus:border-blue-500 transition"
            />
          </div>

          <div className="flex items-center gap-3 w-full md:w-auto justify-between md:justify-start">
            <div className="flex items-center gap-2 text-xs">
              <Sliders className="w-3.5 h-3.5 text-gray-400" />
              <span className="text-gray-400">Cutoff:</span>
              <input
                type="range"
                min={0.50}
                max={0.95}
                step={0.05}
                value={cutoff}
                onChange={(e) => setCutoff(Number(e.target.value))}
                className="accent-blue-500 w-20"
              />
              <span className="font-mono text-gray-300 text-xs">{cutoff.toFixed(2)}</span>
            </div>

            <button
              onClick={() => handleSearch()}
              disabled={isSearching}
              className="flex items-center gap-1.5 px-4 py-2.5 rounded-lg bg-purple-600 hover:bg-purple-500 text-white text-xs font-semibold transition"
            >
              {isSearching ? <RefreshCw className="w-3.5 h-3.5 animate-spin" /> : <Sparkles className="w-3.5 h-3.5" />}
              <span>Vector Search</span>
            </button>
          </div>
        </div>

        {/* Quick Suggestion Chips */}
        <div className="flex items-center gap-2 overflow-x-auto text-[11px] pt-1">
          <span className="text-gray-500 whitespace-nowrap">Try queries:</span>
          {[
            'HikariCP connection pool timeout',
            'Cascading 504 gateway timeout',
            'Kafka consumer lag rebalance loop',
            'JVM OutOfMemoryError heap exhaustion',
          ].map((chip) => (
            <button
              key={chip}
              onClick={() => {
                setQuery(chip);
                handleSearch(chip);
              }}
              className="px-2.5 py-0.5 rounded-full bg-gray-900 border border-gray-800 text-gray-300 hover:border-gray-700 whitespace-nowrap transition"
            >
              {chip}
            </button>
          ))}
        </div>
      </div>

      {/* Semantic Matches Grid */}
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-semibold text-gray-200">
            Semantic Vector Matches ({matches.length} found with cosine similarity &ge; {cutoff})
          </h2>
          {isSearching && <RefreshCw className="w-4 h-4 text-purple-400 animate-spin" />}
        </div>

        {matches.length === 0 ? (
          <div className="p-12 rounded-xl bg-[#111827] border border-gray-800 text-center space-y-2">
            <BookOpen className="w-8 h-8 text-gray-600 mx-auto" />
            <p className="text-xs text-gray-400">
              No previous post-mortems meet the similarity cutoff ({cutoff}). Try lowering the threshold or refining keywords.
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {matches.map((m, i) => {
              const k = m.knowledge;
              const simPct = Math.round(m.similarityScore * 100);

              return (
                <div
                  key={k.id || i}
                  className="p-5 rounded-xl bg-[#111827] border border-gray-800 shadow-md space-y-3 flex flex-col justify-between"
                >
                  <div className="space-y-2">
                    <div className="flex items-start justify-between gap-2">
                      <h3 className="font-semibold text-gray-100 text-sm">{k.title}</h3>
                      <span className="px-2.5 py-0.5 rounded-full bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 text-[10px] font-extrabold whitespace-nowrap">
                        {simPct}% Similarity
                      </span>
                    </div>

                    <p className="text-gray-300 text-xs leading-relaxed">{k.summary}</p>

                    <div className="pt-2 border-t border-gray-800/80 space-y-1.5 text-xs">
                      <div>
                        <span className="font-semibold text-red-400 block text-[11px]">Root Cause:</span>
                        <p className="text-gray-400 text-[11px] leading-relaxed">{k.rootCause}</p>
                      </div>

                      <div>
                        <span className="font-semibold text-emerald-400 block text-[11px]">Remediation Procedure:</span>
                        <p className="text-gray-400 text-[11px] leading-relaxed">{k.resolutionProcedure}</p>
                      </div>
                    </div>
                  </div>

                  <div className="pt-3 border-t border-gray-800 text-[10px] text-gray-500 flex items-center justify-between">
                    <span>Indexed in pgvector memory</span>
                    <span className="font-mono">Score: {m.similarityScore.toFixed(3)}</span>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Index Post-Mortem Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black/70 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-[#111827] border border-gray-800 rounded-2xl w-full max-w-lg p-6 shadow-2xl space-y-4">
            <h3 className="text-base font-bold text-gray-100">Index Resolved Incident Post-Mortem</h3>
            <form onSubmit={handleCreate} className="space-y-3 text-xs">
              <div>
                <label className="block text-gray-300 font-semibold mb-1">Incident Title</label>
                <input
                  type="text"
                  required
                  value={newTitle}
                  onChange={(e) => setNewTitle(e.target.value)}
                  className="w-full bg-gray-900 border border-gray-800 rounded-lg p-2 text-gray-200"
                  placeholder="e.g. HikariCP connection starvation during flash sale"
                />
              </div>

              <div>
                <label className="block text-gray-300 font-semibold mb-1">Executive Summary</label>
                <textarea
                  required
                  rows={2}
                  value={newSummary}
                  onChange={(e) => setNewSummary(e.target.value)}
                  className="w-full bg-gray-900 border border-gray-800 rounded-lg p-2 text-gray-200"
                  placeholder="Describe impact, services affected, and duration..."
                />
              </div>

              <div>
                <label className="block text-gray-300 font-semibold mb-1">Root Cause Explanation</label>
                <textarea
                  required
                  rows={2}
                  value={newRootCause}
                  onChange={(e) => setNewRootCause(e.target.value)}
                  className="w-full bg-gray-900 border border-gray-800 rounded-lg p-2 text-gray-200"
                  placeholder="Exact technical failure mechanism..."
                />
              </div>

              <div>
                <label className="block text-gray-300 font-semibold mb-1">Resolution Runbook Procedure</label>
                <textarea
                  required
                  rows={2}
                  value={newProcedure}
                  onChange={(e) => setNewProcedure(e.target.value)}
                  className="w-full bg-gray-900 border border-gray-800 rounded-lg p-2 text-gray-200"
                  placeholder="Step-by-step actions that resolved the incident..."
                />
              </div>

              <div className="flex items-center justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setShowModal(false)}
                  className="px-3 py-1.5 rounded-lg bg-gray-800 text-gray-300 hover:bg-gray-700"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-1.5 rounded-lg bg-blue-600 text-white font-semibold hover:bg-blue-500"
                >
                  Save &amp; Embed
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
