/**
 * ZARATE PLAYER — Root Entry Point
 * Wraps the app in an Error Boundary + PlayerProvider.
 * Any unhandled exception is caught and shows a recovery screen
 * instead of crashing the APK.
 */
import React, { Component, ErrorInfo } from 'react';
import {
  ActivityIndicator,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { PlayerProvider } from './src/context/PlayerContext';
import PlayerScreen from './src/screens/PlayerScreen';

// ==========================================================================
// ERROR BOUNDARY — Scientific crash shield
// ==========================================================================
interface EBState {
  hasError: boolean;
  message: string;
}

class ErrorBoundary extends Component<
  { children: React.ReactNode },
  EBState
> {
  constructor(props: any) {
    super(props);
    this.state = { hasError: false, message: '' };
  }

  static getDerivedStateFromError(error: Error): EBState {
    return { hasError: true, message: error.message || 'Unknown error' };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('[ZARATE ERROR BOUNDARY]', error, info);
  }

  reset = () => this.setState({ hasError: false, message: '' });

  render() {
    if (this.state.hasError) {
      return (
        <View style={eb.container}>
          <Text style={eb.icon}>⚠</Text>
          <Text style={eb.title}>ZARATE PLAYER — SYSTEM FAULT</Text>
          <Text style={eb.message}>{this.state.message}</Text>
          <TouchableOpacity style={eb.btn} onPress={this.reset}>
            <Text style={eb.btnText}>REINICIAR MÓDULO</Text>
          </TouchableOpacity>
        </View>
      );
    }
    return this.props.children;
  }
}

const eb = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#090a0f',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 32,
  },
  icon: { fontSize: 52, marginBottom: 16 },
  title: {
    color: '#ff007f',
    fontSize: 14,
    fontWeight: '900',
    letterSpacing: 2,
    textAlign: 'center',
    marginBottom: 16,
    fontFamily: 'monospace',
  },
  message: {
    color: '#556',
    fontSize: 12,
    textAlign: 'center',
    marginBottom: 32,
    fontFamily: 'monospace',
  },
  btn: {
    backgroundColor: 'rgba(255,0,127,0.12)',
    borderWidth: 1,
    borderColor: '#ff007f',
    borderRadius: 8,
    paddingHorizontal: 28,
    paddingVertical: 12,
  },
  btnText: {
    color: '#ff007f',
    fontSize: 12,
    fontWeight: 'bold',
    fontFamily: 'monospace',
    letterSpacing: 1,
  },
});

// ==========================================================================
// SPLASH SCREEN — Shown while modules initialize
// ==========================================================================
function SplashScreen() {
  return (
    <View style={splash.container}>
      <ActivityIndicator size="large" color="#00f0ff" />
      <Text style={splash.title}>ZARATE PLAYER</Text>
      <Text style={splash.sub}>INICIALIZANDO MÓDULOS...</Text>
    </View>
  );
}

const splash = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#090a0f',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 16,
  },
  title: {
    color: '#00f0ff',
    fontSize: 22,
    fontWeight: '900',
    letterSpacing: 6,
    fontFamily: 'monospace',
    textShadowColor: 'rgba(0,240,255,0.6)',
    textShadowOffset: { width: 0, height: 0 },
    textShadowRadius: 12,
  },
  sub: {
    color: '#334',
    fontSize: 11,
    letterSpacing: 2,
    fontFamily: 'monospace',
  },
});

// ==========================================================================
// ROOT APP
// ==========================================================================
export default function App() {
  return (
    <ErrorBoundary>
      <PlayerProvider>
        <PlayerScreen />
      </PlayerProvider>
    </ErrorBoundary>
  );
}
