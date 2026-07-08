import React, { useState, useEffect, useRef } from "react";
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
  StatusBar,
  ScrollView
} from "react-native";
import { Audio, AVPlaybackStatus, Video, ResizeMode } from "expo-av";
import * as FileSystem from "expo-file-system";
import * as DocumentPicker from "expo-document-picker";

// ==========================================================================
// CONFIGURACIÓN Y TIPADO DE LA APLICACIÓN
// ==========================================================================
const VPS_BASE_URL = "http://168.194.102.34:3006"; // IP real de tu VPS

interface Track {
  id: string;
  title: string;
  artist: string;
  filename: string;
  uri?: string;       // URI local de la canción si es del explorador
  isVideo?: boolean;   // Flag para saber si es un archivo .mp4
}

interface LyricLine {
  time: number; // Tiempo en milisegundos
  text: string;
}

// ==========================================================================
// PARSER DE ARCHIVOS .LRC (KARAOKE DE LETRAS SINCRO)
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

      const fractionMultiplier = fractionStr.length === 2 ? 10 : 1;
      const milliseconds = parseInt(fractionStr, 10) * fractionMultiplier;

      const totalTime = (minutes * 60 + seconds) * 1000 + milliseconds;
      parsedLines.push({ time: totalTime, text });
    }
  }

  return parsedLines.sort((a, b) => a.time - b.time);
};

