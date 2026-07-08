import React, { useEffect, useRef } from 'react';
import { Animated, FlatList, StyleSheet, Text, View } from 'react-native';
import { LyricLine } from '../types';

// ==========================================================================
// NEXUS PLAYER — KaraokeView
// Real-time synchronized lyric display with smooth auto-scroll.
// Active line glows cyan and scales up; past/future lines are dimmed.
// ==========================================================================

interface Props {
  lyrics: LyricLine[];
  activeLyricIndex: number;
}

const ITEM_HEIGHT = 48;

const LyricItem = React.memo(
  ({ item, isActive }: { item: LyricLine; isActive: boolean }) => {
    const scaleAnim = useRef(new Animated.Value(isActive ? 1.1 : 1)).current;
    const opacityAnim = useRef(new Animated.Value(isActive ? 1 : 0.3)).current;

    useEffect(() => {
      Animated.parallel([
        Animated.spring(scaleAnim, {
          toValue: isActive ? 1.1 : 1,
          useNativeDriver: true,
          tension: 120,
          friction: 8,
        }),
        Animated.timing(opacityAnim, {
          toValue: isActive ? 1 : 0.3,
          duration: 250,
          useNativeDriver: true,
        }),
      ]).start();
    }, [isActive]);

    return (
      <View style={styles.row}>
        <Animated.Text
          style={[
            styles.text,
            isActive && styles.textActive,
            { opacity: opacityAnim, transform: [{ scale: scaleAnim }] },
          ]}
          numberOfLines={2}
        >
          {item.text}
        </Animated.Text>
      </View>
    );
  }
);

export default function KaraokeView({ lyrics, activeLyricIndex }: Props) {
  const listRef = useRef<FlatList<LyricLine>>(null);

  useEffect(() => {
    if (activeLyricIndex >= 0 && listRef.current) {
      try {
        listRef.current.scrollToIndex({
          index: activeLyricIndex,
          animated: true,
          viewPosition: 0.4,
        });
      } catch {
        // scrollToIndex can fail on short lists — ignore
      }
    }
  }, [activeLyricIndex]);

  if (!lyrics.length) return null;

  return (
    <FlatList
      ref={listRef}
      data={lyrics}
      keyExtractor={(_, i) => i.toString()}
      showsVerticalScrollIndicator={false}
      getItemLayout={(_, i) => ({
        length: ITEM_HEIGHT,
        offset: ITEM_HEIGHT * i,
        index: i,
      })}
      style={styles.list}
      renderItem={({ item, index }) => (
        <LyricItem item={item} isActive={index === activeLyricIndex} />
      )}
      initialScrollIndex={0}
      onScrollToIndexFailed={() => {}}
    />
  );
}

const styles = StyleSheet.create({
  list: {
    flex: 1,
  },
  row: {
    height: ITEM_HEIGHT,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 12,
  },
  text: {
    fontSize: 14,
    color: '#3a3b55',
    textAlign: 'center',
    fontWeight: '600',
  },
  textActive: {
    fontSize: 17,
    color: '#00f0ff',
    fontWeight: '900',
    textShadowColor: 'rgba(0,240,255,0.9)',
    textShadowOffset: { width: 0, height: 0 },
    textShadowRadius: 10,
  },
});
