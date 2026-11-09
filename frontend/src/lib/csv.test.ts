import { describe, expect, it } from 'vitest';
import { toCsv } from './csv';

describe('toCsv', () => {
  it('escapes quotes, commas and newlines', () => {
    expect(toCsv(['a', 'b'], [['x,y', 'say "hi"'], [null, 'line\nbreak']])).toBe('a,b\r\n"x,y","say ""hi"""\r\n,"line\nbreak"');
  });

  it('neutralises spreadsheet formula injection', () => {
    expect(toCsv(['v'], [['=HYPERLINK("x")'], ['-5'], [-5]])).toBe('v\r\n"\'=HYPERLINK(""x"")"\r\n\'-5\r\n-5');
  });
});
