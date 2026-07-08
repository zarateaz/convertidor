import React, { useEffect, useRef } from 'react';
import { Animated, StyleSheet, View } from 'react-native';

// ==========================================================================
// NEXUS PLAYER — PlasmaOrb  
// Holographic animated sphere that reacts to playback state.
// ==========================================================================

interface Props {
  isPlaying: boolean;
  size?: number;
}

export default function PlasmaOrb({ isPlaying, size = 130 }: Props) {
  const orbScale    = useRef(new Animated.Value(1)).current;
  const glowOp      = useRef(new Animated.Value(0.2)).current;
  const rotateAnim  = useRef(new Animated.Value(0)).current;
  const rotate2Anim = useRef(new Animated.Value(0)).current;

  // Three expanding waves
  const r1Scale = useRef(new Animated.Value(1)).current;
  const r1Op    = useRef(new Animated.Value(0.7)).current;
  const r2Scale = useRef(new Animated.Value(1)).current;
  const r2Op    = useRef(new Animated.Value(0.5)).current;
  const r3Scale = useRef(new Animated.Value(1)).current;
  const r3Op    = useRef(new Animated.Value(0.3)).current;

  // Plasma particles
  const p1Op = useRef(new Animated.Value(0.3)).current;
  const p2Op = useRef(new Animated.Value(0.7)).current;
  const p3Op = useRef(new Animated.Value(0.5)).current;
  const p4Op = useRef(new Animated.Value(0.2)).current;

  useEffect(() => {
    const loops: Animated.CompositeAnimation[] = [];

    if (!isPlaying) {
      // ── REST STATE: slow gentle breathing ──────────────────────────────
      loops.push(
        Animated.loop(Animated.sequence([
          Animated.parallel([
            Animated.timing(orbScale, { toValue: 1.06, duration: 2000, useNativeDriver: true }),
            Animated.timing(glowOp,   { toValue: 0.4,  duration: 2000, useNativeDriver: true }),
          ]),
          Animated.parallel([
            Animated.timing(orbScale, { toValue: 1.0,  duration: 2000, useNativeDriver: true }),
            Animated.timing(glowOp,   { toValue: 0.12, duration: 2000, useNativeDriver: true }),
          ]),
        ]))
      );
      // Slow rotation
      loops.push(
        Animated.loop(
          Animated.timing(rotateAnim, { toValue: 1, duration: 8000, useNativeDriver: true })
        )
      );
    } else {
      // ── PLAYING STATE: aggressive beat pulse + shockwaves ──────────────

      // Orb heartbeat — mimics kick drum
      loops.push(
        Animated.loop(Animated.sequence([
          Animated.timing(orbScale, { toValue: 1.24, duration: 160, useNativeDriver: true }),
          Animated.timing(orbScale, { toValue: 0.94, duration: 180, useNativeDriver: true }),
          Animated.timing(orbScale, { toValue: 1.16, duration: 150, useNativeDriver: true }),
          Animated.timing(orbScale, { toValue: 1.0,  duration: 420, useNativeDriver: true }),
        ]))
      );

      // Inner glow flicker
      loops.push(
        Animated.loop(Animated.sequence([
          Animated.timing(glowOp, { toValue: 1.0, duration: 180, useNativeDriver: true }),
          Animated.timing(glowOp, { toValue: 0.3, duration: 300, useNativeDriver: true }),
          Animated.timing(glowOp, { toValue: 0.85, duration: 200, useNativeDriver: true }),
          Animated.timing(glowOp, { toValue: 0.2, duration: 480, useNativeDriver: true }),
        ]))
      );

      // Shockwave ring 1 (fastest)
      const wave = (scale: Animated.Value, op: Animated.Value, delay: number, dur: number, maxScale: number, startOp: number) =>
        Animated.loop(Animated.sequence([
          Animated.delay(delay),
          Animated.parallel([
            Animated.sequence([
              Animated.timing(scale, { toValue: maxScale, duration: dur, useNativeDriver: true }),
              Animated.timing(scale, { toValue: 1.0, duration: 0, useNativeDriver: true }),
            ]),
            Animated.sequence([
              Animated.timing(op, { toValue: 0, duration: dur, useNativeDriver: true }),
              Animated.timing(op, { toValue: startOp, duration: 0, useNativeDriver: true }),
            ]),
          ]),
        ]));

      loops.push(wave(r1Scale, r1Op, 0,   650, 2.4, 0.7));
      loops.push(wave(r2Scale, r2Op, 220, 720, 2.8, 0.5));
      loops.push(wave(r3Scale, r3Op, 450, 800, 3.2, 0.35));

      // Orbital ring: fast rotation
      loops.push(
        Animated.loop(
          Animated.timing(rotateAnim, { toValue: 1, duration: 2800, useNativeDriver: true })
        )
      );
      // Second ring counter-rotating
      loops.push(
        Animated.loop(
          Animated.timing(rotate2Anim, { toValue: -1, duration: 4200, useNativeDriver: true })
        )
      );

      // Plasma particles flickering
      const pFlicker = (v: Animated.Value, d1: number, d2: number, delay: number) =>
        Animated.loop(Animated.sequence([
          Animated.delay(delay),
          Animated.timing(v, { toValue: 1,   duration: d1, useNativeDriver: true }),
          Animated.timing(v, { toValue: 0.05, duration: d2, useNativeDriver: true }),
        ]));

      loops.push(pFlicker(p1Op, 280, 320, 0));
      loops.push(pFlicker(p2Op, 350, 300, 100));
      loops.push(pFlicker(p3Op, 200, 400, 220));
      loops.push(pFlicker(p4Op, 320, 280, 350));
    }

    loops.forEach((l) => l.start());
    return () => loops.forEach((l) => l.stop());
  }, [isPlaying]);

  const spin1 = rotateAnim.interpolate({ inputRange: [0, 1], outputRange: ['0deg', '360deg'] });
  const spin2 = rotate2Anim.interpolate({ inputRange: [-1, 0], outputRange: ['-360deg', '0deg'] });

  const containerSize = size * 3.5;

  return (
    <View style={[styles.container, { width: containerSize, height: containerSize }]}>
      {/* ── Shockwave rings ─────────────────────────────────────────── */}
      {([
        { s: r1Scale, o: r1Op, color: 'rgba(0,180,255,0.22)', bw: 1.5 },
        { s: r2Scale, o: r2Op, color: 'rgba(0,100,255,0.16)', bw: 1.0 },
        { s: r3Scale, o: r3Op, color: 'rgba(0,50,220,0.10)',  bw: 0.8 },
      ] as const).map((ring, i) => (
        <Animated.View
          key={i}
          style={[
            styles.ring,
            {
              width: size, height: size, borderRadius: size / 2,
              borderColor: ring.color, borderWidth: ring.bw,
              transform: [{ scale: ring.s as any }],
              opacity: ring.o as any,
            },
          ]}
        />
      ))}

      {/* ── Orbital arc 1 (clockwise) ────────────────────────────────── */}
      <Animated.View
        style={[
          styles.orbit,
          {
            width: size + 48, height: size + 48,
            borderRadius: (size + 48) / 2,
            transform: [{ rotate: spin1 }],
          },
        ]}
      />

      {/* ── Orbital arc 2 (counter-clockwise) ─────────────────────────── */}
      <Animated.View
        style={[
          styles.orbit2,
          {
            width: size + 28, height: size + 28,
            borderRadius: (size + 28) / 2,
            transform: [{ rotate: spin2 }],
          },
        ]}
      />

      {/* ── Core sphere ──────────────────────────────────────────────── */}
      <Animated.View
        style={[
          styles.core,
          {
            width: size, height: size, borderRadius: size / 2,
            transform: [{ scale: orbScale }],
          },
        ]}
      >
        {/* Inner glow */}
        <Animated.View style={[styles.glow, { opacity: glowOp }]} />

        {/* Glass highlight */}
        <View style={styles.highlight} />

        {/* Plasma particles */}
        <Animated.View style={[styles.dot, { top: '22%', left: '28%', width: 6, height: 6, opacity: p1Op }]} />
        <Animated.View style={[styles.dot, { top: '55%', right: '22%', width: 4, height: 4, opacity: p2Op }]} />
        <Animated.View style={[styles.dot, { bottom: '22%', left: '48%', width: 5, height: 5, opacity: p3Op }]} />
        <Animated.View style={[styles.dot, { top: '38%', left: '56%', width: 3, height: 3, opacity: p4Op }]} />

        {/* Horizon line */}
        <View style={styles.horizon} />
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  ring: {
    position: 'absolute',
  },
  orbit: {
    position: 'absolute',
    borderWidth: 1.2,
    borderColor: 'transparent',
    borderTopColor: 'rgba(0,220,255,0.6)',
    borderRightColor: 'rgba(0,130,255,0.3)',
  },
  orbit2: {
    position: 'absolute',
    borderWidth: 0.8,
    borderColor: 'transparent',
    borderBottomColor: 'rgba(0,180,255,0.45)',
    borderLeftColor: 'rgba(0,80,220,0.25)',
  },
  core: {
    backgroundColor: '#000d26',
    borderWidth: 2,
    borderColor: 'rgba(0,210,255,0.85)',
    justifyContent: 'center',
    alignItems: 'center',
    overflow: 'hidden',
    shadowColor: '#00aaff',
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 1,
    shadowRadius: 28,
    elevation: 20,
  },
  glow: {
    position: 'absolute',
    width: '75%',
    height: '75%',
    borderRadius: 200,
    backgroundColor: 'rgba(0,150,255,0.28)',
  },
  highlight: {
    position: 'absolute',
    top: '10%',
    left: '16%',
    width: '36%',
    height: '20%',
    borderRadius: 40,
    backgroundColor: 'rgba(180,240,255,0.18)',
    transform: [{ rotate: '-28deg' }],
  },
  horizon: {
    position: 'absolute',
    top: '52%',
    left: '8%',
    right: '8%',
    height: 1,
    backgroundColor: 'rgba(0,200,255,0.15)',
  },
  dot: {
    position: 'absolute',
    borderRadius: 10,
    backgroundColor: '#00ddff',
  },
});
