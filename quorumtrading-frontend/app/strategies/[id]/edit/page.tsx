'use client';

import { useState, useEffect, useMemo, useCallback, useRef } from 'react';
import { useRouter, useParams } from 'next/navigation';
import styled from 'styled-components';
import type { editor } from 'monaco-editor';
import { DashboardLayout } from '@/app/components/layouts/DashboardLayout';
import { TerminalPane } from '@/app/components/terminal/TerminalPane';
import { TerminalButton } from '@/app/components/terminal/TerminalButton';
import { TerminalText } from '@/app/components/terminal/TerminalText';
import { TerminalSkeleton } from '@/app/components/terminal/TerminalSkeleton';
import { MarkdownEditor } from '@/app/components/editor/MarkdownEditor';
import { LintingPanel } from '@/app/components/strategy/LintingPanel';
import { CheatsheetPanel } from '@/app/components/strategy/CheatsheetPanel';
import { useStrategy, useUpdateStrategy, useSimulations, useStrategySchema } from '@/app/lib/api/hooks';
import { DeleteStrategyModal } from '@/app/components/strategies/DeleteStrategyModal';
import { validateStrategy } from '@/app/lib/validation/strategyValidator';
import type { LintDiagnostic } from '@/app/lib/types/schema';
import toast from 'react-hot-toast';
import { fadeInUp, shadows, textShadows } from '@/app/styles/animations';

const EditorContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.lg};
  height: calc(100vh - 120px);
  animation: ${fadeInUp} 0.5s ease-out;

  @media (max-width: 480px) {
    height: auto;
    gap: ${p => p.theme.spacing.md};
  }
`;

const EditorHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: ${p => p.theme.spacing.md};

  @media (max-width: 480px) {
    flex-direction: column;
    align-items: flex-start;
  }
`;

const HeaderInfo = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.xs};
  flex: 1;
  min-width: 0;
`;

const StrategyNameInput = styled.input`
  font-size: ${p => p.theme.typography.fontSize.xl};
  margin: 0;
  color: ${p => p.theme.colors.primary};
  text-transform: uppercase;
  text-shadow: ${textShadows.primary};
  background: transparent;
  border: 1px solid transparent;
  border-radius: ${p => p.theme.borderRadius.sm};
  padding: ${p => p.theme.spacing.xs} ${p => p.theme.spacing.sm};
  width: 100%;
  font-family: inherit;
  font-weight: inherit;

  &:hover {
    border-color: ${p => p.theme.colors.primary}40;
  }
  &:focus {
    outline: none;
    border-color: ${p => p.theme.colors.primary};
    background: ${p => p.theme.colors.surface};
  }
`;

const StrategyMeta = styled.div`
  display: flex;
  gap: ${p => p.theme.spacing.md};
  align-items: center;
`;

const HeaderActions = styled.div`
  display: flex;
  gap: ${p => p.theme.spacing.sm};
  align-items: center;

  @media (max-width: 480px) {
    flex-wrap: wrap;
  }
`;

const EditorPane = styled(TerminalPane)`
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  box-shadow: ${shadows.card};
  animation: ${fadeInUp} 0.5s ease-out 0.1s both;

  & > div:last-child {
    flex: 1;
    display: flex;
    flex-direction: column;
  }
`;

const LoadingContainer = styled.div`
  padding: ${p => p.theme.spacing.xl};
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.md};
`;

const ErrorContainer = styled.div`
  padding: ${p => p.theme.spacing.xl};
`;

const HelpText = styled(TerminalText)`
  margin-top: ${p => p.theme.spacing.md};
  padding: ${p => p.theme.spacing.md};
  background-color: ${p => p.theme.colors.surface};
  border-left: 3px solid ${p => p.theme.colors.info};
  border-radius: ${p => p.theme.borderRadius.sm};
`;

const EditorWithPanel = styled.div`
  display: grid;
  grid-template-columns: 1fr 380px;
  gap: ${p => p.theme.spacing.lg};
  flex: 1;
  min-height: 0;

  @media (max-width: 1200px) {
    grid-template-columns: 1fr;
    grid-template-rows: 1fr auto;
  }
`;

const EditorColumn = styled.div`
  display: flex;
  flex-direction: column;
  min-height: 0;
