import React, { useState, useEffect, useRef, useMemo } from "react";
import {
  StyleSheet,
  Text,
  View,
  TouchableOpacity,
  FlatList,
  Animated,
  Dimensions,
  ActivityIndicator,
  SafeAreaView,
  StatusBar
} from "react-native";
import { Audio, AVPlaybackStatus } from "expo-av";
import * as FileSystem from "expo-file-system";

// ==========================================================================
// CONFIGURACIÓN Y TIPADO DE LA APLICACIÓN
// ==========================================================================
const VPS_BASE_URL = "http://187.127.20.171:3006"; // IP de tu VPS

interface Track {
  id: string;
  title: string;
  artist: string;
  filename: string;
}

interface LyricLine {
  time: number; // Tiempo en milisegundos
  text: string;
}

// ==========================================================================
// PARSER DE ARCHIVOS .LRC (KARAOKE)
// ==========================================================================
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

      // Convertir centésimas/milésimas a milisegundos
      const fractionMultiplier = fractionStr.length === 2 ? 10 : 1;
      const milliseconds = parseInt(fractionStr, 10) * fractionMultiplier;

      const totalTime = (minutes * 60 + seconds) * 1000 + milliseconds;
      parsedLines.push({ time: totalTime, text });
    }
  }

  return parsedLines.sort((a, b) => a.time - b.time);
};

