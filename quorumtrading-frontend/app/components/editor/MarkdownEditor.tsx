'use client';

import { useRef, useEffect, useCallback } from 'react';
import Editor, { Monaco, OnMount } from '@monaco-editor/react';
import styled from 'styled-components';
import type { editor } from 'monaco-editor';
import type { LintDiagnostic } from '@/app/lib/types/schema';
import { useThemeMode } from '@/app/hooks/useThemeMode';

interface MarkdownEditorProps {
  value: string;
  onChange: (value: string | undefined) => void;
  height?: string;
  readOnly?: boolean;
  className?: string;
  /** Lint diagnostics to display as Monaco markers */
  diagnostics?: LintDiagnostic[];
  /** Callback when editor is ready */
  onEditorReady?: (editor: editor.IStandaloneCodeEditor) => void;
}

const EditorContainer = styled.div`
  position: relative;
  width: 100%;
  border: 2px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.md};
  overflow: hidden;
  background: ${p => p.theme.colors.background};

  box-shadow: 0 0 20px ${p => p.theme.colors.primary}22;

  &:focus-within {
    border-color: ${p => p.theme.colors.primary};
    box-shadow: 0 0 20px ${p => p.theme.colors.primary}44;
  }
`;

const EditorWrapper = styled.div`
  position: relative;
  z-index: 0;
`;

/**
 * Monaco Editor wrapped with terminal theme styling
 *
 * Features:
 * - Markdown syntax highlighting
 * - Terminal color scheme (80s green/cyan palette)
 * - Minimap and line numbers
 * - Auto-formatting and suggestions
 * - Terminal scanline overlay effect
 */
