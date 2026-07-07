import { afterEach, describe, expect, it, vi } from 'vitest';
import { consumeCaseEvents } from './event-client';

describe('consumeCaseEvents', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    sessionStorage.clear();
  });

  it('clears stale cursor and reconnects after server requests reset', async () => {
    sessionStorage.setItem('expense-event:case-1', 'stale-event');
    const onResetRequired = vi.fn();
    const onEvent = vi.fn();
    const controller = new AbortController();
    let calls = 0;
    vi.stubGlobal('fetch', vi.fn(async () => {
      calls += 1;
      if (calls === 1) {
        return new Response(null, { status: 422 });
      }
      controller.abort();
      return new Response(
        [
          'id: event-2',
          'data: {"eventId":"event-2","caseId":"case-1","type":"WORKFLOW_RESUMED","sequence":2,"occurredAt":"2026-06-22T00:00:00Z","payload":{}}',
          '',
          '',
        ].join('\n'),
        { status: 200, headers: { 'Content-Type': 'text/event-stream' } },
      );
    }));

    await consumeCaseEvents({
      caseId: 'case-1',
      signal: controller.signal,
      onEvent,
      onResetRequired,
    });

    expect(onResetRequired).toHaveBeenCalledTimes(1);
    expect(onEvent).toHaveBeenCalledWith(expect.objectContaining({ eventId: 'event-2' }));
    expect(sessionStorage.getItem('expense-event:case-1')).toBe('event-2');
  });
});