export default function App() {
  // Estado de Tracks y Reproducción
  const [tracks, setTracks] = useState<Track[]>([]);
  const [currentTrackIndex, setCurrentTrackIndex] = useState<number | null>(null);
  const [isPlaying, setIsPlaying] = useState<boolean>(false);
  const [playbackPosition, setPlaybackPosition] = useState<number>(0);
  const [playbackDuration, setPlaybackDuration] = useState<number>(0);
  const [lyrics, setLyrics] = useState<LyricLine[]>([]);
  const [activeLyricIndex, setActiveLyricIndex] = useState<number>(-1);
  const [isDownloading, setIsDownloading] = useState<boolean>(false);
  const [downloadProgress, setDownloadProgress] = useState<string>("0%");

  // Referencias para Audio y UI
  const soundRef = useRef<Audio.Sound | null>(null);
  const lyricsListRef = useRef<FlatList<LyricLine> | null>(null);

  // Animaciones del Ecualizador (CAVA Simulado de 12 Barras)
  const visualizerBars = useRef(
    Array.from({ length: 12 }, () => new Animated.Value(5))
  ).current;

  // Cargar lista de canciones disponibles en el VPS
  useEffect(() => {
    // Nota: Aquí listamos canciones de prueba. Puedes enlazar este fetch
    // a tu API si creas un endpoint de listado en server.py.
    const mockTracks: Track[] = [
      {
        id: "1",
        title: "Billy Idol - Eyes Without A Face",
        artist: "Billy Idol",
        filename: "Billy Idol - Eyes Without A Face.mp3"
      },
      {
        id: "2",
        title: "Amor de Antes",
        artist: "Zarate Mix",
        filename: "Amor de Antes.mp3"
      }
    ];
    setTracks(mockTracks);
  }, []);

  // Animación del Ecualizador
  useEffect(() => {
    let animLoop: Animated.CompositeAnimation | null = null;

    if (isPlaying) {
      const animations = visualizerBars.map((bar) => {
        return Animated.loop(
          Animated.sequence([
            Animated.timing(bar, {
              toValue: Math.random() * 55 + 5,
              duration: Math.random() * 250 + 150,
              useNativeDriver: false
            }),
            Animated.timing(bar, {
              toValue: 5,
              duration: Math.random() * 250 + 150,
              useNativeDriver: false
            })
          ])
        );
      });
      animLoop = Animated.parallel(animations);
      animLoop.start();
    } else {
      visualizerBars.forEach((bar) => {
        Animated.timing(bar, {
          toValue: 5,
          duration: 300,
          useNativeDriver: false
        }).start();
      });
    }

    return () => {
      if (animLoop) animLoop.stop();
    };
  }, [isPlaying]);

  // Manejo de Estado de Reproducción de Audio
  const onPlaybackStatusUpdate = (status: AVPlaybackStatus) => {
    if (status.isLoaded) {
      setPlaybackPosition(status.positionMillis);
      setPlaybackDuration(status.durationMillis || 0);
      setIsPlaying(status.isPlaying);

      // Buscar la línea de letra correspondiente al tiempo actual
      if (lyrics.length > 0) {
        let index = -1;
        for (let i = 0; i < lyrics.length; i++) {
          if (status.positionMillis >= lyrics[i].time) {
            index = i;
          } else {
            break;
          }
        }
        if (index !== -1 && index !== activeLyricIndex) {
          setActiveLyricIndex(index);
          // Auto-scroll suave de la letra activa
          lyricsListRef.current?.scrollToIndex({
            index,
            animated: true,
            viewPosition: 0.5
          });
        }
      }

      if (status.didJustFinish) {
        handleNextTrack();
      }
    }
  };

  // Descargar y Cargar Pistas
  const loadTrack = async (index: number) => {
    if (index < 0 || index >= tracks.length) return;
    setIsDownloading(true);
    setDownloadProgress("0%");

    const track = tracks[index];
    const localAudioUri = `${FileSystem.documentDirectory}${track.filename}`;
    const localLrcUri = localAudioUri.replace(".mp3", ".lrc");

    // Verificar si el archivo ya fue descargado
    const audioFileInfo = await FileSystem.getInfoAsync(localAudioUri);
    if (!audioFileInfo.exists) {
      try {
        // Descargar el archivo de Audio
        const downloadRes = FileSystem.createDownloadResumable(
          `${VPS_BASE_URL}/downloads/${encodeURIComponent(track.filename)}`,
          localAudioUri,
          {},
          (downloadProgress) => {
            const progress = (
              downloadProgress.totalBytesWritten /
              downloadProgress.totalBytesExpectedToWrite
            ) * 100;
            setDownloadProgress(`${Math.round(progress)}%`);
          }
        );
        await downloadRes.downloadAsync();
      } catch (err) {
        console.error("Error descargando pista:", err);
        setIsDownloading(false);
        alert("Error de conexión con el servidor ZARATE VPS.");
        return;
      }
    }

    // Intentar descargar o leer el archivo de letra .lrc
    let lrcContent = "";
    try {
      const lrcUrl = `${VPS_BASE_URL}/downloads/${encodeURIComponent(
        track.filename.replace(".mp3", ".lrc")
      )}`;
      const lrcFileInfo = await FileSystem.getInfoAsync(localLrcUri);

      if (!lrcFileInfo.exists) {
        await FileSystem.downloadAsync(lrcUrl, localLrcUri);
      }
      lrcContent = await FileSystem.readAsStringAsync(localLrcUri);
    } catch {
      // Si falla la letra, generamos una simulación básica de línea de texto
      lrcContent = `[00:00.00] Inicia la pista...\n[00:10.00] ${track.title}\n[00:20.00] Disfruta del sonido cibernético ZARATE`;
    }

    // Parsear letras
    const parsedLyrics = parseLrc(lrcContent);
    setLyrics(parsedLyrics);
    setActiveLyricIndex(-1);

    // Cargar sonido en expo-av
    if (soundRef.current) {
      await soundRef.current.unloadAsync();
    }

    try {
      const { sound } = await Audio.Sound.createAsync(
        { uri: localAudioUri },
        { shouldPlay: true },
        onPlaybackStatusUpdate
      );
      soundRef.current = sound;
      setCurrentTrackIndex(index);
    } catch (err) {
      console.error("Error al reproducir audio:", err);
    } finally {
      setIsDownloading(false);
    }
  };

  const handlePlayPause = async () => {
    if (!soundRef.current) {
      if (tracks.length > 0) {
        loadTrack(0);
      }
      return;
    }

    if (isPlaying) {
      await soundRef.current.pauseAsync();
    } else {
      await soundRef.current.playAsync();
    }
  };

  const handleNextTrack = () => {
    if (currentTrackIndex !== null && currentTrackIndex + 1 < tracks.length) {
      loadTrack(currentTrackIndex + 1);
    } else {
      loadTrack(0); // Bucle a la primera pista
    }
  };

  const handlePrevTrack = () => {
    if (currentTrackIndex !== null && currentTrackIndex - 1 >= 0) {
      loadTrack(currentTrackIndex - 1);
    } else {
      loadTrack(tracks.length - 1); // Volver al final
    }
  };

  const activeTrack = currentTrackIndex !== null ? tracks[currentTrackIndex] : null;

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="light-content" backgroundColor="#0d0f12" />

      {/* HEADER LOGO */}
      <View style={styles.header}>
        <Text style={styles.logoAccent}>&lt;</Text>
        <Text style={styles.logoText}>ZARATE PLAYER</Text>
        <Text style={styles.logoAccent}>/&gt;</Text>
      </View>

      {/* REPRODUCTOR / LYRICS CONTAINER */}
      <View style={styles.lyricsContainer}>
        {isDownloading ? (
          <View style={styles.loadingContainer}>
            <ActivityIndicator size="large" color="#00f0ff" />
            <Text style={styles.loadingText}>SINCRONIZANDO CON VPS ZARATE...</Text>
            <Text style={styles.progressText}>{downloadProgress}</Text>
          </View>
        ) : lyrics.length > 0 ? (
          <FlatList
            ref={lyricsListRef}
            data={lyrics}
            keyExtractor={(_, index) => index.toString()}
            showsVerticalScrollIndicator={false}
            getItemLayout={(_, index) => ({
              length: 60,
              offset: 60 * index,
              index
            })}
            renderItem={({ item, index }) => {
              const isActive = index === activeLyricIndex;
              return (
                <View style={styles.lyricRow}>
                  <Text style={[styles.lyricText, isActive && styles.lyricTextActive]}>
                    {item.text}
                  </Text>
                </View>
              );
            }}
          />
        ) : (
          <Text style={styles.emptyText}>Selecciona una pista para iniciar</Text>
        )}
      </View>

      {/* INFORMACIÓN DEL TRACK */}
      <View style={styles.trackInfoContainer}>
        <Text style={styles.trackTitle}>{activeTrack ? activeTrack.title : "Ninguna pista activa"}</Text>
        <Text style={styles.trackArtist}>{activeTrack ? activeTrack.artist : "Servidor ZARATE"}</Text>
      </View>

      {/* CONTROLES MULTIMEDIA */}
      <View style={styles.controlsContainer}>
        <TouchableOpacity style={styles.controlButton} onPress={handlePrevTrack}>
          <Text style={styles.controlIcon}>◀◀</Text>
        </TouchableOpacity>
        <TouchableOpacity style={[styles.controlButton, styles.playButton]} onPress={handlePlayPause}>
          <Text style={styles.playIcon}>{isPlaying ? "❚❚" : "▶"}</Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.controlButton} onPress={handleNextTrack}>
          <Text style={styles.controlIcon}>▶▶</Text>
        </TouchableOpacity>
      </View>

      {/* ECUALIZADOR CAVA VISUAL SIMULATOR */}
      <View style={styles.visualizerContainer}>
        {visualizerBars.map((anim, index) => (
          <Animated.View
            key={index}
            style={[
              styles.visualizerBar,
              {
                height: anim
              }
            ]}
          />
        ))}
      </View>
    </SafeAreaView>
  );
}

