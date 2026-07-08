// ==========================================================================
// NEXUS PLAYER — TypeScript Types
// ==========================================================================

export interface Track {
  id: string;
  title: string;
  artist: string;
  album?: string;
  filename: string;
  /** Full URI — can be local file:// or remote http:// */
  uri: string;
  durationMs?: number;
  isVideo: boolean;
  isLocal: boolean;
}

export type PlayMode = 'normal' | 'repeat_one' | 'shuffle';

export interface LyricLine {
  timeMs: number;
  text: string;
}

export interface PlayerContextValue {
  // ── State ──────────────────────────────────────────────────────────────
  vpsTracks: Track[];
  localTracks: Track[];
  currentTrack: Track | null;
  currentSource: 'vps' | 'local';
  isPlaying: boolean;
  isLoading: boolean;
  positionMs: number;
  durationMs: number;
  lyrics: LyricLine[];
  activeLyricIndex: number;
  playMode: PlayMode;

  // ── Actions ─────────────────────────────────────────────────────────────
  playTrack: (track: Track, source: 'vps' | 'local', playlist: Track[]) => Promise<void>;
  togglePlayPause: () => Promise<void>;
  nextTrack: () => void;
  prevTrack: () => void;
  seekTo: (ms: number) => Promise<void>;
  skipForward: () => Promise<void>;
  skipBackward: () => Promise<void>;
  cyclePlayMode: () => void;
  importLocalFile: () => Promise<void>;
  setVpsTracks: React.Dispatch<React.SetStateAction<Track[]>>;
}

// Needed for import in context file
import React from 'react';
