import React, { useEffect, useRef } from 'react';
import { Animated, StyleSheet, View } from 'react-native';

// ==========================================================================
// NEXUS PLAYER — CavaVisualizer (24-bar CAVA-style equalizer)
// Simulates bass / mid / treble frequency zones with staggered loops.
// ==========================================================================

interface Props {
  isPlaying: boolean;
  barCount?: number;
}

// Frequency profile per bar-zone (normalized 0..1 => multiplied by maxH)
const FREQUENCY_PROFILE = [
  // Low bass: tall, slow
  0.90, 0.95, 1.00, 0.95, 0.88,
  // Mid-bass: medium
  0.75, 0.82, 0.78, 0.72, 0.80,
  // Mids: lively
  0.70, 0.76, 0.80, 0.76, 0.72,
  // Upper mids
  0.60, 0.65, 0.62, 0.58, 0.55,
  // Treble: small, fast
  0.40, 0.38, 0.35, 0.32,
];

function getBarColor(idx: number, total: number): string {
  const ratio = idx / total;
  if (ratio < 0.35) return '#ff007f';   // bass: magenta
  if (ratio < 0.65) return '#a800ff';   // mids: violet
  return '#00f0ff';                     // treble: cyan
}

export default function CavaVisualizer({ isPlaying, barCount = 24 }: Props) {
  const bars = useRef(
    Array.from({ length: barCount }, () => new Animated.Value(4))
  ).current;

  const MAX_H = 50;
  const MIN_H = 4;

  useEffect(() => {
    const loops: Animated.CompositeAnimation[] = [];

    if (!isPlaying) {
      // Decay to minimum
      bars.forEach((bar) => {
        loops.push(
          Animated.loop(
            Animated.sequence([
              Animated.timing(bar, { toValue: MIN_H + 3, duration: 1200, useNativeDriver: false }),
              Animated.timing(bar, { toValue: MIN_H,     duration: 1200, useNativeDriver: false }),
            ])
          )
        );
      });
    } else {
      bars.forEach((bar, i) => {
        const profile = FREQUENCY_PROFILE[i % FREQUENCY_PROFILE.length];
        const maxH    = MAX_H * profile;
        const isLow   = i < 5;
        const isTreble = i >= 20;

        // Duration inversely proportional to frequency (bass slow, treble fast)
        const upMs   = isLow ? 250 : isTreble ? 80 : 150;
        const downMs = isLow ? 300 : isTreble ? 90 : 180;

        // Stagger start so bars are not synchronized
        const delay = (i * 32) % 200;

        loops.push(
          Animated.loop(
            Animated.sequence([
              Animated.delay(delay),
              Animated.timing(bar, {
                toValue: MIN_H + Math.random() * (maxH - MIN_H) * 0.4 + (maxH - MIN_H) * 0.6,
                duration: upMs,
                useNativeDriver: false,
              }),
              Animated.timing(bar, {
                toValue: MIN_H + Math.random() * 8,
                duration: downMs,
                useNativeDriver: false,
              }),
              Animated.timing(bar, {
                toValue: MIN_H + Math.random() * (maxH - MIN_H) * 0.55,
                duration: upMs + 20,
                useNativeDriver: false,
              }),
              Animated.timing(bar, {
                toValue: MIN_H,
                duration: downMs + 30,
                useNativeDriver: false,
              }),
            ])
          )
        );
      });
    }

    loops.forEach((l) => l.start());
    return () => loops.forEach((l) => l.stop());
  }, [isPlaying]);

  return (
    <View style={styles.container}>
      {bars.map((anim, i) => (
        <Animated.View
          key={i}
          style={[
            styles.bar,
            {
              height: anim,
              backgroundColor: getBarColor(i, barCount),
            },
          ]}
        />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'center',
    height: 54,
    gap: 3,
    paddingHorizontal: 4,
  },
  bar: {
    width: 3.5,
    borderRadius: 2,
    minHeight: 4,
    // Note: shadowColor on RN Android requires elevation
    elevation: 2,
  },
});
