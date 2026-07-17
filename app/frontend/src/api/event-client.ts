import { ExpenseWorkflowEvent } from './contracts';
import { getAccessToken } from './http-client';

interface EventStreamOptions {
  caseId: string;
  signal: AbortSignal;
  onEvent: (event: ExpenseWorkflowEvent) => void;
  onResetRequired: () => void;
}

export async function consumeCaseEvents({
  caseId,
  signal,
  onEvent,
  onResetRequired,
}: EventStreamOptions) {
  let lastEventId = sessionStorage.getItem(`my-expense-agent-event:${caseId}`) ?? undefined;
  let lastSequence = 0;
  const seen = new Set<string>();

  while (!signal.aborted) {
    const token = await getAccessToken();
    const response = await fetch(
      `${import.meta.env.VITE_API_BASE_URL ?? ''}/api/v1/fund-applications/${caseId}/events`,
      {
        headers: {
          Accept: 'text/event-stream',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
          ...(lastEventId ? { 'Last-Event-ID': lastEventId } : {}),
        },
        signal,
      },
    );
    if (response.status === 422) {
      sessionStorage.removeItem(`my-expense-agent-event:${caseId}`);
      lastEventId = undefined;
      onResetRequired();
      continue;
    }
    if (!response.ok || !response.body) {
      throw new Error(`事件流连接失败：HTTP ${response.status}`);
    }
    const text = await response.text();
    for (const block of text.split(/\r?\n\r?\n/)) {
      const id = block.match(/^id:\s*(.+)$/m)?.[1];
      const data = block.match(/^data:\s*(.+)$/m)?.[1];
      if (!id || !data || seen.has(id)) continue;
      const event = JSON.parse(data) as ExpenseWorkflowEvent;
      if (lastSequence > 0 && event.sequence > lastSequence + 1) onResetRequired();
      seen.add(id);
      lastSequence = Math.max(lastSequence, event.sequence);
      lastEventId = id;
      sessionStorage.setItem(`my-expense-agent-event:${caseId}`, id);
      onEvent(event);
    }
    await new Promise((resolve) => setTimeout(resolve, 1500));
  }
}
