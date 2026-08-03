import { useMutation } from '@tanstack/react-query';
import { apiClient, CreateStrategyRequest } from '@/app/lib/api/client';

export function StrategyCreateTest() {
  const mutation = useMutation({
    mutationFn: (data: CreateStrategyRequest) => apiClient.strategies.create(data),
  });

  const handleCreate = () => {
    mutation.mutate({
      name: 'Test Strategy',
      markdownContent: `---
version: v1.0
consensus_method: WEIGHTED
evaluation_interval_seconds: 21600
---
# Test Strategy

## Rules
- Test rule 1
- Test rule 2
`,
    });
  };

  if (mutation.isPending) {
    return <div data-testid="creating">Creating...</div>;
  }

  if (mutation.isError) {
    return (
      <div data-testid="create-error">
        Error: {(mutation.error as Error).message}
      </div>
    );
  }

  if (mutation.isSuccess) {
    return (
      <div data-testid="create-success">
        <div data-testid="created-version">{mutation.data.version}</div>
        <div data-testid="created-name">{mutation.data.name}</div>
      </div>
    );
  }

  return (
    <button data-testid="create-button" onClick={handleCreate}>
      Create Strategy
    </button>
  );
}
