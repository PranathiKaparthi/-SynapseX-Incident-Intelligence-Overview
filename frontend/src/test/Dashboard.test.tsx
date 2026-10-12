import React from 'react';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { Dashboard } from '../pages/Dashboard';
import { ApiClient } from '../api/client';

describe('SynapseX Dashboard Component', () => {
  beforeEach(() => {
    vi.spyOn(ApiClient, 'get').mockImplementation(async (endpoint: string) => {
      if (endpoint === '/dashboard/overview') {
        return {
          activeIncidents: 3,
          criticalIncidents: 1,
          eventsProcessed: 18450,
          incidentTrend: [{ day: 'Mon', incidents: 3 }],
        };
      }
      if (endpoint === '/dashboard/services') {
        return [
          { name: 'postgres-db', tier: 1, status: 'DEGRADED', serviceType: 'DATABASE' },
          { name: 'payment-service', tier: 1, status: 'FAILING', serviceType: 'MICROSERVICE' },
        ];
      }
      if (endpoint === '/incidents') {
        return {
          content: [
            {
              id: 'inc-101',
              title: 'HikariCP Pool Starvation',
              severity: 'SEV1',
              status: 'INVESTIGATING',
              rootCauseServiceName: 'postgres-db',
              startedAt: new Date().toISOString(),
            },
          ],
        };
      }
      return {};
    });
  });

  it('renders KPI metric titles and system health overview', async () => {
    render(
      <MemoryRouter>
        <Dashboard />
      </MemoryRouter>
    );

    expect(screen.getByText(/Incident Intelligence Overview/i)).toBeInTheDocument();
    expect(screen.getAllByText(/Active Incidents/i).length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText(/Degraded Services/i)).toBeInTheDocument();
    expect(screen.getByText(/Mean Time to Detect/i)).toBeInTheDocument();
    expect(screen.getByText(/Telemetry Ingested/i)).toBeInTheDocument();
  });
});