export function MarkdownEditor({
  value,
  onChange,
  height = '600px',
  readOnly = false,
  className,
  diagnostics = [],
  onEditorReady,
}: MarkdownEditorProps) {
  const editorRef = useRef<editor.IStandaloneCodeEditor | null>(null);
  const monacoRef = useRef<Monaco | null>(null);
  const { mode: themeMode } = useThemeMode();

  // Convert lint diagnostics to Monaco markers
  const updateMarkers = useCallback(() => {
    if (!editorRef.current || !monacoRef.current) return;

    const model = editorRef.current.getModel();
    if (!model) return;

    const markers: editor.IMarkerData[] = diagnostics.map((diagnostic) => ({
      severity: diagnostic.severity === 'error'
        ? monacoRef.current!.MarkerSeverity.Error
        : diagnostic.severity === 'warning'
        ? monacoRef.current!.MarkerSeverity.Warning
        : monacoRef.current!.MarkerSeverity.Info,
      message: diagnostic.message,
      startLineNumber: diagnostic.line,
      startColumn: diagnostic.column,
      endLineNumber: diagnostic.endLine || diagnostic.line,
      endColumn: diagnostic.endColumn || model.getLineMaxColumn(diagnostic.line),
      code: diagnostic.code,
    }));

    monacoRef.current.editor.setModelMarkers(model, 'strategy-linter', markers);
  }, [diagnostics]);

  // Update markers when diagnostics change
  useEffect(() => {
    updateMarkers();
  }, [updateMarkers]);

  // Switch Monaco theme when app theme mode changes
  useEffect(() => {
    if (monacoRef.current) {
      monacoRef.current.editor.setTheme(themeMode === 'light' ? 'terminal-light' : 'terminal-dark');
    }
  }, [themeMode]);

  const handleEditorDidMount: OnMount = (editor, monaco) => {
    editorRef.current = editor;
    monacoRef.current = monaco;

    monaco.editor.defineTheme('terminal-dark', {
      base: 'vs-dark',
      inherit: true,
      rules: [
        { token: '', foreground: '00ff41', background: '0a0e14' },
        { token: 'comment', foreground: '7a8f84', fontStyle: 'italic' },
        { token: 'keyword', foreground: '00d9ff' },
        { token: 'string', foreground: '06ffa5' },
        { token: 'number', foreground: 'ffb000' },
        { token: 'regexp', foreground: 'ff6b9d' },
        { token: 'type', foreground: '00d9ff' },
        { token: 'class', foreground: '00ff41' },
        { token: 'function', foreground: '00d9ff' },
        { token: 'variable', foreground: '00ff41' },
        { token: 'constant', foreground: 'ffb000' },
        { token: 'tag', foreground: '00d9ff' },
        { token: 'attribute', foreground: '06ffa5' },
        { token: 'delimiter', foreground: '00ff41' },
        { token: 'emphasis', foreground: '00d9ff', fontStyle: 'italic' },
        { token: 'strong', foreground: '00ff41', fontStyle: 'bold' },
      ],
      colors: {
        'editor.background': '#0a0e14',
        'editor.foreground': '#00ff41',
        'editor.lineHighlightBackground': '#14191f',
        'editor.selectionBackground': '#00ff4133',
        'editor.inactiveSelectionBackground': '#00ff4122',
        'editor.cursor': '#00ff41',
        'editorLineNumber.foreground': '#4a5568',
        'editorLineNumber.activeForeground': '#00d9ff',
        'editorIndentGuide.background': '#1a2332',
        'editorIndentGuide.activeBackground': '#00ff4144',
        'editorWhitespace.foreground': '#1a2332',
        'editorCursor.foreground': '#00ff41',
        'editor.findMatchBackground': '#ffb00044',
        'editor.findMatchHighlightBackground': '#ffb00022',
        'editorBracketMatch.background': '#00ff4133',
        'editorBracketMatch.border': '#00ff41',
        'scrollbar.shadow': '#00000088',
        'scrollbarSlider.background': '#1a233244',
        'scrollbarSlider.hoverBackground': '#1a233266',
        'scrollbarSlider.activeBackground': '#00ff4144',
        'minimap.background': '#0f1419',
        'minimapSlider.background': '#1a233244',
        'minimapSlider.hoverBackground': '#1a233266',
        'minimapSlider.activeBackground': '#00ff4144',
      },
    });

    monaco.editor.defineTheme('terminal-light', {
      base: 'vs',
      inherit: true,
      rules: [
        { token: '', foreground: '1a2e1f', background: 'ffffff' },
        { token: 'comment', foreground: '6b7c72', fontStyle: 'italic' },
        { token: 'keyword', foreground: '0077aa' },
        { token: 'string', foreground: '058a5a' },
        { token: 'number', foreground: 'b37800' },
        { token: 'regexp', foreground: 'd4467a' },
        { token: 'type', foreground: '0077aa' },
        { token: 'class', foreground: '0a6e2a' },
        { token: 'function', foreground: '0077aa' },
        { token: 'variable', foreground: '0a6e2a' },
        { token: 'constant', foreground: 'b37800' },
        { token: 'tag', foreground: '0077aa' },
        { token: 'attribute', foreground: '058a5a' },
        { token: 'delimiter', foreground: '0a6e2a' },
        { token: 'emphasis', foreground: '0077aa', fontStyle: 'italic' },
        { token: 'strong', foreground: '0a6e2a', fontStyle: 'bold' },
      ],
      colors: {
        'editor.background': '#ffffff',
        'editor.foreground': '#1a2e1f',
        'editor.lineHighlightBackground': '#f5f5f0',
        'editor.selectionBackground': '#0a6e2a22',
        'editor.inactiveSelectionBackground': '#0a6e2a11',
        'editor.cursor': '#0a6e2a',
        'editorLineNumber.foreground': '#999999',
        'editorLineNumber.activeForeground': '#0077aa',
        'editorIndentGuide.background': '#e0e0dc',
        'editorIndentGuide.activeBackground': '#0a6e2a33',
        'editorWhitespace.foreground': '#e0e0dc',
        'editorCursor.foreground': '#0a6e2a',
        'editor.findMatchBackground': '#b3780044',
        'editor.findMatchHighlightBackground': '#b3780022',
        'editorBracketMatch.background': '#0a6e2a22',
        'editorBracketMatch.border': '#0a6e2a',
        'scrollbar.shadow': '#00000011',
        'scrollbarSlider.background': '#d4d4d044',
        'scrollbarSlider.hoverBackground': '#d4d4d066',
        'scrollbarSlider.activeBackground': '#0a6e2a33',
        'minimap.background': '#f9f9f6',
        'minimapSlider.background': '#d4d4d044',
        'minimapSlider.hoverBackground': '#d4d4d066',
        'minimapSlider.activeBackground': '#0a6e2a33',
      },
    });

    monaco.editor.setTheme(themeMode === 'light' ? 'terminal-light' : 'terminal-dark');

    updateMarkers();
    onEditorReady?.(editor);
  };

  // Method to jump to a specific line (called from LintingPanel)
  const goToLine = useCallback((line: number) => {
    if (editorRef.current) {
      editorRef.current.revealLineInCenter(line);
      editorRef.current.setPosition({ lineNumber: line, column: 1 });
      editorRef.current.focus();
    }
  }, []);

  const handleEditorChange = (value: string | undefined) => {
    onChange(value);
  };

  return (
    <EditorContainer className={className}>
      <EditorWrapper>
        <Editor
          height={height}
          defaultLanguage="markdown"
          value={value}
          onChange={handleEditorChange}
          onMount={handleEditorDidMount}
          options={{
            readOnly,
            minimap: {
              enabled: true,
              scale: 1,
            },
            fontSize: 14,
            lineNumbers: 'on',
            roundedSelection: false,
            scrollBeyondLastLine: false,
            automaticLayout: true,
            wordWrap: 'on',
            wrappingIndent: 'indent',
            renderLineHighlight: 'all',
            cursorBlinking: 'smooth',
            cursorStyle: 'line',
            folding: true,
            foldingStrategy: 'indentation',
            showFoldingControls: 'always',
            renderWhitespace: 'selection',
            bracketPairColorization: {
              enabled: true,
            },
            guides: {
              indentation: true,
              bracketPairs: true,
            },
            suggest: {
              showKeywords: true,
              showSnippets: true,
            },
            quickSuggestions: {
              other: true,
              comments: false,
              strings: false,
            },
            padding: {
              top: 16,
              bottom: 16,
            },
            scrollbar: {
              useShadows: true,
              verticalScrollbarSize: 14,
              horizontalScrollbarSize: 14,
            },
          }}
        />
      </EditorWrapper>
    </EditorContainer>
  );
}
