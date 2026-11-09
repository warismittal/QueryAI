import { useEffect, useRef } from 'react';
import Editor, { loader, type OnMount } from '@monaco-editor/react';
import * as monaco from 'monaco-editor';
import editorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker';
import { useTheme } from '@/lib/theme';

// Bundle Monaco locally (no CDN) so the editor works offline and under a strict CSP.
self.MonacoEnvironment = { getWorker: () => new editorWorker() };
loader.config({ monaco });

let themesDefined = false;
type Tone = { bg: string; line: string; gutter: string; select: string; keyword: string; string: string; number: string; comment: string; operator: string; fn: string; ink: string };
const TONES: Record<string, Tone> = {
  midnight: { bg: '#0d0d10', line: '#141418', gutter: '#3a3842', select: '#7a1c2d66', keyword: 'e0798a', string: 'd6c2a1', number: '9fb4cf', comment: '5f5b66', operator: 'a29da8', fn: 'e6c58f', ink: 'e9e4dd' },
  crimson: { bg: '#100709', line: '#180a0d', gutter: '#4a2a30', select: '#8f1f3366', keyword: 'ff7a8f', string: 'e3c7a8', number: 'a9bdd6', comment: '6b4f55', operator: 'b59aa0', fn: 'f0c890', ink: 'f6e9e6' },
  graphite: { bg: '#121214', line: '#19191c', gutter: '#3d3d44', select: '#5a5a6466', keyword: 'd7d7de', string: 'b8b2a6', number: 'a8b4c4', comment: '606068', operator: '9a9aa3', fn: 'e0405a', ink: 'ececf0' },
  marine: { bg: '#061219', line: '#0a1a23', gutter: '#1f4a56', select: '#0e6f7866', keyword: '5fd6d1', string: 'd8c7a2', number: 'f0a58e', comment: '4f7077', operator: '8fb0b5', fn: 'e6c58f', ink: 'e2f0f0' },
  azure: { bg: '#080d1d', line: '#0d1429', gutter: '#2a3866', select: '#2741a866', keyword: '8fb0ff', string: 'd9c6a5', number: 'f0a8b8', comment: '56638a', operator: '9aa6c8', fn: 'e6c58f', ink: 'e9eefb' },
  light: { bg: '#fdfbf7', line: '#f4f0e9', gutter: '#c9c1b4', select: '#e8c9cc', keyword: '8c1c2c', string: '7a5a2e', number: '2f5e9e', comment: 'a39a90', operator: '6e6772', fn: '9c4a12', ink: '17141a' },
};

function defineThemes(m: typeof monaco) {
  if (themesDefined) return;
  themesDefined = true;
  for (const [name, t] of Object.entries(TONES)) {
    m.editor.defineTheme(`queryai-${name}`, {
      base: name === 'light' ? 'vs' : 'vs-dark',
      inherit: true,
      rules: [
        { token: '', foreground: t.ink },
        { token: 'keyword', foreground: t.keyword },
        { token: 'string', foreground: t.string },
        { token: 'number', foreground: t.number },
        { token: 'comment', foreground: t.comment, fontStyle: 'italic' },
        { token: 'operator', foreground: t.operator },
        { token: 'predefined', foreground: t.fn },
        { token: 'identifier', foreground: t.ink },
      ],
      colors: {
        'editor.background': t.bg,
        'editor.foreground': `#${t.ink}`,
        'editor.lineHighlightBackground': t.line,
        'editor.lineHighlightBorder': '#00000000',
        'editorLineNumber.foreground': t.gutter,
        'editorLineNumber.activeForeground': `#${t.keyword}`,
        'editorGutter.background': t.bg,
        'editor.selectionBackground': t.select,
        'editorCursor.foreground': `#${t.keyword}`,
        'editorIndentGuide.background1': t.line,
        'editorWidget.background': t.line,
        'editorSuggestWidget.background': t.bg,
        'editorSuggestWidget.border': t.gutter,
        'editorSuggestWidget.selectedBackground': t.line,
        'scrollbarSlider.background': `${t.gutter}55`,
      },
    });
  }
}

export interface SqlEditorProps {
  value: string;
  onChange: (value: string) => void;
  onRun?: () => void;
  readOnly?: boolean;
  height?: string | number;
  completions?: { tables: string[]; columns: string[] };
}

export function SqlEditor({ value, onChange, onRun, readOnly, height = '100%', completions }: SqlEditorProps) {
  const { theme } = useTheme();
  const runRef = useRef(onRun);
  runRef.current = onRun;
  const completionRef = useRef(completions);
  completionRef.current = completions;
  const disposable = useRef<monaco.IDisposable | null>(null);

  useEffect(() => () => disposable.current?.dispose(), []);

  const onMount: OnMount = (editor, m) => {
    editor.addCommand(m.KeyMod.CtrlCmd | m.KeyCode.Enter, () => runRef.current?.());
    disposable.current?.dispose();
    disposable.current = m.languages.registerCompletionItemProvider('sql', {
      provideCompletionItems: (model, position) => {
        const word = model.getWordUntilPosition(position);
        const range = { startLineNumber: position.lineNumber, endLineNumber: position.lineNumber, startColumn: word.startColumn, endColumn: word.endColumn };
        const c = completionRef.current;
        if (!c) return { suggestions: [] };
        return {
          suggestions: [
            ...c.tables.map((t) => ({ label: t, kind: m.languages.CompletionItemKind.Struct, insertText: t, range, detail: 'table' })),
            ...[...new Set(c.columns)].map((col) => ({ label: col, kind: m.languages.CompletionItemKind.Field, insertText: col, range, detail: 'column' })),
          ],
        };
      },
    });
  };

  return (
    <Editor
      height={height}
      language="sql"
      value={value}
      onChange={(v) => onChange(v ?? '')}
      onMount={onMount}
      beforeMount={defineThemes}
      theme={`queryai-${theme}`}
      options={{
        readOnly,
        minimap: { enabled: false },
        fontSize: 13.5,
        lineHeight: 22,
        fontFamily: 'JetBrains Mono, ui-monospace, monospace',
        fontLigatures: true,
        cursorBlinking: 'smooth',
        cursorSmoothCaretAnimation: 'on',
        smoothScrolling: true,
        lineNumbersMinChars: 3,
        scrollBeyondLastLine: false,
        wordWrap: 'on',
        automaticLayout: true,
        padding: { top: 16, bottom: 16 },
        renderLineHighlight: 'line',
        tabSize: 2,
        ariaLabel: 'SQL editor',
      }}
    />
  );
}
