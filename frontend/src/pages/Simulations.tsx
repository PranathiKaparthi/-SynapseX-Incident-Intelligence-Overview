import React, { useState, useEffect } from 'react';
import {
  Play,
  Square,
  AlertTriangle,
  Database,
  Cpu,
  Clock,
  Zap,
  Activity,
  Layers,
  CheckCircle2,
  RefreshCw,
  Hash
} from 'lucide-react';
import { ApiClient } from '../api/client';

export const Simulations: React.FC = () => {
  const [status, setStatus] = useState<any>({ running: false, eventsGenerated: 0 });
  const [selectedScenario, setSelectedScenario] = useState('DATABASE_CONNECTION_POOL_EXHAUSTION');
  const [seed, setSeed] = useState(42);
  const [duration, setDuration] = useState(60);
  const [loading, setLoading] = useState(false);

  const scenarios = [
    {
      id: 'DATABASE_CONNECTION_POOL_EXHAUSTION',
      name: 'PostgreSQL Connection Exhaustion',
      service: 'postgres-db',
      icon: Database,
      severity: 'CRITICAL',
      description: 'HikariCP connection pool reaches 100% capacity. Queries time out after 30s causing cascading failures upstream in payment-service.',
      anomalies: ['dbConnUtilization > 95%', 'dbWaitTimeoutMs > 30000'],
    },
    {
      id: 'API_LATENCY_INCREASE',
      name: 'Downstream API Latency Degraded',
      service: 'payment-service',
      icon: Clock,
      severity: 'HIGH',
      description: 'Downstream payment gateway latency increases to 3.2s p99. Causes thread starvation and HTTP 504s in order-service.',
      anomalies: ['latencyMs > 2500', 'errorRate > 12%'],
    },
    {
      id: 'CPU_SPIKE',
      name: 'CPU Saturation & Throttling',
      service: 'order-service',
      icon: Cpu,
      severity: 'HIGH',
      description: 'Sudden flash traffic or regex catastrophic backtracking drives CPU utilization above 90% across order nodes.',
      anomalies: ['cpuUsage > 85%', 'throughputDrop > 40%'],
    },
    {
      id: 'MEMORY_EXHAUSTION',
      name: 'JVM Heap Exhaustion & Leak',
      service: 'auth-service',
      icon: Zap,
      severity: 'CRITICAL',
      description: 'JWT session state memory leak induces continuous Stop-The-World GC pauses, causing health checks to fail.',
      anomalies: ['memoryUsage > 92%', 'gcPauseMs > 4500'],
    },
    {
      id: 'KAFKA_CONSUMER_LAG',
      name: 'Kafka Consumer Partition Stall',
      service: 'notification-service',
      icon: Layers,
      severity: 'WARNING',
      description: 'Slow deserialization causes consumer group rebalance loop and rapid message lag accumulation.',
      anomalies: ['consumerLag > 5000', 'commitRate = 0'],
    },
    {
      id: 'NETWORK_FAILURE',
      name: 'Cascading Dependency Outage',
      service: 'postgres-db',
      icon: AlertTriangle,
      severity: 'CRITICAL',
      description: 'Database outage propagates along dependency graph: postgres-db -> payment-service -> order-service -> api-gateway.',
      anomalies: ['multipleServiceAnomalies', 'cascadingSev1Correlated'],
    },
    {
      id: 'PAYMENT_FAILURE',
      name: 'Payment Service Hard Fault',
      service: 'payment-service',
      icon: AlertTriangle,
      severity: 'CRITICAL',
      description: 'Payment microservice terminates unexpectedly, returning immediate 503 Service Unavailable to order checkout pipeline.',
      anomalies: ['errorRate = 100%', 'connectionRefused'],
    },
    {
      id: 'NORMAL_TRAFFIC',
      name: 'Normal Diurnal Baseline Noise',
      service: 'all-services',
      icon: Activity,
      severity: 'INFO',
      description: 'Generates healthy diurnal telemetry traffic with normal fluctuations. No anomalies or alerts are triggered.',
      anomalies: ['allMetricsWithinConfidenceBands'],
    },
  ];

  const fetchStatus = async () => {
    try {
      const data = await ApiClient.get('/simulations/status');
      if (data) setStatus(data);
    } catch {}
  };

  useEffect(() => {
    fetchStatus();
    const interval = setInterval(fetchStatus, 3000);
    return () => clearInterval(interval);
  }, []);

  const handleStart = async (scenarioId?: string) => {
    const sc = scenarioId || selectedScenario;
    try {
      setLoading(true);
      await ApiClient.post('/simulations/start', {
        scenario: sc,
        durationSeconds: duration,
        seed: seed,
      });
      fetchStatus();
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleStop = async () => {
    try {
      setLoading(true);
      await ApiClient.post('/simulations/stop');
      fetchStatus();
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2">
          Deterministic Failure Simulation Sandbox
        </h1>
        <p className="text-sm text-gray-400 mt-1">
          Generate realistic distributed systems telemetry and stress-test autonomous correlation &amp; multi-agent AI
        </p>
      </div>

      {/* Simulator Control Bar */}
      <div className="p-5 rounded-xl bg-[#111827] border border-gray-800 shadow-xl flex flex-col lg:flex-row lg:items-center lg:justify-between gap-6">
        <div className="flex flex-wrap items-center gap-4">
          {/* Status Badge */}
          <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-gray-900 border border-gray-800 text-xs">
            <span
              className={`w-2.5 h-2.5 rounded-full ${
                status.running ? 'bg-emerald-400 animate-ping' : 'bg-gray-500'
              }`}
            ></span>
            <span className="font-semibold text-gray-200">
              {status.running ? 'SIMULATOR ACTIVE' : 'IDLE'}
            </span>
            {status.running && (
              <span className="text-gray-400">
                &bull; {status.scenario} &bull; {status.eventsGenerated} events
              </span>
            )}
          </div>

          {/* Seed Input */}
          <div className="flex items-center gap-2 text-xs">
            <span className="text-gray-400 flex items-center gap-1">
              <Hash className="w-3.5 h-3.5" />
              <span>Seed:</span>
            </span>
            <input
              type="number"
              value={seed}
              onChange={(e) => setSeed(Number(e.target.value))}
              disabled={status.running}
              className="w-20 bg-gray-900 border border-gray-800 rounded px-2 py-1 text-xs text-gray-200 focus:outline-none focus:border-blue-500 font-mono"
            />
          </div>

          {/* Duration Slider */}
          <div className="flex items-center gap-2 text-xs">
            <span className="text-gray-400">Duration:</span>
            <input
              type="range"
              min={15}
              max={300}
              step={15}
              value={duration}
              onChange={(e) => setDuration(Number(e.target.value))}
              disabled={status.running}
              className="accent-blue-500 w-24"
            />
            <span className="font-mono text-gray-300 text-xs">{duration}s</span>
          </div>
        </div>

        {/* Action Buttons */}
        <div className="flex items-center gap-3">
          {status.running ? (
            <button
              onClick={handleStop}
              disabled={loading}
              className="flex items-center gap-1.5 px-4 py-2 rounded-lg bg-red-600 hover:bg-red-500 text-white text-xs font-semibold shadow-lg shadow-red-600/20 transition"
            >
              <Square className="w-3.5 h-3.5" />
              <span>Halt Simulation</span>
            </button>
          ) : (
            <button
              onClick={() => handleStart()}
              disabled={loading}
              className="flex items-center gap-1.5 px-4 py-2 rounded-lg bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold shadow-lg shadow-blue-600/20 transition"
            >
              <Play className="w-3.5 h-3.5" />
              <span>Start Generator</span>
            </button>
          )}

          <button
            onClick={fetchStatus}
            className="p-2 rounded-lg bg-gray-900 border border-gray-800 text-gray-400 hover:text-white transition"
            title="Refresh status"
          >
            <RefreshCw className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* 8 Failure Scenario Cards Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {scenarios.map((sc) => {
          const Icon = sc.icon;
          const isSelected = selectedScenario === sc.id;
          const isCurrentlyRunning = status.running && status.scenario === sc.id;

          return (
            <div
              key={sc.id}
              onClick={() => setSelectedScenario(sc.id)}
              className={`p-4 rounded-xl border transition cursor-pointer flex flex-col justify-between ${
                isCurrentlyRunning
                  ? 'bg-red-950/20 border-red-500/50 shadow-lg shadow-red-500/10'
                  : isSelected
                  ? 'bg-[#111827] border-blue-500/60 shadow-lg shadow-blue-500/10'
                  : 'bg-[#111827] border-gray-800 hover:border-gray-700'
              }`}
            >
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <div className="w-8 h-8 rounded-lg bg-gray-900 border border-gray-800 flex items-center justify-center text-blue-400">
                    <Icon className="w-4 h-4" />
                  </div>
                  <span
                    className={`px-2 py-0.5 rounded text-[10px] font-bold border ${
                      sc.severity === 'CRITICAL'
                        ? 'bg-red-500/10 text-red-400 border-red-500/30'
                        : sc.severity === 'HIGH'
                        ? 'bg-amber-500/10 text-amber-400 border-amber-500/30'
                        : sc.severity === 'WARNING'
                        ? 'bg-yellow-500/10 text-yellow-400 border-yellow-500/30'
                        : 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30'
                    }`}
                  >
                    {sc.severity}
                  </span>
                </div>

                <div>
                  <h3 className="font-semibold text-gray-100 text-xs">{sc.name}</h3>
                  <p className="font-mono text-[11px] text-gray-500 mt-0.5">Target: {sc.service}</p>
                </div>

                <p className="text-gray-400 text-[11px] leading-relaxed line-clamp-3">
                  {sc.description}
                </p>

                <div className="space-y-1">
                  <span className="text-[10px] text-gray-500 font-semibold block">Expected Anomalies:</span>
                  <div className="flex flex-wrap gap-1">
                    {sc.anomalies.map((a) => (
                      <span
                        key={a}
                        className="px-1.5 py-0.5 rounded bg-gray-900 border border-gray-800 text-gray-400 font-mono text-[9px]"
                      >
                        {a}
                      </span>
                    ))}
                  </div>
                </div>
              </div>

              <div className="mt-4 pt-3 border-t border-gray-800/80 flex items-center justify-between">
                <span className="text-[10px] text-gray-500">Seedable RNG</span>
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    setSelectedScenario(sc.id);
                    handleStart(sc.id);
                  }}
                  disabled={status.running}
                  className="px-2.5 py-1 rounded bg-gray-800 hover:bg-gray-700 disabled:opacity-30 text-gray-200 text-[11px] font-medium border border-gray-700/60 transition flex items-center gap-1"
                >
                  <Play className="w-3 h-3 text-blue-400" />
                  <span>Launch</span>
                </button>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