`;

const PanelColumn = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.md};
  min-height: 0;
  overflow-y: auto;

  &::-webkit-scrollbar {
    width: 6px;
  }
  &::-webkit-scrollbar-track {
    background: transparent;
  }
  &::-webkit-scrollbar-thumb {
    background: ${p => p.theme.colors.primary}40;
    border-radius: 3px;
    &:hover {
      background: ${p => p.theme.colors.primary}60;
    }
  }
`;

export default function StrategyEditPage() {
  const params = useParams();
  const router = useRouter();
  const strategyId = params?.id as string;
  const editorRef = useRef<editor.IStandaloneCodeEditor | null>(null);

  const { data: strategy, isLoading, error } = useStrategy(strategyId);
  const { data: schema, isLoading: schemaLoading } = useStrategySchema();
  const { data: allSimulations } = useSimulations();
  const updateMutation = useUpdateStrategy();

  const [editorValue, setEditorValue] = useState('');
  const [strategyName, setStrategyName] = useState('');
  const [hasChanges, setHasChanges] = useState(false);
  const [showDeleteModal, setShowDeleteModal] = useState(false);

  // Initialize editor value and name when strategy loads
  useEffect(() => {
    if (strategy) {
      setEditorValue(strategy.markdownContent);
      setStrategyName(strategy.name);
      setHasChanges(false);
    }
  }, [strategy]);

  // Validate markdown content against schema
  const diagnostics = useMemo<LintDiagnostic[]>(() => {
    if (!schema || !editorValue) return [];
    return validateStrategy(editorValue, schema);
  }, [editorValue, schema]);

  // Count errors for save button disabling
  const errorCount = useMemo(() =>
    diagnostics.filter(d => d.severity === 'error').length,
    [diagnostics]
  );

  const handleEditorChange = (value: string | undefined) => {
    setEditorValue(value || '');
    setHasChanges((value || '') !== strategy?.markdownContent || strategyName !== strategy?.name);
  };

  const handleNameChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const newName = e.target.value;
    setStrategyName(newName);
    setHasChanges(newName !== strategy?.name || editorValue !== strategy?.markdownContent);
  };

  const handleEditorReady = useCallback((editor: editor.IStandaloneCodeEditor) => {
    editorRef.current = editor;
  }, []);

  const handleDiagnosticClick = useCallback((diagnostic: LintDiagnostic) => {
    if (editorRef.current) {
      editorRef.current.revealLineInCenter(diagnostic.line);
      editorRef.current.setPosition({ lineNumber: diagnostic.line, column: diagnostic.column });
      editorRef.current.focus();
    }
  }, []);

  const handleSave = async () => {
    if (!strategy || !hasChanges) return;

    // Warn if there are validation errors
    if (errorCount > 0) {
      const confirmed = window.confirm(
        `The strategy has ${errorCount} validation error${errorCount !== 1 ? 's' : ''}. ` +
        `Are you sure you want to save anyway?`
      );
      if (!confirmed) return;
    }

    try {
      await updateMutation.mutateAsync({
        id: strategyId,
        data: {
          name: strategyName,
          markdownContent: editorValue,
        },
      });
      setHasChanges(false);
    } catch (error) {
      // Error toast is handled by the mutation hook
      console.error('Failed to save strategy:', error);
    }
  };

  const handleCancel = () => {
    if (hasChanges) {
      const confirmed = window.confirm(
        'You have unsaved changes. Are you sure you want to leave?'
      );
      if (!confirmed) return;
    }
    router.push(`/strategies/${strategyId}`);
  };

  const handleReset = () => {
    if (!strategy) return;
    const confirmed = window.confirm(
      'Are you sure you want to discard all changes?'
    );
    if (confirmed) {
      setEditorValue(strategy.markdownContent);
      setStrategyName(strategy.name);
      setHasChanges(false);
    }
  };

  const strategySims = allSimulations?.filter(s => s.strategyId === strategyId) || [];
  const hasLiveSims = strategySims.some(s => s.status === 'LIVE' || s.status === 'SIMULATING');

  const handleDeleteModalClose = () => {
    setShowDeleteModal(false);
  };

  const handleDeleted = () => {
    router.push('/strategies');
  };

  if (isLoading) {
    return (
      <DashboardLayout>
        <EditorContainer>
          <TerminalPane title="Loading Strategy...">
            <LoadingContainer>
              <TerminalSkeleton width="60%" height="32px" />
              <TerminalSkeleton width="40%" height="20px" />
              <div style={{ marginTop: '16px' }}>
                <TerminalSkeleton width="100%" height="400px" />
              </div>
            </LoadingContainer>
          </TerminalPane>
        </EditorContainer>
      </DashboardLayout>
    );
  }

  if (error || !strategy) {
    return (
      <DashboardLayout>
        <EditorContainer>
          <TerminalPane title="Error">
            <ErrorContainer>
              <TerminalText variant="error">
                {error ? 'Failed to load strategy' : 'Strategy not found'}
              </TerminalText>
              <TerminalText size="sm" variant="muted" style={{ marginTop: '8px' }}>
                {error?.message || 'The strategy you are looking for does not exist.'}
              </TerminalText>
              <TerminalButton
                onClick={handleCancel}
                style={{ marginTop: '16px' }}
              >
                Back to Dashboard
              </TerminalButton>
            </ErrorContainer>
          </TerminalPane>
        </EditorContainer>
      </DashboardLayout>
    );
  }

  return (
    <DashboardLayout>
      <EditorContainer>
        {/* Header */}
        <EditorHeader>
          <HeaderInfo>
            <StrategyNameInput
              value={strategyName}
              onChange={handleNameChange}
              placeholder="Strategy name"
              maxLength={255}
            />
            <StrategyMeta>
              <TerminalText size="sm" variant="muted">
                Version {strategy.version}
              </TerminalText>
              <TerminalText size="sm" variant="muted">
                •
              </TerminalText>
              <TerminalText size="sm" variant="muted">
                Last updated: {new Date(strategy.createdAt).toLocaleString()}
              </TerminalText>
              {hasChanges && (
                <>
                  <TerminalText size="sm" variant="muted">
                    •
                  </TerminalText>
                  <TerminalText size="sm" variant="warning">
                    Unsaved changes
                  </TerminalText>
                </>
              )}
              {errorCount > 0 && (
                <>
                  <TerminalText size="sm" variant="muted">
                    •
                  </TerminalText>
                  <TerminalText size="sm" variant="error">
                    {errorCount} error{errorCount !== 1 ? 's' : ''}
                  </TerminalText>
                </>
              )}
            </StrategyMeta>
          </HeaderInfo>
          <HeaderActions>
            <TerminalButton
              variant="danger"
              onClick={() => setShowDeleteModal(true)}
              disabled={updateMutation.isPending}
            >
              Delete Strategy
            </TerminalButton>
            <TerminalButton
              variant="ghost"
              onClick={handleReset}
              disabled={!hasChanges || updateMutation.isPending}
            >
              Reset
            </TerminalButton>
            <TerminalButton
              variant="secondary"
              onClick={handleCancel}
              disabled={updateMutation.isPending}
            >
              Cancel
            </TerminalButton>
            <TerminalButton
              variant="primary"
              onClick={handleSave}
              disabled={!hasChanges || updateMutation.isPending}
            >
              {updateMutation.isPending ? 'Saving...' : 'Save Strategy'}
            </TerminalButton>
          </HeaderActions>
        </EditorHeader>

        {/* Monaco Editor with Linting Panel */}
        <EditorWithPanel>
          <EditorColumn>
            <EditorPane title="Strategy Markdown Editor">
              <MarkdownEditor
                value={editorValue}
                onChange={handleEditorChange}
                height="max(400px, calc(100vh - 280px))"
                diagnostics={diagnostics}
                onEditorReady={handleEditorReady}
              />
              <HelpText size="sm" variant="muted">
                <strong>Tip:</strong> Strategies are written in Markdown with YAML frontmatter.
                Include sections like &quot;Consensus Method&quot;, &quot;Evaluation Interval&quot;, and &quot;Rules&quot; to
                define your trading strategy.
              </HelpText>
            </EditorPane>
          </EditorColumn>
          <PanelColumn>
            <LintingPanel
              diagnostics={diagnostics}
              isLoading={schemaLoading}
              onDiagnosticClick={handleDiagnosticClick}
            />
            <CheatsheetPanel />
          </PanelColumn>
        </EditorWithPanel>
      </EditorContainer>

      {strategy && (
        <DeleteStrategyModal
          isOpen={showDeleteModal}
          onClose={handleDeleteModalClose}
          onDeleted={handleDeleted}
          strategyId={strategyId}
          strategyName={strategy.name}
          simulationCount={strategySims.length}
          hasLiveSims={hasLiveSims}
        />
      )}
    </DashboardLayout>
  );
}
