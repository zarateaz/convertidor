import React, { useState, useEffect, useRef, useCallback } from "react";
import {
  StyleSheet,
  Text,
  View,
  TouchableOpacity,
  FlatList,
  Animated,
  ActivityIndicator,
  SafeAreaView,
  StatusBar,
  ScrollView,
  Platform,
} from "react-native";
import { Audio, AVPlaybackStatus, Video, ResizeMode } from "expo-av";
import * as FileSystem from "expo-file-system";
import * as DocumentPicker from "expo-document-picker";

// ==========================================================================
// CONFIG
// ==========================================================================
const VPS_BASE_URL = "http://168.194.102.34:3006";

type PlayMode = "normal" | "repeat_one" | "shuffle";

interface Track {
  id: string;
  title: string;
  artist: string;
  filename: string;
  uri?: string;
  isVideo?: boolean;
}

interface LyricLine {
  time: number;
  text: string;
}

const parseLrc = (lrcContent: string): LyricLine[] => {
  const lines = lrcContent.split("\n");
  const timeRegex = /\[(\d{2}):(\d{2})[.:](\d{2,3})\](.*)/;
  const parsedLines: LyricLine[] = [];
  for (const line of lines) {
    const match = timeRegex.exec(line);
    if (match) {
      const minutes = parseInt(match[1], 10);
      const seconds = parseInt(match[2], 10);
      const fractionStr = match[3];
      const text = match[4].trim();
      const fractionMultiplier = fractionStr.length === 2 ? 10 : 1;
      const milliseconds = parseInt(fractionStr, 10) * fractionMultiplier;
      const totalTime = (minutes * 60 + seconds) * 1000 + milliseconds;
      parsedLines.push({ time: totalTime, text });
    }
  }
  return parsedLines.sort((a, b) => a.time - b.time);
};

