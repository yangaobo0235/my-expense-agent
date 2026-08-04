import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PolicyCatalogPage } from './PolicyCatalogPage';

const api = vi.hoisted(() => ({
  listPolicies: vi.fn(),
  searchPolicies: vi.fn(),
}));

vi.mock('../../api/expense-api', () => api);

describe('PolicyCatalogPage', () => {
  beforeEach(() => {
    api.listPolicies.mockResolvedValue([
      {
        id: 'policy-1',
        policyCode: 'COMPETITION-TRAVEL-CN',
        name: '学生竞赛差旅经费管理办法',
        category: '竞赛差旅费',
        region: 'CN',
        applicantType: 'STUDENT',
        version: '1.0',
        effectiveFrom: '2026-01-01',
        status: 'ACTIVE',
        sourceUri: 'policy://my-expense-agent/COMPETITION-TRAVEL-CN-V1',
        chunkCount: 4,
        indexedChunkCount: 4,
        updatedAt: '2026-06-22T00:00:00Z',
      },
    ]);
  });

  it('renders policy version, validity and index progress', async () => {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={client}>
        <PolicyCatalogPage />
      </QueryClientProvider>,
    );

    expect(await screen.findByText('学生竞赛差旅经费管理办法')).toBeInTheDocument();
    expect(screen.getByText('COMPETITION-TRAVEL-CN')).toBeInTheDocument();
    expect(screen.getByText('2026-01-01 ～ 长期有效')).toBeInTheDocument();
    expect(screen.getByText('4/4')).toBeInTheDocument();
  });
});