// ==========================================================================
// ESTILOS DE LA INTERFAZ (CYBERPUNK GLOW)
// ==========================================================================
const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#0d0f12",
    paddingHorizontal: 20,
    justifyContent: "space-between"
  },
  header: {
    flexDirection: "row",
    justifyContent: "center",
    alignItems: "center",
    paddingVertical: 15,
    borderBottomWidth: 1,
    borderBottomColor: "rgba(0, 240, 255, 0.1)"
  },
  logoAccent: {
    fontSize: 20,
    fontWeight: "bold",
    color: "#00f0ff",
    textShadowColor: "rgba(0, 240, 255, 0.5)",
    textShadowOffset: { width: 0, height: 0 },
    textShadowRadius: 10
  },
  logoText: {
    fontSize: 22,
    fontWeight: "900",
    letterSpacing: 3,
    color: "#ffffff",
    marginHorizontal: 10,
    textShadowColor: "rgba(0, 240, 255, 0.4)",
    textShadowOffset: { width: 0, height: 0 },
    textShadowRadius: 15
  },
  lyricsContainer: {
    flex: 1,
    marginVertical: 20,
    justifyContent: "center"
  },
  lyricRow: {
    height: 60,
    justifyContent: "center",
    alignItems: "center",
    paddingHorizontal: 10
  },
  lyricText: {
    fontSize: 16,
    color: "#444444",
    textAlign: "center",
    fontWeight: "600"
  },
  lyricTextActive: {
    fontSize: 22,
    color: "#00f0ff",
    fontWeight: "900",
    textShadowColor: "rgba(0, 240, 255, 0.8)",
    textShadowOffset: { width: 0, height: 0 },
    textShadowRadius: 12
  },
  loadingContainer: {
    alignItems: "center",
    justifyContent: "center"
  },
  loadingText: {
    color: "#00f0ff",
    fontFamily: "monospace",
    marginTop: 15,
    fontSize: 12
  },
  progressText: {
    color: "#ff007f",
    fontSize: 18,
    fontWeight: "bold",
    marginTop: 5
  },
  emptyText: {
    color: "#444444",
    textAlign: "center",
    fontSize: 16,
    fontStyle: "italic"
  },
  trackInfoContainer: {
    alignItems: "center",
    marginBottom: 20
  },
  trackTitle: {
    fontSize: 18,
    fontWeight: "bold",
    color: "#ffffff",
    textAlign: "center",
    marginBottom: 5
  },
  trackArtist: {
    fontSize: 14,
    color: "#ff007f",
    fontWeight: "600",
    textShadowColor: "rgba(255, 0, 127, 0.4)",
    textShadowOffset: { width: 0, height: 0 },
    textShadowRadius: 5
  },
  controlsContainer: {
    flexDirection: "row",
    justifyContent: "center",
    alignItems: "center",
    marginBottom: 30,
    gap: 25
  },
  controlButton: {
    width: 60,
    height: 60,
    borderRadius: 30,
    backgroundColor: "rgba(13, 15, 18, 0.9)",
    borderWidth: 1,
    borderColor: "rgba(0, 240, 255, 0.2)",
    justifyContent: "center",
    alignItems: "center",
    shadowColor: "#00f0ff",
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.3,
    shadowRadius: 10,
    elevation: 5
  },
  playButton: {
    width: 75,
    height: 75,
    borderRadius: 37.5,
    borderColor: "#ff007f",
    shadowColor: "#ff007f",
    shadowOpacity: 0.5,
    shadowRadius: 15
  },
  controlIcon: {
    color: "#00f0ff",
    fontSize: 16,
    fontWeight: "bold"
  },
  playIcon: {
    color: "#ff007f",
    fontSize: 24,
    fontWeight: "bold"
  },
  visualizerContainer: {
    flexDirection: "row",
    justifyContent: "center",
    alignItems: "flex-end",
    height: 60,
    marginBottom: 15,
    gap: 6
  },
  visualizerBar: {
    width: 4,
    backgroundColor: "#ff007f",
    borderRadius: 2,
    shadowColor: "#ff007f",
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.6,
    shadowRadius: 8
  }
});
