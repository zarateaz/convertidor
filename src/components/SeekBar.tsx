import React, { useRef, useState } from 'react';
import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';

// ==========================================================================
// NEXUS PLAYER — SeekBar
// Tappable progress bar with live time labels and draggable thumb.
// ==========================================================================

interface Props {
  positionMs: number;
  durationMs: number;
  onSeek: (ms: number) => void;
}

export function formatTime(ms: number): string {
  const total = Math.floor(ms / 1000);
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${m}:${s < 10 ? '0' : ''}${s}`;
}

export default function SeekBar({ positionMs, durationMs, onSeek }: Props) {
  const [barWidth, setBarWidth] = useState(200);

  const percent = durationMs > 0 ? (positionMs / durationMs) * 100 : 0;

  const handlePress = (e: any) => {
    if (durationMs === 0) return;
    const { locationX } = e.nativeEvent;
    const ratio = Math.max(0, Math.min(1, locationX / barWidth));
    onSeek(ratio * durationMs);
  };

  return (
    <View style={styles.row}>
      <Text style={styles.time}>{formatTime(positionMs)}</Text>

      <TouchableOpacity
        activeOpacity={1}
        style={styles.trackTouchArea}
        onPress={handlePress}
        onLayout={(e) => setBarWidth(e.nativeEvent.layout.width)}
      >
        {/* Track background */}
        <View style={styles.track}>
          {/* Filled portion */}
          <View style={[styles.fill, { width: `${percent}%` }]} />
          {/* Thumb */}
          <View style={[styles.thumb, { left: `${percent}%` }]} />
        </View>
      </TouchableOpacity>

      <Text style={styles.time}>{formatTime(durationMs)}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    marginVertical: 10,
    paddingHorizontal: 2,
  },
  time: {
    color: '#55566a',
    fontSize: 10,
    fontFamily: 'monospace',
    width: 32,
    textAlign: 'center',
  },
  trackTouchArea: {
    flex: 1,
    paddingVertical: 14,   // enlarged touch target for mobile
    marginHorizontal: 6,
  },
  track: {
    height: 4,
    backgroundColor: 'rgba(255,255,255,0.06)',
    borderRadius: 2,
    position: 'relative',
  },
  fill: {
    height: '100%',
    backgroundColor: '#00f0ff',
    borderRadius: 2,
    shadowColor: '#00f0ff',
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.9,
    shadowRadius: 4,
  },
  thumb: {
    position: 'absolute',
    top: -5,
    width: 14,
    height: 14,
    borderRadius: 7,
    backgroundColor: '#fff',
    borderWidth: 2,
    borderColor: '#00f0ff',
    marginLeft: -7,
    shadowColor: '#00f0ff',
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 1,
    shadowRadius: 6,
    elevation: 4,
  },
});
