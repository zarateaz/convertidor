import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from 'react';
import { Audio, AVPlaybackStatus } from 'expo-av';
import * as FileSystem from 'expo-file-system';
import * as DocumentPicker from 'expo-document-picker';
import { LyricLine, PlayerContextValue, PlayMode, Track } from '../types';
import { findActiveLyricIndex, parseLrc } from '../utils/lrcParser';

// ==========================================================================
// NEXUS PLAYER — Global Audio Context
// ==========================================================================

const VPS_BASE_URL = 'http://168.194.102.34:3006';

const PlayerContext = createContext<PlayerContextValue | null>(null);

export function usePlayer(): PlayerContextValue {
  const ctx = useContext(PlayerContext);
  if (!ctx) throw new Error('usePlayer must be used inside <PlayerProvider>');
  return ctx;
}

// ---------------------------------------------------------------------------

export function PlayerProvider({ children }: { children: React.ReactNode }) {
  // ── Track Lists ───────────────────────────────────────────────────────────
  const [vpsTracks, setVpsTracks] = useState<Track[]>([]);
  const [localTracks, setLocalTracks] = useState<Track[]>([]);

  // ── Playback State ────────────────────────────────────────────────────────
  const [currentTrack, setCurrentTrack] = useState<Track | null>(null);
  const [currentSource, setCurrentSource] = useState<'vps' | 'local'>('vps');
  const [queue, setQueue] = useState<Track[]>([]);
  const [queueIndex, setQueueIndex] = useState<number>(0);
  const [isPlaying, setIsPlaying] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [positionMs, setPositionMs] = useState(0);
  const [durationMs, setDurationMs] = useState(0);

  // ── Lyrics ────────────────────────────────────────────────────────────────
  const [lyrics, setLyrics] = useState<LyricLine[]>([]);
  const [activeLyricIndex, setActiveLyricIndex] = useState(-1);

  // ── Play Mode ─────────────────────────────────────────────────────────────
  const [playMode, setPlayMode] = useState<PlayMode>('normal');

  // ── Refs (for sync access inside callbacks) ───────────────────────────────
  const soundRef      = useRef<Audio.Sound | null>(null);
  const isReadyRef    = useRef(false);   // Sound.isLoaded before seeking
  const queueRef      = useRef<Track[]>([]);
  const queueIdxRef   = useRef(0);
  const playModeRef   = useRef<PlayMode>('normal');
  const sourceRef     = useRef<'vps' | 'local'>('vps');

  // Keep refs in sync with state
  useEffect(() => { queueRef.current = queue; }, [queue]);
  useEffect(() => { queueIdxRef.current = queueIndex; }, [queueIndex]);
  useEffect(() => { playModeRef.current = playMode; }, [playMode]);

  // ── Audio Mode (runs once on mount) ──────────────────────────────────────
  useEffect(() => {
    (async () => {
      try {
        await Audio.setAudioModeAsync({
          allowsRecordingIOS: false,
          playsInSilentModeIOS: true,
          staysActiveInBackground: true,   // ← background playback
          shouldDuckAndroid: false,
          playThroughEarpieceAndroid: false,
        });
      } catch (err) {
        console.warn('[NEXUS] setAudioMode failed:', err);
      }
    })();
  }, []);

  // ── Unload helper ─────────────────────────────────────────────────────────
  const unloadSound = async () => {
    isReadyRef.current = false;
    if (soundRef.current) {
      try { await soundRef.current.unloadAsync(); } catch {}
      soundRef.current = null;
    }
  };

  // ── Playback status callback ──────────────────────────────────────────────
  const onStatus = useCallback((status: AVPlaybackStatus) => {
    if (!status.isLoaded) return;

    if (!isReadyRef.current) isReadyRef.current = true;

    setPositionMs(status.positionMillis);
    setDurationMs(status.durationMillis ?? 0);
    setIsPlaying(status.isPlaying);

    // Active lyric lookup
    setActiveLyricIndex(
      findActiveLyricIndex(lyrics, status.positionMillis)
    );

    // Auto-advance on finish
    if (status.didJustFinish) {
      handleTrackFinish();
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [lyrics]);

  const handleTrackFinish = () => {
    const q   = queueRef.current;
    const idx = queueIdxRef.current;
    const mode = playModeRef.current;

    if (mode === 'repeat_one') {
      soundRef.current?.replayAsync().catch(() => {});
    } else if (mode === 'shuffle') {
      let next = Math.floor(Math.random() * q.length);
      if (q.length > 1) while (next === idx) next = Math.floor(Math.random() * q.length);
      _loadByIndex(next);
    } else {
      _loadByIndex(idx + 1 < q.length ? idx + 1 : 0);
    }
  };

  // ── Internal loader ───────────────────────────────────────────────────────
  const _loadByIndex = async (index: number) => {
    const q = queueRef.current;
    if (!q.length || index < 0 || index >= q.length) return;

    const track = q[index];
    setIsLoading(true);
    await unloadSound();

    setCurrentTrack(track);
    setQueueIndex(index);
    queueIdxRef.current = index;

    // Resolve URI (download VPS track to local cache if needed)
    let uri = track.uri;
    if (!track.isLocal) {
      const cacheUri = `${FileSystem.cacheDirectory}${track.filename}`;
      try {
        const info = await FileSystem.getInfoAsync(cacheUri);
        if (!info.exists) {
          const dl = FileSystem.createDownloadResumable(uri, cacheUri);
          await dl.downloadAsync();
        }
        uri = cacheUri;
      } catch (e) {
        console.warn('[NEXUS] Download failed, streaming direct:', e);
      }
    }

    // Fetch .lrc lyrics
    try {
      const lrcFilename = track.filename.replace(/\.(mp3|mp4|m4a|aac|ogg)$/i, '.lrc');
      const lrcCacheUri = `${FileSystem.cacheDirectory}${lrcFilename}`;
      const lrcInfo = await FileSystem.getInfoAsync(lrcCacheUri);

      if (!lrcInfo.exists) {
        if (!track.isLocal) {
          await FileSystem.downloadAsync(
            `${VPS_BASE_URL}/downloads/${encodeURIComponent(lrcFilename)}`,
            lrcCacheUri
          );
        }
      }
      const raw = await FileSystem.readAsStringAsync(lrcCacheUri);
      setLyrics(parseLrc(raw));
    } catch {
      setLyrics([
        { timeMs: 0, text: `♫ ${track.title}` },
        { timeMs: 3000, text: track.artist },
        { timeMs: 8000, text: '— ZARATE PLAYER —' },
      ]);
    }
    setActiveLyricIndex(-1);

    // Create and play sound (only for audio — video handled in component)
    if (!track.isVideo) {
      try {
        const { sound } = await Audio.Sound.createAsync(
          { uri },
          { shouldPlay: true, progressUpdateIntervalMillis: 200 },
          onStatus
        );
        soundRef.current = sound;
      } catch (e) {
        console.error('[NEXUS] Audio load failed:', e);
      }
    }

    setIsLoading(false);
  };

  // ── Public: playTrack ─────────────────────────────────────────────────────
  const playTrack = async (
    track: Track,
    source: 'vps' | 'local',
    playlist: Track[]
  ) => {
    sourceRef.current = source;
    setCurrentSource(source);
    setQueue(playlist);
    queueRef.current = playlist;

    const idx = playlist.findIndex((t) => t.id === track.id);
    await _loadByIndex(idx >= 0 ? idx : 0);
  };

  // ── Public: togglePlayPause ───────────────────────────────────────────────
  const togglePlayPause = async () => {
    if (!soundRef.current || !isReadyRef.current) return;
    try {
      if (isPlaying) await soundRef.current.pauseAsync();
      else           await soundRef.current.playAsync();
    } catch (e) {
      console.warn('[NEXUS] togglePlayPause error:', e);
    }
  };

  // ── Public: nextTrack / prevTrack ─────────────────────────────────────────
  const nextTrack = () => {
    const q   = queueRef.current;
    const idx = queueIdxRef.current;
    if (!q.length) return;

    if (playMode === 'shuffle') {
      let n = Math.floor(Math.random() * q.length);
      if (q.length > 1) while (n === idx) n = Math.floor(Math.random() * q.length);
      _loadByIndex(n);
    } else {
      _loadByIndex(idx + 1 < q.length ? idx + 1 : 0);
    }
  };

  const prevTrack = () => {
    const q   = queueRef.current;
    const idx = queueIdxRef.current;
    if (!q.length) return;

    if (positionMs > 3000) {
      // If past 3s into track, restart it
      seekTo(0);
    } else {
      _loadByIndex(idx - 1 >= 0 ? idx - 1 : q.length - 1);
    }
  };

  // ── Public: seekTo ────────────────────────────────────────────────────────
  const seekTo = async (ms: number) => {
    if (!soundRef.current || !isReadyRef.current) return;
    try {
      const clamped = Math.max(0, Math.min(durationMs, ms));
      await soundRef.current.setPositionAsync(clamped);
      setPositionMs(clamped);
    } catch (e) {
      console.warn('[NEXUS] seekTo error:', e);
    }
  };

  const skipForward  = () => seekTo(positionMs + 10_000);
  const skipBackward = () => seekTo(positionMs - 10_000);

  // ── Public: cyclePlayMode ─────────────────────────────────────────────────
  const cyclePlayMode = () => {
    setPlayMode((prev) => {
      const next: PlayMode =
        prev === 'normal'     ? 'repeat_one' :
        prev === 'repeat_one' ? 'shuffle'    : 'normal';
      playModeRef.current = next;
      return next;
    });
  };

  // ── Public: importLocalFile ───────────────────────────────────────────────
  const importLocalFile = async () => {
    try {
      const result = await DocumentPicker.getDocumentAsync({
        type: ['audio/mpeg', 'video/mp4', 'audio/mp4', 'audio/*', 'video/*'],
        copyToCacheDirectory: true,
        multiple: true,
      });
      if (result.canceled) return;

      const newTracks: Track[] = result.assets.map((asset) => {
        const isVideo = /\.(mp4|mkv|mov|avi)$/i.test(asset.name);
        return {
          id: `local-${Date.now()}-${Math.random()}`,
          title: asset.name.replace(/\.[^/.]+$/, ''),
          artist: isVideo ? 'Video Local' : 'Audio Local',
          filename: asset.name,
          uri: asset.uri,
          isVideo,
          isLocal: true,
        };
      });

      setLocalTracks((prev) => [...prev, ...newTracks]);
    } catch (e) {
      console.warn('[NEXUS] importLocalFile error:', e);
    }
  };

  // ── Cleanup on unmount ────────────────────────────────────────────────────
  useEffect(() => {
    return () => { unloadSound(); };
  }, []);

  const value: PlayerContextValue = {
    vpsTracks, localTracks, setVpsTracks,
    currentTrack, currentSource,
    isPlaying, isLoading,
    positionMs, durationMs,
    lyrics, activeLyricIndex,
    playMode,
    playTrack, togglePlayPause,
    nextTrack, prevTrack,
    seekTo, skipForward, skipBackward,
    cyclePlayMode, importLocalFile,
  };

  return (
    <PlayerContext.Provider value={value}>
      {children}
    </PlayerContext.Provider>
  );
}
