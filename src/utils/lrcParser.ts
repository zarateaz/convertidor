import { LyricLine } from '../types';

// ==========================================================================
// NEXUS PLAYER — .lrc File Parser
// Supports [mm:ss.xx] and [mm:ss.xxx] timestamp formats
// ==========================================================================

const TIME_REGEX = /\[(\d{1,2}):(\d{2})[.:](\d{2,3})\](.*)/;

export function parseLrc(content: string): LyricLine[] {
  if (!content || typeof content !== 'string') return [];

  const lines = content.split('\n');
  const result: LyricLine[] = [];

  for (const raw of lines) {
    const line = raw.trim();
    const match = TIME_REGEX.exec(line);
    if (!match) continue;

    const minutes   = parseInt(match[1], 10);
    const seconds   = parseInt(match[2], 10);
    const fracStr   = match[3];
    const text      = match[4].trim();

    if (!text) continue; // skip blank timestamp lines

    // Normalize fractions: 2 digits = centiseconds (×10), 3 = milliseconds (×1)
    const fracMs = fracStr.length === 2
      ? parseInt(fracStr, 10) * 10
      : parseInt(fracStr, 10);

    const timeMs = (minutes * 60 + seconds) * 1000 + fracMs;
    result.push({ timeMs, text });
  }

  return result.sort((a, b) => a.timeMs - b.timeMs);
}

/**
 * Returns the index of the active lyric line for the given position in milliseconds.
 * Returns -1 if no lyric has started yet.
 */
export function findActiveLyricIndex(
  lyrics: LyricLine[],
  positionMs: number
): number {
  if (!lyrics.length || positionMs < lyrics[0].timeMs) return -1;

  let lo = 0;
  let hi = lyrics.length - 1;
  let found = 0;

  // Binary search for best match
  while (lo <= hi) {
    const mid = Math.floor((lo + hi) / 2);
    if (lyrics[mid].timeMs <= positionMs) {
      found = mid;
      lo = mid + 1;
    } else {
      hi = mid - 1;
    }
  }
  return found;
}
