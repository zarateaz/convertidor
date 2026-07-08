import React, { useState } from 'react';
import {
  ActivityIndicator,
  Platform,
  SafeAreaView,
  ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { Video, ResizeMode } from 'expo-av';
import { usePlayer } from '../context/PlayerContext';
import PlasmaOrb from '../components/PlasmaOrb';
import CavaVisualizer from '../components/CavaVisualizer';
import KaraokeView from '../components/KaraokeView';
import SeekBar from '../components/SeekBar';
import { Track } from '../types';

// ==========================================================================
// NEXUS PLAYER — Main Player Screen
// ==========================================================================

const VPS_BASE = 'http://168.194.102.34:3006';

type Tab = 'vps' | 'local';

const PLAY_MODE_ICONS: Record<string, string> = {
  normal:     '🔁',
  repeat_one: '🔂',
  shuffle:    '🔀',
};
const PLAY_MODE_LABELS: Record<string, string> = {
  normal:     'NORMAL',
  repeat_one: 'REPETIR',
  shuffle:    'ALEATORIO',
};

export default function PlayerScreen() {
  const player = usePlayer();
  const [activeTab, setActiveTab] = useState<Tab>('vps');

  const isAudio = player.currentTrack && !player.currentTrack.isVideo;
  const isVideo = player.currentTrack?.isVideo;
  const hasTrack = !!player.currentTrack;

  const browseList: Track[] = activeTab === 'vps' ? player.vpsTracks : player.localTracks;

  const handleTrackPress = (track: Track) => {
    const source: 'vps' | 'local' = activeTab;
    player.playTrack(track, source, browseList);
  };

  // ── Grid background lines ─────────────────────────────────────────────────
  const GridOverlay = () => (
    <View style={StyleSheet.absoluteFillObject} pointerEvents="none">
      {Array.from({ length: 14 }).map((_, i) => (
        <View key={`h${i}`} style={[styles.gridLineH, { top: `${(i + 1) * 7}%` }]} />
      ))}
      {Array.from({ length: 10 }).map((_, i) => (
        <View key={`v${i}`} style={[styles.gridLineV, { left: `${(i + 1) * 9}%` }]} />
      ))}
    </View>
  );

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="light-content" backgroundColor="#090a0f" />
      <GridOverlay />

      {/* ═══ HEADER ════════════════════════════════════════════════════════ */}
      <View style={styles.header}>
        <Text style={styles.logoAccent}>&lt;</Text>
        <Text style={styles.logoText}>NEXUS PLAYER</Text>
        <Text style={styles.logoAccent}>/&gt;</Text>
      </View>

      {/* ═══ TABS ══════════════════════════════════════════════════════════ */}
      <View style={styles.tabRow}>
        {(['vps', 'local'] as Tab[]).map((tab) => (
          <TouchableOpacity
            key={tab}
            style={[styles.tab, activeTab === tab && styles.tabActive]}
            onPress={() => setActiveTab(tab)}
          >
            <Text style={[styles.tabText, activeTab === tab && styles.tabTextActive]}>
              {tab === 'vps' ? '☁  NUBE VPS' : '📂  DISCO LOCAL'}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      {/* ═══ CENTRAL CONTENT AREA ══════════════════════════════════════════ */}
      <View style={styles.center}>
        {player.isLoading ? (
          /* Loading State */
          <View style={styles.loadingBox}>
            <ActivityIndicator size="large" color="#00f0ff" />
            <Text style={styles.loadingText}>SINCRONIZANDO CON NEXUS...</Text>
          </View>

        ) : isVideo ? (
          /* Video Player */
          <View style={styles.videoWrapper}>
            <Video
              style={styles.video}
              source={{ uri: player.currentTrack!.uri }}
              resizeMode={ResizeMode.CONTAIN}
              shouldPlay={player.isPlaying}
              onPlaybackStatusUpdate={() => {}}
            />
          </View>

        ) : isAudio && hasTrack ? (
          /* Plasma Orb + Karaoke */
          <View style={styles.audioView}>
            <PlasmaOrb isPlaying={player.isPlaying} size={120} />
            <View style={styles.karaokeContainer}>
              <KaraokeView
                lyrics={player.lyrics}
                activeLyricIndex={player.activeLyricIndex}
              />
            </View>
          </View>

        ) : (
          /* Track browser (no track selected yet) */
          <View style={styles.browser}>
            {activeTab === 'local' && (
              <TouchableOpacity style={styles.importBtn} onPress={player.importLocalFile}>
                <Text style={styles.importBtnText}>➕  EXPLORAR MULTIMEDIA (.mp3 / .mp4)</Text>
              </TouchableOpacity>
            )}

            <ScrollView showsVerticalScrollIndicator={false}>
              {browseList.length === 0 ? (
                <View style={styles.emptyBox}>
                  <Text style={styles.emptyText}>
                    {activeTab === 'local'
                      ? 'Importa archivos de tu dispositivo arriba.'
                      : 'No hay pistas en el VPS aún.'}
                  </Text>
                </View>
              ) : (
                browseList.map((track, idx) => {
                  const isActive =
                    player.currentTrack?.id === track.id;
                  return (
                    <TouchableOpacity
                      key={track.id}
                      style={[styles.trackItem, isActive && styles.trackItemActive]}
                      onPress={() => handleTrackPress(track)}
                    >
                      <View style={[styles.numBadge, isActive && styles.numBadgeActive]}>
                        <Text style={styles.numText}>
                          {isActive && player.isPlaying ? '♫' : track.isVideo ? '📹' : String(idx + 1)}
                        </Text>
                      </View>
                      <View style={styles.trackMeta}>
                        <Text style={[styles.trackTitle, isActive && styles.trackTitleActive]} numberOfLines={1}>
                          {track.title}
                        </Text>
                        <Text style={styles.trackArtist}>{track.artist}</Text>
                      </View>
                      <Text style={[styles.playArrow, isActive && styles.playArrowActive]}>▶</Text>
                    </TouchableOpacity>
                  );
                })
              )}
            </ScrollView>
          </View>
        )}
      </View>

      {/* ═══ NOW PLAYING INFO ══════════════════════════════════════════════ */}
      <View style={styles.nowPlaying}>
        <Text style={styles.nowTitle} numberOfLines={1} ellipsizeMode="tail">
          {player.currentTrack ? player.currentTrack.title : 'Ninguna pista activa'}
        </Text>
        <View style={styles.nowSubRow}>
          <Text style={styles.nowArtist}>
            {player.currentTrack ? player.currentTrack.artist : 'Selecciona para iniciar'}
          </Text>
          {/* Play mode chip */}
          <TouchableOpacity style={styles.modeChip} onPress={player.cyclePlayMode}>
            <Text style={styles.modeIcon}>{PLAY_MODE_ICONS[player.playMode]}</Text>
            <Text style={styles.modeLabel}>{PLAY_MODE_LABELS[player.playMode]}</Text>
          </TouchableOpacity>
        </View>
      </View>

      {/* ═══ SEEK BAR ══════════════════════════════════════════════════════ */}
      <SeekBar
        positionMs={player.positionMs}
        durationMs={player.durationMs}
        onSeek={player.seekTo}
      />

      {/* ═══ TRANSPORT CONTROLS ════════════════════════════════════════════ */}
      <View style={styles.controls}>
        {/* -10s */}
        <TouchableOpacity style={styles.skipBtn} onPress={player.skipBackward}>
          <Text style={styles.skipLabel}>-10s</Text>
        </TouchableOpacity>

        {/* Prev */}
        <TouchableOpacity style={styles.controlBtn} onPress={player.prevTrack}>
          <Text style={styles.controlIcon}>◀◀</Text>
        </TouchableOpacity>

        {/* Play / Pause */}
        <TouchableOpacity style={styles.playBtn} onPress={player.togglePlayPause}>
          <Text style={styles.playIcon}>{player.isPlaying ? '❚❚' : '▶'}</Text>
        </TouchableOpacity>

        {/* Next */}
        <TouchableOpacity style={styles.controlBtn} onPress={player.nextTrack}>
          <Text style={styles.controlIcon}>▶▶</Text>
        </TouchableOpacity>

        {/* +10s */}
        <TouchableOpacity style={styles.skipBtn} onPress={player.skipForward}>
          <Text style={styles.skipLabel}>+10s</Text>
        </TouchableOpacity>
      </View>

      {/* ═══ CAVA VISUALIZER ═══════════════════════════════════════════════ */}
      <CavaVisualizer isPlaying={player.isPlaying} barCount={24} />
    </SafeAreaView>
  );
}

// ==========================================================================
// STYLES
// ==========================================================================
const MONO = Platform.OS === 'ios' ? 'Courier' : 'monospace';

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: '#090a0f',
    paddingHorizontal: 14,
    justifyContent: 'space-between',
  },

  // Grid
  gridLineH: {
    position: 'absolute',
    left: 0, right: 0,
    height: 1,
    backgroundColor: 'rgba(0,240,255,0.025)',
  },
  gridLineV: {
    position: 'absolute',
    top: 0, bottom: 0,
    width: 1,
    backgroundColor: 'rgba(0,240,255,0.025)',
  },

  // Header
  header: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(0,240,255,0.07)',
  },
  logoAccent: {
    fontSize: 15,
    fontWeight: 'bold',
    color: '#00f0ff',
    fontFamily: MONO,
    textShadowColor: 'rgba(0,240,255,0.7)',
    textShadowOffset: { width: 0, height: 0 },
    textShadowRadius: 8,
  },
  logoText: {
    fontSize: 18,
    fontWeight: '900',
    letterSpacing: 4,
    color: '#ffffff',
    marginHorizontal: 10,
    fontFamily: MONO,
    textShadowColor: 'rgba(0,240,255,0.2)',
    textShadowOffset: { width: 0, height: 0 },
    textShadowRadius: 6,
  },

  // Tabs
  tabRow: {
    flexDirection: 'row',
    backgroundColor: 'rgba(10,11,20,0.8)',
    borderRadius: 10,
    padding: 3,
    marginVertical: 8,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.04)',
  },
  tab: {
    flex: 1,
    paddingVertical: 8,
    alignItems: 'center',
    borderRadius: 7,
  },
  tabActive: {
    backgroundColor: 'rgba(255,0,127,0.14)',
    borderWidth: 1,
    borderColor: 'rgba(255,0,127,0.35)',
  },
  tabText: {
    fontSize: 11,
    fontWeight: '800',
    color: '#555568',
    fontFamily: MONO,
  },
  tabTextActive: {
    color: '#ff007f',
    textShadowColor: 'rgba(255,0,127,0.4)',
    textShadowOffset: { width: 0, height: 0 },
    textShadowRadius: 5,
  },

  // Center
  center: {
    flex: 1,
    justifyContent: 'center',
    overflow: 'hidden',
  },
  loadingBox: {
    alignItems: 'center',
    justifyContent: 'center',
    flex: 1,
  },
  loadingText: {
    color: '#00f0ff',
    fontFamily: MONO,
    letterSpacing: 2,
    fontSize: 11,
    marginTop: 14,
  },

  // Video
  videoWrapper: {
    width: '100%',
    aspectRatio: 16 / 9,
    backgroundColor: '#000',
    borderRadius: 12,
    overflow: 'hidden',
    borderWidth: 2,
    borderColor: '#ff007f',
    shadowColor: '#ff007f',
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.7,
    shadowRadius: 16,
    elevation: 10,
    alignSelf: 'center',
  },
  video: {
    width: '100%',
    height: '100%',
  },

  // Audio view
  audioView: {
    flex: 1,
    alignItems: 'center',
  },
  karaokeContainer: {
    flex: 1,
    width: '100%',
    marginTop: 8,
  },

  // Browser
  browser: { flex: 1 },
  importBtn: {
    backgroundColor: 'rgba(0,240,255,0.06)',
    borderWidth: 1,
    borderColor: 'rgba(0,240,255,0.28)',
    borderRadius: 10,
    paddingVertical: 12,
    alignItems: 'center',
    marginBottom: 10,
  },
  importBtnText: {
    color: '#00f0ff',
    fontSize: 11,
    fontWeight: 'bold',
    fontFamily: MONO,
  },
  emptyBox: { alignItems: 'center', paddingTop: 40 },
  emptyText: { color: '#44455a', fontSize: 13, fontStyle: 'italic', textAlign: 'center' },

  // Track items
  trackItem: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(12,14,28,0.6)',
    borderRadius: 10,
    padding: 10,
    marginBottom: 6,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.03)',
  },
  trackItemActive: {
    borderColor: 'rgba(0,240,255,0.28)',
    backgroundColor: 'rgba(0,240,255,0.04)',
  },
  numBadge: {
    width: 28, height: 28, borderRadius: 14,
    backgroundColor: 'rgba(0,240,255,0.06)',
    justifyContent: 'center', alignItems: 'center',
  },
  numBadgeActive: { backgroundColor: 'rgba(0,240,255,0.18)' },
  numText: { color: '#00f0ff', fontSize: 11, fontWeight: 'bold' },
  trackMeta: { flex: 1, marginLeft: 10 },
  trackTitle: { color: '#ccd', fontSize: 13, fontWeight: '700' },
  trackTitleActive: { color: '#00f0ff' },
  trackArtist: { color: '#667', fontSize: 10, marginTop: 1 },
  playArrow: { color: '#333', fontSize: 12 },
  playArrowActive: { color: '#ff007f' },

  // Now playing info
  nowPlaying: {
    alignItems: 'center',
    marginBottom: 4,
    paddingHorizontal: 8,
  },
  nowTitle: {
    fontSize: 15,
    fontWeight: 'bold',
    color: '#fff',
    textAlign: 'center',
    marginBottom: 5,
  },
  nowSubRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 12,
  },
  nowArtist: {
    fontSize: 12,
    color: '#ff007f',
    fontWeight: '600',
  },
  modeChip: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(255,0,127,0.08)',
    borderWidth: 1,
    borderColor: 'rgba(255,0,127,0.22)',
    borderRadius: 20,
    paddingHorizontal: 8,
    paddingVertical: 3,
    gap: 3,
  },
  modeIcon: { fontSize: 11 },
  modeLabel: {
    color: '#ff007f',
    fontSize: 9,
    fontWeight: 'bold',
    fontFamily: MONO,
    letterSpacing: 0.5,
  },

  // Controls
  controls: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 12,
    gap: 14,
  },
  skipBtn: {
    width: 40, height: 40, borderRadius: 20,
    backgroundColor: 'rgba(12,14,28,0.7)',
    borderWidth: 1, borderColor: 'rgba(255,0,127,0.2)',
    justifyContent: 'center', alignItems: 'center',
  },
  skipLabel: {
    color: '#ff007f',
    fontSize: 9,
    fontWeight: 'bold',
    fontFamily: MONO,
  },
  controlBtn: {
    width: 52, height: 52, borderRadius: 26,
    backgroundColor: 'rgba(12,14,28,0.85)',
    borderWidth: 1.5, borderColor: 'rgba(0,240,255,0.2)',
    justifyContent: 'center', alignItems: 'center',
    shadowColor: '#00f0ff',
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.2, shadowRadius: 6, elevation: 3,
  },
  controlIcon: { color: '#00f0ff', fontSize: 14, fontWeight: 'bold' },
  playBtn: {
    width: 68, height: 68, borderRadius: 34,
    backgroundColor: 'rgba(12,14,28,0.9)',
    borderWidth: 2, borderColor: '#ff007f',
    justifyContent: 'center', alignItems: 'center',
    shadowColor: '#ff007f',
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.6, shadowRadius: 16, elevation: 8,
  },
  playIcon: { color: '#ff007f', fontSize: 22, fontWeight: 'bold', marginLeft: 3 },
});
