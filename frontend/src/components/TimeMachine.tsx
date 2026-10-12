import React, { useState, useEffect } from 'react';
import {
  Play,
  Pause,
  RotateCcw,
  FastForward,
  Activity,
  AlertTriangle,
  MessageSquare,
  GitCommit,
  ArrowRight,
  TrendingUp,
  Server
} from 'lucide-react';
import { ApiClient } from '../api/client';

interface TimeMachineProps {
  incidentId: string;
}

export const TimeMachine: React.FC<TimeMachineProps> = ({ incidentId }) => {
  const [events, setEvents] = useState<any[]>([]);
  const [whatChanged, setWhatChanged] = useState<any>(null);
  const [loading, setLoading] = useState(true);

  // Playback state
  const [currentIndex, setCurrentIndex] = useState(0);
  const [isPlaying, setIsPlaying] = useState(false);
  const [playbackSpeed, setPlaybackSpeed] = useState(1);

  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);
        const [replayStream, diff] = await Promise.all([
          ApiClient.get(`/incidents/${incidentId}/replay`).catch(() => []),
          ApiClient.get(`/incidents/${incidentId}/what-changed`, { baselineMinutes: 30 }).catch(() => null),
        ]);
        setEvents(replayStream || []);
        setWhatChanged(diff);
        setCurrentIndex((replayStream || []).length); // default to end
      } catch (err) {
        console.error(err);
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, [incidentId]);

  // Playback timer effect
  useEffect(() => {
    let timer: any;
    if (isPlaying && currentIndex < events.length) {
      const intervalMs = Math.max(100, Math.floor(1000 / playbackSpeed));
      timer = setInterval(() => {
        setCurrentIndex((prev) => {
          if (prev >= events.length) {
            setIsPlaying(false);
            return prev;
          }
          return prev + 1;
        });
      }, intervalMs);
    } else if (currentIndex >= events.length) {
      setIsPlaying(false);
    }
    return () => clearInterval(timer);
  }, [isPlaying, currentIndex, events.length, playbackSpeed]);

  const visibleEvents = events.slice(0, currentIndex);
  const currentEvent = events[currentIndex - 1] || events[0];

  const getEventIcon = (type: string) => {
    switch (type) {
      case 'TELEMETRY':
        return Activity;
      case 'ANOMALY':
        return AlertTriangle;
      case 'WAR_ROOM_MESSAGE':
        return MessageSquare;
      default:
        return GitCommit;
    }
  };

  const getEventBadge = (type: string) => {
    switch (type) {
      case 'TELEMETRY':
        return 'bg-blue-500/10 text-blue-400 border-blue-500/20';
      case 'ANOMALY':
        return 'bg-red-500/10 text-red-400 border-red-500/20';
      case 'WAR_ROOM_MESSAGE':
        return 'bg-purple-500/10 text-purple-400 border-purple-500/20';
      default:
        return 'bg-gray-700/50 text-gray-300 border-gray-600/40';
    }
  };

  return (
    <div className="space-y-6">
      {/* Time Machine Playback Scrubber Bar */}
      <div className="p-5 rounded-xl bg-[#111827] border border-gray-800 shadow-xl space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <h3 className="text-sm font-semibold text-gray-200">Incident Time Machine Replay</h3>
            <p className="text-xs text-gray-400">
              Deterministic ordered playback sorted by (timestamp ASC, sequenceId ASC)
            </p>
          </div>

          <div className="flex items-center gap-3">
            {/* Speed Selector */}
            <div className="flex items-center gap-1 bg-gray-900 border border-gray-800 rounded-lg p-1 text-xs text-gray-300">
              <FastForward className="w-3.5 h-3.5 text-gray-400 ml-1" />
              {[1, 2, 5, 10].map((spd) => (
                <button
                  key={spd}
                  onClick={() => setPlaybackSpeed(spd)}
                  className={`px-2 py-0.5 rounded text-[11px] font-medium transition ${
                    playbackSpeed === spd ? 'bg-blue-600 text-white' : 'hover:text-white'
                  }`}
                >
                  {spd}x
                </button>
              ))}
            </div>

            {/* Play / Pause / Reset */}
            <div className="flex items-center gap-1.5">
              <button
                onClick={() => setCurrentIndex(0)}
                className="p-2 rounded-lg bg-gray-900 border border-gray-800 text-gray-400 hover:text-white transition"
                title="Rewind to start"
              >
                <RotateCcw className="w-4 h-4" />
              </button>
              <button
                onClick={() => setIsPlaying(!isPlaying)}
                className="px-3.5 py-2 rounded-lg bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold flex items-center gap-1.5 shadow-lg shadow-blue-600/20 transition"
              >
                {isPlaying ? <Pause className="w-3.5 h-3.5" /> : <Play className="w-3.5 h-3.5" />}
                <span>{isPlaying ? 'Pause' : 'Play Replay'}</span>
              </button>
            </div>
          </div>
        </div>

        {/* Scrubber Range Slider */}
        <div className="space-y-1.5">
          <input
            type="range"
            min={0}
            max={events.length}
            value={currentIndex}
            onChange={(e) => {
              setIsPlaying(false);
              setCurrentIndex(Number(e.target.value));
            }}
            className="w-full accent-blue-500 bg-gray-800 h-2 rounded-lg cursor-pointer"
          />
          <div className="flex items-center justify-between text-[11px] text-gray-500 font-mono">
            <span>Start: {events[0]?.timestamp ? new Date(events[0].timestamp).toLocaleTimeString() : '00:00:00'}</span>
            <span className="text-blue-400 font-semibold">
              Event {currentIndex} of {events.length} &bull; {currentEvent?.timestamp ? new Date(currentEvent.timestamp).toLocaleTimeString() : ''}
            </span>
            <span>End: {events[events.length - 1]?.timestamp ? new Date(events[events.length - 1].timestamp).toLocaleTimeString() : '00:00:00'}</span>
          </div>
        </div>
      </div>

      {/* "What Changed?" Baseline vs Incident Window Analysis */}
      {whatChanged && (
        <div className="p-5 rounded-xl bg-[#111827] border border-gray-800 space-y-4">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-semibold text-gray-200 flex items-center gap-2">
              <TrendingUp className="w-4 h-4 text-amber-400" />
              <span>"What Changed?" &mdash; Baseline vs Incident Window</span>
            </h3>
            <span className="text-xs text-gray-500">30-min Pre-Incident Baseline Diff</span>
          </div>

          {/* Metric Deltas Grid */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
            {Object.entries(whatChanged.metricDeltas || {}).map(([metric, d]: [string, any]) => {
              const isWorse = d.deltaPercentage > 0;
              return (
                <div key={metric} className="p-3.5 rounded-lg bg-gray-900/60 border border-gray-800 text-xs">
                  <span className="font-semibold text-gray-300 capitalize">{metric}</span>
                  <div className="mt-2 flex items-baseline justify-between">
                    <span className="text-gray-400 text-[11px]">
                      Base: <span className="font-mono text-gray-300">{d.baselineAvg}</span>
                    </span>
                    <ArrowRight className="w-3 h-3 text-gray-600" />
                    <span className="text-gray-400 text-[11px]">
                      Peak: <span className="font-mono text-white font-bold">{d.incidentAvg}</span>
                    </span>
                  </div>
                  <div className="mt-2 text-[11px]">
                    <span className={`font-semibold ${isWorse ? 'text-red-400' : 'text-emerald-400'}`}>
                      {d.deltaPercentage > 0 ? `+${d.deltaPercentage}%` : `${d.deltaPercentage}%`}
                    </span>
                    <span className="text-gray-500 ml-1">({d.deltaAbsolute > 0 ? `+${d.deltaAbsolute}` : d.deltaAbsolute})</span>
                  </div>
                </div>
              );
            })}
          </div>

          {/* Dependency Health Status */}
          <div className="pt-3 border-t border-gray-800 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-2">
            {Object.entries(whatChanged.dependencyHealth || {}).map(([srv, health]: [string, any]) => (
              <div key={srv} className="p-2.5 rounded bg-gray-900/40 border border-gray-800 flex items-center justify-between text-xs">
                <div className="flex items-center gap-1.5">
                  <Server className="w-3.5 h-3.5 text-gray-400" />
                  <span className="font-mono text-[11px] text-gray-300">{srv}</span>
                </div>
                <span className={`text-[10px] font-bold ${health.includes('FAILING') || health.includes('DEGRADED') ? 'text-red-400' : 'text-emerald-400'}`}>
                  {health}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Chronological Stream of Visible Events */}
      <div className="p-5 rounded-xl bg-[#111827] border border-gray-800 space-y-3">
        <h3 className="text-sm font-semibold text-gray-200">
          Deterministic Playback Stream ({visibleEvents.length} events emitted)
        </h3>

        <div className="max-h-96 overflow-y-auto space-y-2 pr-1 divide-y divide-gray-800/40">
          {visibleEvents.length === 0 ? (
            <div className="py-8 text-center text-xs text-gray-500">
              Rewound to start. Press Play or drag scrubber to replay.
            </div>
          ) : (
            visibleEvents.map((evt) => {
              const Icon = getEventIcon(evt.eventType);
              return (
                <div key={evt.sequenceId} className="pt-2 flex items-start justify-between gap-3 text-xs">
                  <div className="flex items-start gap-2.5">
                    <div className="w-6 h-6 rounded bg-gray-900 border border-gray-800 flex items-center justify-center text-gray-400 shrink-0 mt-0.5">
                      <Icon className="w-3.5 h-3.5" />
                    </div>
                    <div>
                      <div className="flex items-center gap-2">
                        <span className={`px-1.5 py-0.2 rounded text-[10px] font-bold border ${getEventBadge(evt.eventType)}`}>
                          {evt.eventType}
                        </span>
                        <span className="font-semibold text-gray-300">{evt.source}</span>
                        <span className="text-[10px] text-gray-500 font-mono">
                          #{evt.sequenceId} &bull; {new Date(evt.timestamp).toLocaleTimeString()}
                        </span>
                      </div>
                      <p className="text-gray-400 text-xs mt-0.5">{evt.summary}</p>
                    </div>
                  </div>
                  <span className="text-[10px] text-gray-500 font-mono shrink-0">
                    {evt.eventId?.substring(0, 12)}
                  </span>
                </div>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
};