const formatTime = (millis: number) => {
  const totalSeconds = Math.floor(millis / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds < 10 ? "0" : ""}${seconds}`;
};

// ==========================================================================
// PLASMA ORB — ESFERA HOLOGRÁFICA REACTIVA AL AUDIO
// ==========================================================================
function PlasmaOrb({ isPlaying }: { isPlaying: boolean }) {
  // Escala de la esfera principal (respira con la música)
  const orbScale = useRef(new Animated.Value(1)).current;
  // Opacidad del glow central
  const glowOpacity = useRef(new Animated.Value(0.5)).current;
  // Rotación del anillo orbital externo
  const rotateAnim = useRef(new Animated.Value(0)).current;
  // Tres anillos de onda expansiva que irradian hacia afuera
  const ring1Scale = useRef(new Animated.Value(1)).current;
  const ring1Opacity = useRef(new Animated.Value(0.7)).current;
  const ring2Scale = useRef(new Animated.Value(1)).current;
  const ring2Opacity = useRef(new Animated.Value(0.5)).current;
  const ring3Scale = useRef(new Animated.Value(1)).current;
  const ring3Opacity = useRef(new Animated.Value(0.3)).current;
  // Partículas de plasma parpadeando
  const particle1 = useRef(new Animated.Value(0.2)).current;
  const particle2 = useRef(new Animated.Value(0.6)).current;
  const particle3 = useRef(new Animated.Value(0.4)).current;

  useEffect(() => {
    if (!isPlaying) {
      // Estado de reposo: pulso suave y lento
      const restLoop = Animated.loop(
        Animated.sequence([
          Animated.parallel([
            Animated.timing(orbScale, { toValue: 1.05, duration: 1800, useNativeDriver: true }),
            Animated.timing(glowOpacity, { toValue: 0.35, duration: 1800, useNativeDriver: true }),
          ]),
          Animated.parallel([
            Animated.timing(orbScale, { toValue: 1.0, duration: 1800, useNativeDriver: true }),
            Animated.timing(glowOpacity, { toValue: 0.15, duration: 1800, useNativeDriver: true }),
          ]),
        ])
      );
      restLoop.start();
      return () => restLoop.stop();
    }

    // ── ANIMACIÓN DE REPRODUCCIÓN ──────────────────────────────────
    // Esfera principal: latido rápido agresivo
    const orbLoop = Animated.loop(
      Animated.sequence([
        Animated.timing(orbScale, { toValue: 1.22, duration: 180, useNativeDriver: true }),
        Animated.timing(orbScale, { toValue: 0.96, duration: 200, useNativeDriver: true }),
        Animated.timing(orbScale, { toValue: 1.14, duration: 160, useNativeDriver: true }),
        Animated.timing(orbScale, { toValue: 1.0, duration: 400, useNativeDriver: true }),
      ])
    );

    // Glow central parpadeante
    const glowLoop = Animated.loop(
      Animated.sequence([
        Animated.timing(glowOpacity, { toValue: 1.0, duration: 200, useNativeDriver: true }),
        Animated.timing(glowOpacity, { toValue: 0.4, duration: 350, useNativeDriver: true }),
        Animated.timing(glowOpacity, { toValue: 0.8, duration: 200, useNativeDriver: true }),
        Animated.timing(glowOpacity, { toValue: 0.3, duration: 450, useNativeDriver: true }),
      ])
    );

    // Onda 1: rápida, fuerte
    const wave1 = Animated.loop(
      Animated.parallel([
        Animated.sequence([
          Animated.timing(ring1Scale, { toValue: 2.2, duration: 700, useNativeDriver: true }),
          Animated.timing(ring1Scale, { toValue: 1.0, duration: 0, useNativeDriver: true }),
        ]),
        Animated.sequence([
          Animated.timing(ring1Opacity, { toValue: 0, duration: 700, useNativeDriver: true }),
          Animated.timing(ring1Opacity, { toValue: 0.7, duration: 0, useNativeDriver: true }),
        ]),
      ])
    );

    // Onda 2: desfasada 230ms
    const wave2 = Animated.loop(
      Animated.sequence([
        Animated.delay(230),
        Animated.parallel([
          Animated.sequence([
            Animated.timing(ring2Scale, { toValue: 2.5, duration: 750, useNativeDriver: true }),
            Animated.timing(ring2Scale, { toValue: 1.0, duration: 0, useNativeDriver: true }),
          ]),
          Animated.sequence([
            Animated.timing(ring2Opacity, { toValue: 0, duration: 750, useNativeDriver: true }),
            Animated.timing(ring2Opacity, { toValue: 0.5, duration: 0, useNativeDriver: true }),
          ]),
        ]),
      ])
    );

    // Onda 3: desfasada 480ms
    const wave3 = Animated.loop(
      Animated.sequence([
        Animated.delay(480),
        Animated.parallel([
          Animated.sequence([
            Animated.timing(ring3Scale, { toValue: 2.8, duration: 800, useNativeDriver: true }),
            Animated.timing(ring3Scale, { toValue: 1.0, duration: 0, useNativeDriver: true }),
          ]),
          Animated.sequence([
            Animated.timing(ring3Opacity, { toValue: 0, duration: 800, useNativeDriver: true }),
            Animated.timing(ring3Opacity, { toValue: 0.3, duration: 0, useNativeDriver: true }),
          ]),
        ]),
      ])
    );

    // Rotación del anillo orbital
    const rotLoop = Animated.loop(
      Animated.timing(rotateAnim, { toValue: 1, duration: 3500, useNativeDriver: true })
    );

    // Partículas de plasma
    const p1 = Animated.loop(Animated.sequence([
      Animated.timing(particle1, { toValue: 1, duration: 350, useNativeDriver: true }),
      Animated.timing(particle1, { toValue: 0.1, duration: 350, useNativeDriver: true }),
    ]));
    const p2 = Animated.loop(Animated.sequence([
      Animated.delay(120),
      Animated.timing(particle2, { toValue: 0.1, duration: 400, useNativeDriver: true }),
      Animated.timing(particle2, { toValue: 1, duration: 400, useNativeDriver: true }),
    ]));
    const p3 = Animated.loop(Animated.sequence([
      Animated.delay(240),
      Animated.timing(particle3, { toValue: 1, duration: 300, useNativeDriver: true }),
      Animated.timing(particle3, { toValue: 0.2, duration: 300, useNativeDriver: true }),
    ]));

    orbLoop.start(); glowLoop.start();
    wave1.start(); wave2.start(); wave3.start();
    rotLoop.start();
    p1.start(); p2.start(); p3.start();

    return () => {
      orbLoop.stop(); glowLoop.stop();
      wave1.stop(); wave2.stop(); wave3.stop();
      rotLoop.stop();
      p1.stop(); p2.stop(); p3.stop();
    };
  }, [isPlaying]);

  const spin = rotateAnim.interpolate({
    inputRange: [0, 1],
    outputRange: ["0deg", "360deg"],
  });

  const ORB = 130; // diámetro de la esfera en dp

  return (
    <View style={orbStyles.orbContainer}>
      {/* ── ONDAS EXPANSIVAS ─────────────────────────── */}
      {[{ s: ring1Scale, o: ring1Opacity, color: "rgba(0,180,255,0.25)" },
        { s: ring2Scale, o: ring2Opacity, color: "rgba(0,120,255,0.18)" },
        { s: ring3Scale, o: ring3Opacity, color: "rgba(0,60,220,0.12)" }
      ].map((ring, idx) => (
        <Animated.View
          key={idx}
          style={[
            orbStyles.ring,
            {
              width: ORB,
              height: ORB,
              borderRadius: ORB / 2,
              borderColor: ring.color,
              transform: [{ scale: ring.s }],
              opacity: ring.o,
            },
          ]}
        />
      ))}

      {/* ── ANILLO ORBITAL ROTANTE ───────────────────── */}
      <Animated.View
        style={[
          orbStyles.orbitRing,
          {
            width: ORB + 50,
            height: ORB + 50,
            borderRadius: (ORB + 50) / 2,
            transform: [{ rotate: spin }],
          },
        ]}
      />

      {/* ── ESFERA PRINCIPAL ─────────────────────────── */}
      <Animated.View
        style={[
          orbStyles.orbCore,
          {
            width: ORB,
            height: ORB,
            borderRadius: ORB / 2,
            transform: [{ scale: orbScale }],
          },
        ]}
      >
        {/* Glow interior */}
        <Animated.View style={[orbStyles.innerGlow, { opacity: glowOpacity }]} />
        {/* Reflejo de luz superior */}
        <View style={orbStyles.highlight} />
        {/* Partículas internas */}
        <Animated.View style={[orbStyles.particle, orbStyles.p1, { opacity: particle1 }]} />
        <Animated.View style={[orbStyles.particle, orbStyles.p2, { opacity: particle2 }]} />
        <Animated.View style={[orbStyles.particle, orbStyles.p3, { opacity: particle3 }]} />
      </Animated.View>
    </View>
  );
}

const orbStyles = StyleSheet.create({
  orbContainer: {
    alignItems: "center",
    justifyContent: "center",
    height: 220,
    position: "relative",
  },
  ring: {
    position: "absolute",
    borderWidth: 1.5,
  },
  orbitRing: {
    position: "absolute",
    borderWidth: 1,
    borderColor: "transparent",
    borderTopColor: "rgba(0,200,255,0.5)",
    borderRightColor: "rgba(0,100,255,0.3)",
  },
  orbCore: {
    backgroundColor: "#001a3d",
    borderWidth: 2,
    borderColor: "rgba(0,200,255,0.8)",
    justifyContent: "center",
    alignItems: "center",
    overflow: "hidden",
    shadowColor: "#00aaff",
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 1,
    shadowRadius: 30,
    elevation: 20,
  },
  innerGlow: {
    position: "absolute",
    width: "80%",
    height: "80%",
    borderRadius: 100,
    backgroundColor: "rgba(0,160,255,0.25)",
  },
  highlight: {
    position: "absolute",
    top: "12%",
    left: "18%",
    width: "38%",
    height: "22%",
    borderRadius: 30,
    backgroundColor: "rgba(160,230,255,0.22)",
    transform: [{ rotate: "-25deg" }],
  },
  particle: {
    position: "absolute",
    borderRadius: 4,
    backgroundColor: "#00ddff",
  },
  p1: { width: 5, height: 5, top: "25%", left: "30%" },
  p2: { width: 3, height: 3, top: "55%", right: "25%" },
  p3: { width: 4, height: 4, bottom: "20%", left: "50%" },
});

// ==========================================================================
// COMPONENTE PRINCIPAL
// ==========================================================================
export default function App() {
  const [activeTab, setActiveTab] = useState<"nube" | "local">("nube");
  const [vpsTracks, setVpsTracks] = useState<Track[]>([]);
  const [localTracks, setLocalTracks] = useState<Track[]>([]);

  // Estado de reproducción
  const [currentTrackIndex, setCurrentTrackIndex] = useState<number | null>(null);
  const [currentSource, setCurrentSource] = useState<"nube" | "local">("nube");
  const [isPlaying, setIsPlaying] = useState(false);
  const [playbackPosition, setPlaybackPosition] = useState(0);
  const [playbackDuration, setPlaybackDuration] = useState(0);
  const [isLoaded, setIsLoaded] = useState(false); // ← CLAVE: saber si audio está listo para seek

  // Modos de reproducción
  const [playMode, setPlayMode] = useState<PlayMode>("normal");

  // Letras sincro
  const [lyrics, setLyrics] = useState<LyricLine[]>([]);
  const [activeLyricIndex, setActiveLyricIndex] = useState(-1);

  // Descarga
  const [isDownloading, setIsDownloading] = useState(false);
  const [downloadProgress, setDownloadProgress] = useState("0%");

  // Barra de progreso
  const [progressBarWidth, setProgressBarWidth] = useState(200);

  // Refs
  const soundRef = useRef<Audio.Sound | null>(null);
  const videoRef = useRef<Video | null>(null);
  const lyricsListRef = useRef<FlatList<LyricLine> | null>(null);
  const isLoadedRef = useRef(false); // ← ref sincrónico para seek sin esperar re-render

  // Ecualizador - 14 barras
  const visualizerBars = useRef(
    Array.from({ length: 14 }, () => new Animated.Value(5))
  ).current;

  // -------------------------------------------------------------------------
  // Carga inicial de canciones VPS (mock, reemplaza por fetch real si tienes endpoint)
  // -------------------------------------------------------------------------
  useEffect(() => {
    setVpsTracks([
      { id: "v1", title: "Billy Idol - Eyes Without A Face", artist: "Billy Idol", filename: "Billy Idol - Eyes Without A Face.mp3" },
      { id: "v2", title: "Morir De Amor", artist: "Kudai", filename: "Morir De Amor.mp3" },
      { id: "v3", title: "Baby Come Back", artist: "Player", filename: "Baby Come Back.mp3" },
    ]);
  }, []);

  // -------------------------------------------------------------------------
  // Animación del ecualizador
  // -------------------------------------------------------------------------
  useEffect(() => {
    let animLoop: Animated.CompositeAnimation | null = null;
    if (isPlaying) {
      const animations = visualizerBars.map((bar) =>
        Animated.loop(
          Animated.sequence([
            Animated.timing(bar, { toValue: Math.random() * 70 + 5, duration: Math.random() * 180 + 80, useNativeDriver: false }),
            Animated.timing(bar, { toValue: 5, duration: Math.random() * 180 + 80, useNativeDriver: false }),
          ])
        )
      );
      animLoop = Animated.parallel(animations);
      animLoop.start();
    } else {
      visualizerBars.forEach((bar) =>
        Animated.timing(bar, { toValue: 5, duration: 300, useNativeDriver: false }).start()
      );
    }
    return () => { if (animLoop) animLoop.stop(); };
  }, [isPlaying]);

  // -------------------------------------------------------------------------
  // Callback de estado de reproducción (audio y video)
  // -------------------------------------------------------------------------
  const onPlaybackStatusUpdate = useCallback((status: AVPlaybackStatus) => {
    if (!status.isLoaded) return;

    // Marcar como listo para seek en cuanto se carga
    if (!isLoadedRef.current) {
      isLoadedRef.current = true;
      setIsLoaded(true);
    }

    setPlaybackPosition(status.positionMillis);
    setPlaybackDuration(status.durationMillis || 0);
    setIsPlaying(status.isPlaying);

    // Auto-scroll letras karaoke
    if (lyrics.length > 0) {
      let idx = -1;
      for (let i = 0; i < lyrics.length; i++) {
        if (status.positionMillis >= lyrics[i].time) idx = i;
        else break;
      }
      if (idx !== -1 && idx !== activeLyricIndex) {
        setActiveLyricIndex(idx);
        lyricsListRef.current?.scrollToIndex({ index: idx, animated: true, viewPosition: 0.5 });
      }
    }

    // Al terminar canción, actuar según modo de reproducción
    if (status.didJustFinish) {
      handleTrackEnd();
    }
  }, [lyrics, activeLyricIndex]);

  // -------------------------------------------------------------------------
  // Lógica al terminar canción según modo
  // -------------------------------------------------------------------------
  const handleTrackEnd = useCallback(() => {
    const src = currentSource;
    const playlist = src === "nube" ? vpsTracks : localTracks;
    const idx = currentTrackIndex;

    if (playMode === "repeat_one") {
      // Repetir la misma canción
      soundRef.current?.replayAsync();
    } else if (playMode === "shuffle") {
      // Aleatoria: índice random diferente al actual
      let nextIdx = Math.floor(Math.random() * playlist.length);
      if (playlist.length > 1) {
        while (nextIdx === idx) nextIdx = Math.floor(Math.random() * playlist.length);
      }
      loadTrack(nextIdx, src);
    } else {
      // Normal: siguiente o volver al inicio
      if (idx !== null && idx + 1 < playlist.length) loadTrack(idx + 1, src);
      else loadTrack(0, src);
    }
  }, [currentSource, currentTrackIndex, playMode, vpsTracks, localTracks]);

  // -------------------------------------------------------------------------
  // Cargar y reproducir una pista (audio o video)
  // -------------------------------------------------------------------------
  const loadTrack = async (index: number, source: "nube" | "local" = activeTab) => {
    const playlist = source === "nube" ? vpsTracks : localTracks;
    if (index < 0 || index >= playlist.length) return;

    setIsDownloading(true);
    setDownloadProgress("0%");
    setIsLoaded(false);
    isLoadedRef.current = false;

    // Descargar o detener medios previos
    if (soundRef.current) {
      await soundRef.current.unloadAsync();
      soundRef.current = null;
    }

    const track = playlist[index];
    let uriToPlay = track.uri || "";

    if (source === "nube") {
      const localAudioUri = `${FileSystem.documentDirectory}${track.filename}`;
      const localLrcUri = localAudioUri.replace(".mp3", ".lrc");
      const audioInfo = await FileSystem.getInfoAsync(localAudioUri);

      if (!audioInfo.exists) {
        try {
          const dl = FileSystem.createDownloadResumable(
            `${VPS_BASE_URL}/downloads/${encodeURIComponent(track.filename)}`,
            localAudioUri,
            {},
            (p) => {
              const perc = (p.totalBytesWritten / p.totalBytesExpectedToWrite) * 100;
              setDownloadProgress(`${Math.round(perc)}%`);
            }
          );
          await dl.downloadAsync();
        } catch {
          setIsDownloading(false);
          alert("Error de conexión con el VPS.");
          return;
        }
      }
      uriToPlay = localAudioUri;

      let lrcContent = "";
      try {
        const lrcInfo = await FileSystem.getInfoAsync(localLrcUri);
        if (!lrcInfo.exists) {
          await FileSystem.downloadAsync(
            `${VPS_BASE_URL}/downloads/${encodeURIComponent(track.filename.replace(".mp3", ".lrc"))}`,
            localLrcUri
          );
        }
        lrcContent = await FileSystem.readAsStringAsync(localLrcUri);
      } catch {
        lrcContent = `[00:00.00] ♫ ${track.title}\n[00:05.00] ${track.artist}\n[00:12.00] Zarate Player v2.0`;
      }
      setLyrics(parseLrc(lrcContent));
    } else {
      setLyrics([{ time: 0, text: `♫ ${track.title}` }, { time: 2000, text: track.artist }]);
    }

    setActiveLyricIndex(-1);
    setCurrentTrackIndex(index);
    setCurrentSource(source);

    if (!track.isVideo) {
      try {
        // Configurar modo de audio antes de crear el sonido
        await Audio.setAudioModeAsync({
          allowsRecordingIOS: false,
          playsInSilentModeIOS: true,
          staysActiveInBackground: true,
          shouldDuckAndroid: false,
        });

        const { sound } = await Audio.Sound.createAsync(
          { uri: uriToPlay },
          { shouldPlay: true, progressUpdateIntervalMillis: 250 },
          onPlaybackStatusUpdate
        );
        soundRef.current = sound;
      } catch (err) {
        console.error("Error cargando audio:", err);
      }
    }
    setIsDownloading(false);
  };

  // -------------------------------------------------------------------------
  // SEEK / ADELANTAR - La función clave corregida
  // La separamos en una función independiente con validación robusta
  // -------------------------------------------------------------------------
  const seekTo = async (newPositionMs: number) => {
    const clampedPos = Math.max(0, Math.min(playbackDuration || 0, newPositionMs));

    const playlist = currentSource === "nube" ? vpsTracks : localTracks;
    const currentTrack = currentTrackIndex !== null ? playlist[currentTrackIndex] : null;

    if (currentTrack?.isVideo && videoRef.current) {
      try {
        await videoRef.current.setPositionAsync(clampedPos);
        setPlaybackPosition(clampedPos);
      } catch (err) {
        console.error("Video seek error:", err);
      }
    } else if (soundRef.current && isLoadedRef.current) {
      try {
        await soundRef.current.setPositionAsync(clampedPos);
        setPlaybackPosition(clampedPos);
      } catch (err) {
        console.error("Audio seek error:", err);
      }
    } else {
      console.warn("Seek ignorado: audio no está listo aún.");
    }
  };

  const handleSkipForward = () => seekTo(playbackPosition + 10000);
  const handleSkipBackward = () => seekTo(playbackPosition - 10000);

  const handleProgressBarPress = (event: any) => {
    if (playbackDuration === 0) return;
    const { locationX } = event.nativeEvent;
    const percent = Math.max(0, Math.min(1, locationX / progressBarWidth));
    seekTo(percent * playbackDuration);
  };

  // -------------------------------------------------------------------------
  // Play / Pause
  // -------------------------------------------------------------------------
  const handlePlayPause = async () => {
    const playlist = currentSource === "nube" ? vpsTracks : localTracks;

    if (currentTrackIndex === null) {
      if (playlist.length > 0) loadTrack(0, activeTab);
      return;
    }

    const track = playlist[currentTrackIndex];
    if (track.isVideo && videoRef.current) {
      isPlaying ? await videoRef.current.pauseAsync() : await videoRef.current.playAsync();
    } else if (soundRef.current) {
      isPlaying ? await soundRef.current.pauseAsync() : await soundRef.current.playAsync();
    }
  };

  // -------------------------------------------------------------------------
  // Siguiente / Anterior (respetan el modo shuffle)
  // -------------------------------------------------------------------------
  const handleNextTrack = () => {
    const playlist = currentSource === "nube" ? vpsTracks : localTracks;
    if (playlist.length === 0) return;

    if (playMode === "shuffle") {
      let nextIdx = Math.floor(Math.random() * playlist.length);
      if (playlist.length > 1) while (nextIdx === currentTrackIndex) nextIdx = Math.floor(Math.random() * playlist.length);
      loadTrack(nextIdx, currentSource);
    } else if (currentTrackIndex !== null && currentTrackIndex + 1 < playlist.length) {
      loadTrack(currentTrackIndex + 1, currentSource);
    } else {
      loadTrack(0, currentSource);
    }
  };

  const handlePrevTrack = () => {
    const playlist = currentSource === "nube" ? vpsTracks : localTracks;
    if (playlist.length === 0) return;

    if (playMode === "shuffle") {
      let prevIdx = Math.floor(Math.random() * playlist.length);
      if (playlist.length > 1) while (prevIdx === currentTrackIndex) prevIdx = Math.floor(Math.random() * playlist.length);
      loadTrack(prevIdx, currentSource);
    } else if (currentTrackIndex !== null && currentTrackIndex - 1 >= 0) {
      loadTrack(currentTrackIndex - 1, currentSource);
    } else {
      loadTrack(playlist.length - 1, currentSource);
    }
  };

  // -------------------------------------------------------------------------
  // Ciclar modo de reproducción
  // -------------------------------------------------------------------------
  const cyclePlayMode = () => {
    setPlayMode((prev) => {
      if (prev === "normal") return "repeat_one";
      if (prev === "repeat_one") return "shuffle";
      return "normal";
    });
  };

  const playModeIcon = () => {
    if (playMode === "repeat_one") return "🔂";
    if (playMode === "shuffle") return "🔀";
    return "🔁";
  };

  const playModeLabel = () => {
    if (playMode === "repeat_one") return "REPETIR";
    if (playMode === "shuffle") return "ALEATORIO";
    return "NORMAL";
  };

  // -------------------------------------------------------------------------
  // Explorador de archivos local (.mp3 y .mp4)
  // -------------------------------------------------------------------------
  const openFileExplorer = async () => {
    try {
      const result = await DocumentPicker.getDocumentAsync({
        type: ["audio/mpeg", "video/mp4"],
        copyToCacheDirectory: true,
      });
      if (!result.canceled && result.assets?.length > 0) {
        const asset = result.assets[0];
        const isVideo = asset.name.toLowerCase().endsWith(".mp4");
        const newTrack: Track = {
          id: `local-${Date.now()}`,
          title: asset.name.replace(/\.[^/.]+$/, ""),
          artist: isVideo ? "Video Local" : "Audio Local",
          filename: asset.name,
          uri: asset.uri,
          isVideo,
        };
        setLocalTracks((prev) => [...prev, newTrack]);
      }
    } catch (err) {
      console.error("Error abriendo explorador:", err);
    }
  };

  // -------------------------------------------------------------------------
  // RENDER
  // -------------------------------------------------------------------------
  const playlist = currentSource === "nube" ? vpsTracks : localTracks;
  const browseList = activeTab === "nube" ? vpsTracks : localTracks;
  const activeTrack = currentTrackIndex !== null ? playlist[currentTrackIndex] : null;

  const progressPercent = playbackDuration > 0 ? (playbackPosition / playbackDuration) * 100 : 0;

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="light-content" backgroundColor="#060810" />

      {/* ═══════════════════════════════════════════════ HEADER */}
      <View style={styles.header}>
        <Text style={styles.logoAccent}>&lt;</Text>
        <Text style={styles.logoText}>ZARATE PLAYER</Text>
        <Text style={styles.logoAccent}>/&gt;</Text>
      </View>

      {/* ═══════════════════════════════════════════════ TABS */}
      <View style={styles.tabContainer}>
        {(["nube", "local"] as const).map((tab) => (
          <TouchableOpacity
            key={tab}
            style={[styles.tabButton, activeTab === tab && styles.tabButtonActive]}
            onPress={() => setActiveTab(tab)}
          >
            <Text style={[styles.tabButtonText, activeTab === tab && styles.tabButtonTextActive]}>
              {tab === "nube" ? "☁ VPS NUBE" : "📂 DISCO LOCAL"}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      {/* ═══════════════════════════════════════════════ ÁREA CENTRAL */}
      <View style={styles.contentContainer}>
        {isDownloading ? (
          <View style={styles.loadingContainer}>
            <ActivityIndicator size="large" color="#00f0ff" />
            <Text style={styles.loadingText}>SINCRONIZANDO...</Text>
            <Text style={styles.progressText}>{downloadProgress}</Text>
          </View>

        ) : activeTrack?.isVideo ? (
          /* VIDEO PLAYER 16:9 RESPONSIVO */
          <View style={styles.videoWrapper}>
            <Video
              ref={videoRef}
              style={styles.videoPlayer}
              source={{ uri: activeTrack.uri || "" }}
              resizeMode={ResizeMode.CONTAIN}
              shouldPlay={true}
              onPlaybackStatusUpdate={onPlaybackStatusUpdate}
            />
          </View>

        ) : currentTrackIndex !== null && !activeTrack?.isVideo ? (
          /* ESFERA DE PLASMA + LETRAS KARAOKE PARA AUDIO MP3 */
          <View style={{ flex: 1 }}>
            <PlasmaOrb isPlaying={isPlaying} />
            <FlatList
              ref={lyricsListRef}
              data={lyrics}
              keyExtractor={(_, i) => i.toString()}
              showsVerticalScrollIndicator={false}
              getItemLayout={(_, i) => ({ length: 44, offset: 44 * i, index: i })}
              style={{ flex: 1 }}
              renderItem={({ item, index }) => {
                const isActive = index === activeLyricIndex;
                return (
                  <View style={[styles.lyricRow, { height: 44 }]}>
                    <Text style={[styles.lyricText, isActive && styles.lyricTextActive]}>
                      {item.text}
                    </Text>
                  </View>
                );
              }}
            />
          </View>

        ) : (
          /* LISTA DE CANCIONES PARA EXPLORAR */
          <View style={styles.browserContainer}>
            {activeTab === "local" && (
              <TouchableOpacity style={styles.importButton} onPress={openFileExplorer}>
                <Text style={styles.importButtonText}>➕  EXPLORAR MULTIMEDIA  (.mp3 / .mp4)</Text>
              </TouchableOpacity>
            )}
            <ScrollView showsVerticalScrollIndicator={false}>
              {browseList.length === 0 ? (
                <View style={styles.emptyContainer}>
                  <Text style={styles.emptyText}>
                    {activeTab === "local" ? "Importa archivos de tu dispositivo." : "Conectando con el VPS..."}
                  </Text>
                </View>
              ) : (
                browseList.map((item, idx) => {
                  const isActive = currentTrackIndex === idx && currentSource === activeTab;
                  return (
                    <TouchableOpacity
                      key={item.id}
                      style={[styles.trackListItem, isActive && styles.trackListItemActive]}
                      onPress={() => loadTrack(idx, activeTab)}
                    >
                      <View style={[styles.trackNumBadge, isActive && styles.trackNumBadgeActive]}>
                        <Text style={styles.trackListNum}>
                          {isActive && isPlaying ? "♫" : item.isVideo ? "📹" : (idx + 1).toString()}
                        </Text>
                      </View>
                      <View style={styles.trackListText}>
                        <Text style={[styles.trackListName, isActive && styles.trackListNameActive]} numberOfLines={1}>
                          {item.title}
                        </Text>
                        <Text style={styles.trackListSub}>{item.artist}</Text>
                      </View>
                      <Text style={[styles.trackPlayIcon, isActive && styles.trackPlayIconActive]}>▶</Text>
                    </TouchableOpacity>
                  );
                })
              )}
            </ScrollView>
          </View>
        )}
      </View>

      {/* ═══════════════════════════════════════════════ INFO TRACK */}
      <View style={styles.trackInfoContainer}>
        <Text style={styles.trackTitle} numberOfLines={1} ellipsizeMode="tail">
          {activeTrack ? activeTrack.title : "Ninguna pista activa"}
        </Text>
        <View style={styles.trackSubRow}>
          <Text style={styles.trackArtist}>{activeTrack ? activeTrack.artist : "Selecciona para iniciar"}</Text>
          {/* CHIP DE MODO REPRODUCCIÓN */}
          <TouchableOpacity style={styles.playModeChip} onPress={cyclePlayMode}>
            <Text style={styles.playModeIcon}>{playModeIcon()}</Text>
            <Text style={styles.playModeText}>{playModeLabel()}</Text>
          </TouchableOpacity>
        </View>
      </View>

      {/* ═══════════════════════════════════════════════ BARRA DE PROGRESO INTERACTIVA */}
      <View style={styles.progressRow}>
        <Text style={styles.progressTimeText}>{formatTime(playbackPosition)}</Text>
        <TouchableOpacity
          activeOpacity={1}
          style={styles.progressBarTouchArea}
          onPress={handleProgressBarPress}
          onLayout={(e) => setProgressBarWidth(e.nativeEvent.layout.width)}
        >
          <View style={styles.progressBarBg}>
            <View style={[styles.progressBarFill, { width: `${progressPercent}%` }]} />
            <View style={[styles.progressBarHandle, { left: `${progressPercent}%` }]} />
          </View>
        </TouchableOpacity>
        <Text style={styles.progressTimeText}>{formatTime(playbackDuration)}</Text>
      </View>

      {/* ═══════════════════════════════════════════════ CONTROLES */}
      <View style={styles.controlsContainer}>
        {/* -10s */}
        <TouchableOpacity style={styles.skipBtn} onPress={handleSkipBackward}>
          <Text style={styles.skipIcon}>-10s</Text>
        </TouchableOpacity>
        {/* Anterior */}
        <TouchableOpacity style={styles.controlButton} onPress={handlePrevTrack}>
          <Text style={styles.controlIcon}>◀◀</Text>
        </TouchableOpacity>
        {/* Play/Pause - GRANDE */}
        <TouchableOpacity style={styles.playButton} onPress={handlePlayPause}>
          <Text style={styles.playIcon}>{isPlaying ? "❚❚" : "▶"}</Text>
        </TouchableOpacity>
        {/* Siguiente */}
        <TouchableOpacity style={styles.controlButton} onPress={handleNextTrack}>
          <Text style={styles.controlIcon}>▶▶</Text>
        </TouchableOpacity>
        {/* +10s */}
        <TouchableOpacity style={styles.skipBtn} onPress={handleSkipForward}>
          <Text style={styles.skipIcon}>+10s</Text>
        </TouchableOpacity>
      </View>

      {/* ═══════════════════════════════════════════════ ECUALIZADOR DINÁMICO */}
      <View style={styles.visualizerContainer}>
        {visualizerBars.map((anim, i) => (
          <Animated.View
            key={i}
            style={[styles.visualizerBar, { height: anim, backgroundColor: i % 2 === 0 ? "#ff007f" : "#00f0ff" }]}
          />
        ))}
      </View>
    </SafeAreaView>
  );
}

// ==========================================================================
// ESTILOS
// ==========================================================================
const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#060810",
    paddingHorizontal: 14,
    justifyContent: "space-between",
  },
  header: {
    flexDirection: "row",
    justifyContent: "center",
    alignItems: "center",
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: "rgba(0,240,255,0.08)",
  },
  logoAccent: {
    fontSize: 16,
    fontWeight: "bold",
    color: "#00f0ff",
    textShadowColor: "rgba(0,240,255,0.7)",
    textShadowOffset: { width: 0, height: 0 },
    textShadowRadius: 8,
  },
  logoText: {
    fontSize: 18,
    fontWeight: "900",
    letterSpacing: 3,
    color: "#fff",
    marginHorizontal: 8,
  },
  tabContainer: {
    flexDirection: "row",
    backgroundColor: "rgba(13,14,25,0.8)",
    borderRadius: 12,
    padding: 3,
    marginVertical: 8,
    borderWidth: 1,
    borderColor: "rgba(255,255,255,0.04)",
  },
  tabButton: {
    flex: 1,
    paddingVertical: 8,
    alignItems: "center",
    borderRadius: 8,
  },
  tabButtonActive: {
    backgroundColor: "rgba(255,0,127,0.15)",
    borderWidth: 1,
    borderColor: "rgba(255,0,127,0.35)",
  },
  tabButtonText: {
    fontSize: 11,
    fontWeight: "800",
    color: "#66677a",
    fontFamily: Platform.OS === "ios" ? "Courier" : "monospace",
  },
  tabButtonTextActive: {
    color: "#ff007f",
  },
  contentContainer: {
    flex: 1,
    marginVertical: 8,
  },
  loadingContainer: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
  },
  loadingText: {
    color: "#00f0ff",
    fontFamily: Platform.OS === "ios" ? "Courier" : "monospace",
    marginTop: 14,
    fontSize: 11,
    letterSpacing: 2,
  },
  progressText: {
    color: "#ff007f",
    fontSize: 16,
    fontWeight: "bold",
    marginTop: 5,
  },
  videoWrapper: {
    width: "100%",
    aspectRatio: 16 / 9,
    backgroundColor: "#000",
    borderRadius: 14,
    overflow: "hidden",
    borderWidth: 2,
    borderColor: "#ff007f",
    shadowColor: "#ff007f",
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.6,
    shadowRadius: 16,
    elevation: 10,
    alignSelf: "center",
  },
  videoPlayer: {
    width: "100%",
    height: "100%",
  },
  lyricRow: {
    height: 52,
    justifyContent: "center",
    alignItems: "center",
    paddingHorizontal: 8,
  },
  lyricText: {
    fontSize: 14,
    color: "#3a3b52",
    textAlign: "center",
    fontWeight: "600",
  },
  lyricTextActive: {
    fontSize: 19,
    color: "#00f0ff",
    fontWeight: "900",
    textShadowColor: "rgba(0,240,255,0.8)",
    textShadowOffset: { width: 0, height: 0 },
    textShadowRadius: 12,
  },
  browserContainer: {
    flex: 1,
  },
  importButton: {
    backgroundColor: "rgba(0,240,255,0.06)",
    borderWidth: 1,
    borderColor: "rgba(0,240,255,0.28)",
    borderRadius: 10,
    paddingVertical: 12,
    alignItems: "center",
    marginBottom: 10,
  },
  importButtonText: {
    color: "#00f0ff",
    fontWeight: "bold",
    fontSize: 11,
    fontFamily: Platform.OS === "ios" ? "Courier" : "monospace",
    letterSpacing: 0.5,
  },
  emptyContainer: {
    alignItems: "center",
    paddingTop: 40,
  },
  emptyText: {
    color: "#44455a",
    fontSize: 13,
    fontStyle: "italic",
  },
  trackListItem: {
    flexDirection: "row",
    backgroundColor: "rgba(14,16,32,0.6)",
    padding: 10,
    borderRadius: 10,
    marginBottom: 6,
    alignItems: "center",
    borderWidth: 1,
    borderColor: "rgba(255,255,255,0.03)",
  },
  trackListItemActive: {
    borderColor: "rgba(0,240,255,0.3)",
    backgroundColor: "rgba(0,240,255,0.04)",
  },
  trackNumBadge: {
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: "rgba(0,240,255,0.05)",
    justifyContent: "center",
    alignItems: "center",
  },
  trackNumBadgeActive: {
    backgroundColor: "rgba(0,240,255,0.15)",
  },
  trackListNum: {
    color: "#00f0ff",
    fontSize: 11,
    fontWeight: "bold",
  },
  trackListText: {
    flex: 1,
    marginLeft: 10,
  },
  trackListName: {
    color: "#dde",
    fontSize: 13,
    fontWeight: "700",
  },
  trackListNameActive: {
    color: "#00f0ff",
  },
  trackListSub: {
    color: "#667",
    fontSize: 10,
    marginTop: 1,
  },
  trackPlayIcon: {
    color: "#333",
    fontSize: 12,
    fontWeight: "bold",
  },
  trackPlayIconActive: {
    color: "#ff007f",
  },
  trackInfoContainer: {
    alignItems: "center",
    marginBottom: 4,
    paddingHorizontal: 10,
  },
  trackTitle: {
    fontSize: 15,
    fontWeight: "bold",
    color: "#fff",
    textAlign: "center",
    marginBottom: 5,
  },
  trackSubRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 12,
  },
  trackArtist: {
    fontSize: 12,
    color: "#ff007f",
    fontWeight: "600",
  },
  playModeChip: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "rgba(255,0,127,0.08)",
    borderWidth: 1,
    borderColor: "rgba(255,0,127,0.2)",
    borderRadius: 20,
    paddingHorizontal: 8,
    paddingVertical: 3,
    gap: 3,
  },
  playModeIcon: {
    fontSize: 11,
  },
  playModeText: {
    color: "#ff007f",
    fontSize: 9,
    fontWeight: "bold",
    fontFamily: Platform.OS === "ios" ? "Courier" : "monospace",
    letterSpacing: 0.5,
  },
  progressRow: {
    flexDirection: "row",
    alignItems: "center",
    marginVertical: 10,
  },
  progressTimeText: {
    color: "#556",
    fontSize: 10,
    width: 30,
    textAlign: "center",
    fontFamily: Platform.OS === "ios" ? "Courier" : "monospace",
  },
  progressBarTouchArea: {
    flex: 1,
    paddingVertical: 12,
    marginHorizontal: 6,
  },
  progressBarBg: {
    height: 4,
    backgroundColor: "rgba(255,255,255,0.05)",
    borderRadius: 2,
    position: "relative",
  },
  progressBarFill: {
    height: "100%",
    backgroundColor: "#00f0ff",
    borderRadius: 2,
  },
  progressBarHandle: {
    position: "absolute",
    top: -5,
    width: 14,
    height: 14,
    borderRadius: 7,
    backgroundColor: "#fff",
    borderWidth: 2,
    borderColor: "#00f0ff",
    marginLeft: -7,
    shadowColor: "#00f0ff",
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 1,
    shadowRadius: 6,
    elevation: 4,
  },
  controlsContainer: {
    flexDirection: "row",
    justifyContent: "center",
    alignItems: "center",
    marginBottom: 14,
    gap: 12,
  },
  controlButton: {
    width: 50,
    height: 50,
    borderRadius: 25,
    backgroundColor: "rgba(14,17,34,0.85)",
    borderWidth: 1.5,
    borderColor: "rgba(0,240,255,0.2)",
    justifyContent: "center",
    alignItems: "center",
    shadowColor: "#00f0ff",
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.2,
    shadowRadius: 6,
    elevation: 3,
  },
  playButton: {
    width: 68,
    height: 68,
    borderRadius: 34,
    backgroundColor: "rgba(14,17,34,0.9)",
    borderWidth: 2,
    borderColor: "#ff007f",
    justifyContent: "center",
    alignItems: "center",
    shadowColor: "#ff007f",
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.55,
    shadowRadius: 14,
    elevation: 6,
  },
  skipBtn: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: "rgba(14,17,34,0.6)",
    borderWidth: 1,
    borderColor: "rgba(255,0,127,0.2)",
    justifyContent: "center",
    alignItems: "center",
  },
  controlIcon: {
    color: "#00f0ff",
    fontSize: 14,
    fontWeight: "bold",
  },
  skipIcon: {
    color: "#ff007f",
    fontSize: 9,
    fontWeight: "bold",
    fontFamily: Platform.OS === "ios" ? "Courier" : "monospace",
  },
  playIcon: {
    color: "#ff007f",
    fontSize: 22,
    fontWeight: "bold",
    marginLeft: 3,
  },
  visualizerContainer: {
    flexDirection: "row",
    justifyContent: "center",
    alignItems: "flex-end",
    height: 36,
    marginBottom: 4,
    gap: 4,
  },
  visualizerBar: {
    width: 3,
    borderRadius: 2,
  },
});