export default function App() {
  // Pestaña Activa: "nube" (VPS) o "local" (Explorador del Dispositivo)
  const [activeTab, setActiveTab] = useState<"nube" | "local">("nube");

  // Listas de Canciones
  const [vpsTracks, setVpsTracks] = useState<Track[]>([]);
  const [localTracks, setLocalTracks] = useState<Track[]>([]);
  
  // Reproducción y Audio
  const [currentTrackIndex, setCurrentTrackIndex] = useState<number | null>(null);
  const [currentSource, setCurrentSource] = useState<"nube" | "local">("nube");
  const [isPlaying, setIsPlaying] = useState<boolean>(false);
  const [playbackPosition, setPlaybackPosition] = useState<number>(0);
  const [playbackDuration, setPlaybackDuration] = useState<number>(0);
  const [lyrics, setLyrics] = useState<LyricLine[]>([]);
  const [activeLyricIndex, setActiveLyricIndex] = useState<number>(-1);
  const [isDownloading, setIsDownloading] = useState<boolean>(false);
  const [downloadProgress, setDownloadProgress] = useState<string>("0%");
  
  // Ancho dinámico de la barra de progreso para Adelantar/Retroceder
  const [progressBarWidth, setProgressBarWidth] = useState<number>(200);

  // Referencias de Componentes
  const soundRef = useRef<Audio.Sound | null>(null);
  const videoRef = useRef<Video | null>(null);
  const lyricsListRef = useRef<FlatList<LyricLine> | null>(null);

  // Animaciones del Ecualizador Cyberpunk (12 Barras)
  const visualizerBars = useRef(
    Array.from({ length: 12 }, () => new Animated.Value(5))
  ).current;

  // Cargar lista inicial de canciones de la nube (Servidor VPS)
  useEffect(() => {
    const fetchVpsTracks = async () => {
      try {
        const mockTracks: Track[] = [
          {
            id: "v1",
            title: "Billy Idol - Eyes Without A Face",
            artist: "Billy Idol",
            filename: "Billy Idol - Eyes Without A Face.mp3"
          },
          {
            id: "v2",
            title: "Morir De Amor",
            artist: "Kudai",
            filename: "Morir De Amor.mp3"
          },
          {
            id: "v3",
            title: "Baby Come Back",
            artist: "Player",
            filename: "Baby Come Back.mp3"
          }
        ];
        setVpsTracks(mockTracks);
      } catch (err) {
        console.error("Error al listar canciones del VPS:", err);
      }
    };
    fetchVpsTracks();
  }, []);

  // Bucle de Animación del Ecualizador
  useEffect(() => {
    let animLoop: Animated.CompositeAnimation | null = null;

    if (isPlaying) {
      const animations = visualizerBars.map((bar) => {
        return Animated.loop(
          Animated.sequence([
            Animated.timing(bar, {
              toValue: Math.random() * 65 + 5,
              duration: Math.random() * 200 + 100,
              useNativeDriver: false
            }),
            Animated.timing(bar, {
              toValue: 5,
              duration: Math.random() * 200 + 100,
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

  // Manejo de Estados de Reproducción (Tanto para Audio como Video)
  const onPlaybackStatusUpdate = (status: AVPlaybackStatus) => {
    if (status.isLoaded) {
      setPlaybackPosition(status.positionMillis);
      setPlaybackDuration(status.durationMillis || 0);
      setIsPlaying(status.isPlaying);

      // Auto-scrollear letras sincro
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

  // Cargar y reproducir cualquier pista (Nube o Local)
  const loadTrack = async (index: number, source: "nube" | "local" = activeTab) => {
    const playlist = source === "nube" ? vpsTracks : localTracks;
    if (index < 0 || index >= playlist.length) return;

    setIsDownloading(true);
    setDownloadProgress("0%");
    
    // Resetear sonido y video anteriores
    if (soundRef.current) {
      await soundRef.current.unloadAsync();
      soundRef.current = null;
    }
    if (videoRef.current) {
      await videoRef.current.unloadAsync();
    }

    const track = playlist[index];
    let uriToPlay = track.uri || "";

    // Si es una pista de la Nube (VPS), descargar o verificar archivo
    if (source === "nube") {
      const localAudioUri = `${FileSystem.documentDirectory}${track.filename}`;
      const localLrcUri = localAudioUri.replace(".mp3", ".lrc");

      const audioFileInfo = await FileSystem.getInfoAsync(localAudioUri);
      if (!audioFileInfo.exists) {
        try {
          const downloadRes = FileSystem.createDownloadResumable(
            `${VPS_BASE_URL}/downloads/${encodeURIComponent(track.filename)}`,
            localAudioUri,
            {},
            (progress) => {
              const perc = (progress.totalBytesWritten / progress.totalBytesExpectedToWrite) * 100;
              setDownloadProgress(`${Math.round(perc)}%`);
            }
          );
          await downloadRes.downloadAsync();
        } catch (err) {
          console.error("Error descargando pista:", err);
          setIsDownloading(false);
          alert("Error de conexión con el VPS.");
          return;
        }
      }
      uriToPlay = localAudioUri;

      // Intentar cargar lyrics .lrc
      let lrcContent = "";
      try {
        const lrcUrl = `${VPS_BASE_URL}/downloads/${encodeURIComponent(track.filename.replace(".mp3", ".lrc"))}`;
        const lrcFileInfo = await FileSystem.getInfoAsync(localLrcUri);
        if (!lrcFileInfo.exists) {
          await FileSystem.downloadAsync(lrcUrl, localLrcUri);
        }
        lrcContent = await FileSystem.readAsStringAsync(localLrcUri);
      } catch {
        lrcContent = `[00:00.00] Inicia reproducción...\n[00:08.00] Escuchando: ${track.title}\n[00:20.00] Zarate Player v2.0 - El Sonido del Futuro.`;
      }
      setLyrics(parseLrc(lrcContent));
    } else {
      // Si es pista Local, no hay letras sincro por defecto
      setLyrics([
        { time: 0, text: "Reproduciendo archivo local" },
        { time: 3000, text: track.title }
      ]);
    }

    setActiveLyricIndex(-1);
    setCurrentTrackIndex(index);
    setCurrentSource(source);

    try {
      if (track.isVideo) {
        // La reproducción de video la maneja el componente Video de expo-av directamente en Render
        setIsDownloading(false);
      } else {
        const { sound } = await Audio.Sound.createAsync(
          { uri: uriToPlay },
          { shouldPlay: true },
          onPlaybackStatusUpdate
        );
        soundRef.current = sound;
        setIsDownloading(false);
      }
    } catch (err) {
      console.error("Error cargando pista en player:", err);
      setIsDownloading(false);
    }
  };

  // Explorar el Dispositivo para Vincular Audio / Video (.mp3, .mp4)
  const openFileExplorer = async () => {
    try {
      const result = await DocumentPicker.getDocumentAsync({
        type: ["audio/mpeg", "video/mp4"],
        copyToCacheDirectory: true
      });

      if (!result.canceled && result.assets && result.assets.length > 0) {
        const asset = result.assets[0];
        const isVideo = asset.name.toLowerCase().endsWith(".mp4");
        
        const newTrack: Track = {
          id: `local-${Date.now()}`,
          title: asset.name.replace(/\.[^/.]+$/, ""), // Quitar extensión
          artist: isVideo ? "Video Local" : "Audio Local",
          filename: asset.name,
          uri: asset.uri,
          isVideo: isVideo
        };

        setLocalTracks((prev) => [...prev, newTrack]);
        alert(`¡Archivo "${asset.name}" vinculado con éxito!`);
      }
    } catch (err) {
      console.error("Error al abrir explorador de archivos:", err);
    }
  };

  const handlePlayPause = async () => {
    const playlist = currentSource === "nube" ? vpsTracks : localTracks;
    if (playlist.length === 0) return;

    if (currentTrackIndex === null) {
      loadTrack(0, activeTab);
      return;
    }

    const currentTrack = playlist[currentTrackIndex];

    if (currentTrack.isVideo && videoRef.current) {
      if (isPlaying) {
        await videoRef.current.pauseAsync();
      } else {
        await videoRef.current.playAsync();
      }
    } else if (soundRef.current) {
      if (isPlaying) {
        await soundRef.current.pauseAsync();
      } else {
        await soundRef.current.playAsync();
      }
    }
  };

  const handleNextTrack = () => {
    const playlist = currentSource === "nube" ? vpsTracks : localTracks;
    if (playlist.length === 0) return;

    if (currentTrackIndex !== null && currentTrackIndex + 1 < playlist.length) {
      loadTrack(currentTrackIndex + 1, currentSource);
    } else {
      loadTrack(0, currentSource);
    }
  };

  const handlePrevTrack = () => {
    const playlist = currentSource === "nube" ? vpsTracks : localTracks;
    if (playlist.length === 0) return;

    if (currentTrackIndex !== null && currentTrackIndex - 1 >= 0) {
      loadTrack(currentTrackIndex - 1, currentSource);
    } else {
      loadTrack(playlist.length - 1, currentSource);
    }
  };

  // Adelantar o Retroceder tocando en la barra de progreso (Scrubbing interactivo)
  const handleProgressBarPress = async (event: any) => {
    if (playbackDuration === 0) return;
    const { locationX } = event.nativeEvent;
    
    // Calcular porcentaje de pulsación sobre el ancho medido de la barra
    const percent = Math.max(0, Math.min(1, locationX / progressBarWidth));
    const newPosition = percent * playbackDuration;
    
    try {
      if (activeTrack?.isVideo && videoRef.current) {
        await videoRef.current.setPositionAsync(newPosition);
      } else if (soundRef.current) {
        await soundRef.current.setPositionAsync(newPosition);
      }
      setPlaybackPosition(newPosition);
    } catch (err) {
      console.error("Error al adelantar posición:", err);
    }
  };

  const handleProgressBarLayout = (event: any) => {
    const { width } = event.nativeEvent.layout;
    setProgressBarWidth(width);
  };

  // Adelantar +10 segundos
  const handleSkipForward = async () => {
    const newPosition = Math.min(playbackDuration, playbackPosition + 10000);
    try {
      if (activeTrack?.isVideo && videoRef.current) {
        await videoRef.current.setPositionAsync(newPosition);
      } else if (soundRef.current) {
        await soundRef.current.setPositionAsync(newPosition);
      }
      setPlaybackPosition(newPosition);
    } catch (err) {
      console.error("Error skip forward:", err);
    }
  };

  // Retroceder -10 segundos
  const handleSkipBackward = async () => {
    const newPosition = Math.max(0, playbackPosition - 10000);
    try {
      if (activeTrack?.isVideo && videoRef.current) {
        await videoRef.current.setPositionAsync(newPosition);
      } else if (soundRef.current) {
        await soundRef.current.setPositionAsync(newPosition);
      }
      setPlaybackPosition(newPosition);
    } catch (err) {
      console.error("Error skip backward:", err);
    }
  };

  const playlist = currentSource === "nube" ? vpsTracks : localTracks;
  const activeTrack = currentTrackIndex !== null ? playlist[currentTrackIndex] : null;

  // Formatear tiempos para el reproductor (MM:SS)
  const formatTime = (millis: number) => {
    const totalSeconds = millis / 1000;
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = Math.floor(totalSeconds % 60);
    return `${minutes}:${seconds < 10 ? "0" : ""}${seconds}`;
  };

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="light-content" backgroundColor="#070810" />

      {/* HEADER LOGO STYLISH IOS CYBERPUNK */}
      <View style={styles.header}>
        <Text style={styles.logoAccent}>&lt;</Text>
        <Text style={styles.logoText}>ZARATE PLAYER</Text>
        <Text style={styles.logoAccent}>/&gt;</Text>
      </View>

      {/* TABS DE SELECCIÓN NUBE / LOCAL */}
      <View style={styles.tabContainer}>
        <TouchableOpacity
          style={[styles.tabButton, activeTab === "nube" && styles.tabButtonActive]}
          onPress={() => setActiveTab("nube")}
        >
          <Text style={[styles.tabButtonText, activeTab === "nube" && styles.tabButtonTextActive]}>
            ☁ VPS NUBE
          </Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.tabButton, activeTab === "local" && styles.tabButtonActive]}
          onPress={() => setActiveTab("local")}
        >
          <Text style={[styles.tabButtonText, activeTab === "local" && styles.tabButtonTextActive]}>
            📂 DISCO LOCAL
          </Text>
        </TouchableOpacity>
      </View>

      {/* ÁREA DE CONTENIDO (LYRICS / EXPLORADOR / PANTALLA VIDEO) */}
      <View style={styles.contentContainer}>
        {isDownloading ? (
          <View style={styles.loadingContainer}>
            <ActivityIndicator size="large" color="#00f0ff" />
            <Text style={styles.loadingText}>SINCRONIZANDO CON VPS...</Text>
            <Text style={styles.progressText}>{downloadProgress}</Text>
          </View>
        ) : activeTrack && activeTrack.isVideo && currentSource === activeTab ? (
          /* PANTALLA DE VIDEO (.MP4) CON ASPECT RATIO 16:9 RESPONSIVE */
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
        ) : activeTab === "nube" ? (
          /* SECCIÓN DE LETRAS SINCRO (MODO NUBE) */
          lyrics.length > 0 ? (
            <FlatList
              ref={lyricsListRef}
              data={lyrics}
              keyExtractor={(_, index) => index.toString()}
              showsVerticalScrollIndicator={false}
              getItemLayout={(_, index) => ({
                length: 50,
                offset: 50 * index,
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
            <View style={styles.emptyContainer}>
              <Text style={styles.emptyText}>Explora las canciones cargadas en tu VPS</Text>
              <ScrollView style={styles.vpsList}>
                {vpsTracks.map((item, idx) => (
                  <TouchableOpacity
                    key={item.id}
                    style={styles.trackListItem}
                    onPress={() => loadTrack(idx, "nube")}
                  >
                    <Text style={styles.trackListNum}>{idx + 1}</Text>
                    <View style={styles.trackListText}>
                      <Text style={styles.trackListName}>{item.title}</Text>
                      <Text style={styles.trackListSub}>{item.artist}</Text>
                    </View>
                    <Text style={styles.trackPlayIcon}>▶</Text>
                  </TouchableOpacity>
                ))}
              </ScrollView>
            </View>
          )
        ) : (
          /* EXPLORADOR DE ARCHIVOS LOCALES */
          <View style={styles.localExplorerContainer}>
            <TouchableOpacity style={styles.importButton} onPress={openFileExplorer}>
              <Text style={styles.importButtonText}>➕ EXPLORAR & VINCULAR MULTIMEDIA (.mp3 / .mp4)</Text>
            </TouchableOpacity>

            {localTracks.length > 0 ? (
              <FlatList
                data={localTracks}
                keyExtractor={(item) => item.id}
                showsVerticalScrollIndicator={false}
                renderItem={({ item, index }) => {
                  const isActive = currentTrackIndex === index && currentSource === "local";
                  return (
                    <TouchableOpacity
                      style={[styles.trackListItem, isActive && styles.trackListItemActive]}
                      onPress={() => loadTrack(index, "local")}
                    >
                      <Text style={styles.trackListNum}>{item.isVideo ? "📹" : "🎵"}</Text>
                      <View style={styles.trackListText}>
                        <Text style={[styles.trackListName, isActive && styles.trackListNameActive]}>
                          {item.title}
                        </Text>
                        <Text style={styles.trackListSub}>
                          {item.isVideo ? "Video MP4" : "Audio MP3"}
                        </Text>
                      </View>
                      <Text style={styles.trackPlayIcon}>▶</Text>
                    </TouchableOpacity>
                  );
                }}
              />
            ) : (
              <View style={styles.emptyContainer}>
                <Text style={styles.emptyText}>No has importado archivos locales aún.</Text>
                <Text style={styles.emptySubText}>Pulsa el botón de arriba para buscar en tu dispositivo.</Text>
              </View>
            )}
          </View>
        )}
      </View>

      {/* INFORMACIÓN DEL TRACK ACTIVO */}
      <View style={styles.trackInfoContainer}>
        <Text style={styles.trackTitle} numberOfLines={1} ellipsizeMode="tail">
          {activeTrack ? activeTrack.title : "Ninguna pista activa"}
        </Text>
        <Text style={styles.trackArtist}>
          {activeTrack ? activeTrack.artist : "Selecciona para iniciar"}
        </Text>
      </View>

      {/* BARRA DE PROGRESO INTERACTIVA DE REPRODUCCIÓN (TAP TO SEEK / ADELANTAR) */}
      <View style={styles.progressContainer}>
        <Text style={styles.progressTimeText}>{formatTime(playbackPosition)}</Text>
        
        {/* Envoltura táctil para adelantar al hacer clic en cualquier parte de la barra */}
        <TouchableOpacity
          activeOpacity={1}
          style={styles.progressBarTouchArea}
          onPress={handleProgressBarPress}
          onLayout={handleProgressBarLayout}
        >
          <View style={styles.progressBarBg}>
            <View
              style={[
                styles.progressBarFill,
                {
                  width: `${
                    playbackDuration > 0
                      ? (playbackPosition / playbackDuration) * 100
                      : 0
                  }%`
                }
              ]}
            />
            {/* Cabezal deslizante estilo iOS */}
            <View
              style={[
                styles.progressBarHandle,
                {
                  left: `${
                    playbackDuration > 0
                      ? (playbackPosition / playbackDuration) * 100
                      : 0
                  }%`
                }
              ]}
            />
          </View>
        </TouchableOpacity>

        <Text style={styles.progressTimeText}>{formatTime(playbackDuration)}</Text>
      </View>

      {/* CONTROLES MULTIMEDIA CIRCULARES (IOS + NEÓN) CON ACCIÓN DE ADELANTAR +-10s */}
      <View style={styles.controlsContainer}>
        <TouchableOpacity style={styles.controlButtonSmall} onPress={handleSkipBackward} title="Retroceder 10s">
          <Text style={styles.controlIconSmall}>-10s</Text>
        </TouchableOpacity>

        <TouchableOpacity style={styles.controlButton} onPress={handlePrevTrack}>
          <Text style={styles.controlIcon}>◀◀</Text>
        </TouchableOpacity>

        <TouchableOpacity style={[styles.controlButton, styles.playButton]} onPress={handlePlayPause}>
          <Text style={styles.playIcon}>{isPlaying ? "❚❚" : "▶"}</Text>
        </TouchableOpacity>

        <TouchableOpacity style={styles.controlButton} onPress={handleNextTrack}>
          <Text style={styles.controlIcon}>▶▶</Text>
        </TouchableOpacity>

        <TouchableOpacity style={styles.controlButtonSmall} onPress={handleSkipForward} title="Adelantar 10s">
          <Text style={styles.controlIconSmall}>+10s</Text>
        </TouchableOpacity>
      </View>

      {/* ECUALIZADOR DINÁMICO (CAVA DIGITAL SIMULADOR) */}
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
// ESTILOS DE LA INTERFAZ FUSION DE CRISTAL DE IOS & NEÓN CYBERPUNK (RESPONSIVE)
// ==========================================================================
const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#070810",
    paddingHorizontal: 16,
    justifyContent: "space-between"
  },
  header: {
    flexDirection: "row",
    justifyContent: "center",
    alignItems: "center",
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: "rgba(0, 240, 255, 0.08)"
  },
  logoAccent: {
    fontSize: 16,
    fontWeight: "bold",
    color: "#00f0ff",
    textShadowColor: "rgba(0, 240, 255, 0.6)",
    textShadowOffset: { width: 0, height: 0 },
    textShadowRadius: 8
  },
  logoText: {
    fontSize: 18,
    fontWeight: "900",
    letterSpacing: 3,
    color: "#ffffff",
    marginHorizontal: 8,
    textShadowColor: "rgba(0, 240, 255, 0.3)",
    textShadowOffset: { width: 0, height: 0 },
    textShadowRadius: 10
  },
  tabContainer: {
    flexDirection: "row",
    backgroundColor: "rgba(13, 14, 25, 0.75)",
    borderRadius: 12,
    padding: 3,
    marginVertical: 8,
    borderWidth: 1,
    borderColor: "rgba(255, 255, 255, 0.04)"
  },
  tabButton: {
    flex: 1,
    paddingVertical: 8,
    alignItems: "center",
    borderRadius: 8
  },
  tabButtonActive: {
    backgroundColor: "rgba(255, 0, 127, 0.15)",
    borderWidth: 1,
    borderColor: "rgba(255, 0, 127, 0.3)"
  },
  tabButtonText: {
    fontSize: 11,
    fontWeight: "800",
    color: "#88899a",
    fontFamily: "monospace"
  },
  tabButtonTextActive: {
    color: "#ff007f",
    textShadowColor: "rgba(255, 0, 127, 0.5)",
    textShadowOffset: { width: 0, height: 0 },
    textShadowRadius: 6
  },
  contentContainer: {
    flex: 1,
    marginVertical: 10,
    justifyContent: "center"
  },
  lyricRow: {
    height: 50,
    justifyContent: "center",
    alignItems: "center",
    paddingHorizontal: 8
  },
  lyricText: {
    fontSize: 14,
    color: "#464858",
    textAlign: "center",
    fontWeight: "600"
  },
  lyricTextActive: {
    fontSize: 18,
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
    fontSize: 11,
    letterSpacing: 1
  },
  progressText: {
    color: "#ff007f",
    fontSize: 16,
    fontWeight: "bold",
    marginTop: 5
  },
  emptyContainer: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center"
  },
  emptyText: {
    color: "#56586e",
    textAlign: "center",
    fontSize: 13,
    fontStyle: "italic",
    marginBottom: 8
  },
  emptySubText: {
    color: "#3a3b4c",
    fontSize: 11,
    textAlign: "center"
  },
  vpsList: {
    width: "100%",
    marginTop: 10
  },
  trackListItem: {
    flexDirection: "row",
    backgroundColor: "rgba(18, 20, 36, 0.5)",
    padding: 10,
    borderRadius: 10,
    marginBottom: 6,
    alignItems: "center",
    borderWidth: 1,
    borderColor: "rgba(255, 255, 255, 0.03)"
  },
  trackListItemActive: {
    borderColor: "rgba(0, 240, 255, 0.3)",
    backgroundColor: "rgba(0, 240, 255, 0.05)"
  },
  trackListNum: {
    color: "#00f0ff",
    fontSize: 13,
    fontWeight: "bold",
    width: 25,
    textAlign: "center"
  },
  trackListText: {
    flex: 1,
    marginLeft: 8
  },
  trackListName: {
    color: "#ffffff",
    fontSize: 13,
    fontWeight: "700"
  },
  trackListNameActive: {
    color: "#00f0ff"
  },
  trackListSub: {
    color: "#888",
    fontSize: 10,
    marginTop: 1
  },
  trackPlayIcon: {
    color: "#ff007f",
    fontSize: 13,
    fontWeight: "bold"
  },
  localExplorerContainer: {
    flex: 1
  },
  importButton: {
    backgroundColor: "rgba(0, 240, 255, 0.08)",
    borderWidth: 1,
    borderColor: "rgba(0, 240, 255, 0.3)",
    borderRadius: 10,
    paddingVertical: 12,
    alignItems: "center",
    marginBottom: 12,
    shadowColor: "#00f0ff",
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.1,
    shadowRadius: 5
  },
  importButtonText: {
    color: "#00f0ff",
    fontWeight: "bold",
    fontSize: 11,
    fontFamily: "monospace",
    letterSpacing: 0.5
  },
  videoWrapper: {
    width: "100%",
    aspectRatio: 16 / 9,
    maxHeight: 320,
    backgroundColor: "#000",
    borderRadius: 12,
    overflow: "hidden",
    borderWidth: 2,
    borderColor: "#ff007f",
    shadowColor: "#ff007f",
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.5,
    shadowRadius: 15,
    elevation: 8,
    alignSelf: "center"
  },
  videoPlayer: {
    width: "100%",
    height: "100%"
  },
  trackInfoContainer: {
    alignItems: "center",
    marginBottom: 5,
    paddingHorizontal: 20
  },
  trackTitle: {
    fontSize: 16,
    fontWeight: "bold",
    color: "#ffffff",
    textAlign: "center",
    marginBottom: 4
  },
  trackArtist: {
    fontSize: 12,
    color: "#ff007f",
    fontWeight: "600",
    textShadowColor: "rgba(255, 0, 127, 0.4)",
    textShadowOffset: { width: 0, height: 0 },
    textShadowRadius: 5
  },
  progressContainer: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginVertical: 10
  },
  progressTimeText: {
    color: "#888",
    fontSize: 10,
    width: 32,
    textAlign: "center",
    fontFamily: "monospace"
  },
  progressBarTouchArea: {
    flex: 1,
    paddingVertical: 10, // Mayor área táctil para facilitar pulsación en móviles
    marginHorizontal: 5
  },
  progressBarBg: {
    height: 5,
    backgroundColor: "rgba(255, 255, 255, 0.05)",
    borderRadius: 3,
    position: "relative"
  },
  progressBarFill: {
    height: "100%",
    backgroundColor: "#00f0ff",
    borderRadius: 3,
    shadowColor: "#00f0ff",
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.8,
    shadowRadius: 5
  },
  progressBarHandle: {
    position: "absolute",
    top: -3,
    width: 11,
    height: 11,
    borderRadius: 6,
    backgroundColor: "#ffffff",
    borderWidth: 1.5,
    borderColor: "#00f0ff",
    marginLeft: -5,
    shadowColor: "#00f0ff",
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.8,
    shadowRadius: 4,
    elevation: 2
  },
  controlsContainer: {
    flexDirection: "row",
    justifyContent: "center",
    alignItems: "center",
    marginBottom: 15,
    gap: 15
  },
  controlButton: {
    width: 54,
    height: 54,
    borderRadius: 27,
    backgroundColor: "rgba(17, 19, 36, 0.8)",
    borderWidth: 1.5,
    borderColor: "rgba(0, 240, 255, 0.18)",
    justifyContent: "center",
    alignItems: "center",
    shadowColor: "#00f0ff",
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.25,
    shadowRadius: 8,
    elevation: 4
  },
  controlButtonSmall: {
    width: 42,
    height: 42,
    borderRadius: 21,
    backgroundColor: "rgba(17, 19, 36, 0.6)",
    borderWidth: 1,
    borderColor: "rgba(255, 0, 127, 0.15)",
    justifyContent: "center",
    alignItems: "center",
    shadowColor: "#ff007f",
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.15,
    shadowRadius: 5,
    elevation: 2
  },
  playButton: {
    width: 66,
    height: 66,
    borderRadius: 33,
    borderColor: "#ff007f",
    shadowColor: "#ff007f",
    shadowOpacity: 0.45,
    shadowRadius: 12
  },
  controlIcon: {
    color: "#00f0ff",
    fontSize: 14,
    fontWeight: "bold"
  },
  controlIconSmall: {
    color: "#ff007f",
    fontSize: 10,
    fontWeight: "bold",
    fontFamily: "monospace"
  },
  playIcon: {
    color: "#ff007f",
    fontSize: 20,
    fontWeight: "bold",
    marginLeft: 3
  },
  visualizerContainer: {
    flexDirection: "row",
    justifyContent: "center",
    alignItems: "flex-end",
    height: 40,
    marginBottom: 5,
    gap: 5
  },
  visualizerBar: {
    width: 3.5,
    backgroundColor: "#ff007f",
    borderRadius: 2,
    shadowColor: "#ff007f",
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.6,
    shadowRadius: 6
  }
});
