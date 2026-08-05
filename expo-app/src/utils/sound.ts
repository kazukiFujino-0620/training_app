import * as FileSystem from 'expo-file-system';
import { Audio } from 'expo-av';

let audioModeSet = false;

async function ensureAudioMode() {
  if (audioModeSet) return;
  await Audio.setAudioModeAsync({
    playsInSilentModeIOS: true,
    allowsRecordingIOS: false,
    staysActiveInBackground: false,
    shouldDuckAndroid: false,
    playThroughEarpieceAndroid: false,
  });
  audioModeSet = true;
}

// Generate a sine wave WAV file as base64
function buildWavBase64(frequency: number, durationMs: number): string {
  const sampleRate = 22050;
  const numSamples = Math.floor(sampleRate * durationMs / 1000);
  const dataSize = numSamples * 2;
  const buffer = new Uint8Array(44 + dataSize);
  const view = new DataView(buffer.buffer);

  // RIFF header
  buffer.set([0x52, 0x49, 0x46, 0x46]);           // "RIFF"
  view.setUint32(4, 36 + dataSize, true);
  buffer.set([0x57, 0x41, 0x56, 0x45], 8);         // "WAVE"

  // fmt chunk
  buffer.set([0x66, 0x6D, 0x74, 0x20], 12);        // "fmt "
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true);                      // PCM
  view.setUint16(22, 1, true);                      // mono
  view.setUint32(24, sampleRate, true);
  view.setUint32(28, sampleRate * 2, true);
  view.setUint16(32, 2, true);
  view.setUint16(34, 16, true);

  // data chunk
  buffer.set([0x64, 0x61, 0x74, 0x61], 36);        // "data"
  view.setUint32(40, dataSize, true);

  const fadeSamples = Math.max(1, Math.floor(sampleRate * 0.01)); // 10ms fade
  for (let i = 0; i < numSamples; i++) {
    const t = i / sampleRate;
    let amp = 0.85;
    if (i < fadeSamples) amp *= i / fadeSamples;
    else if (i > numSamples - fadeSamples) amp *= (numSamples - i) / fadeSamples;
    const sample = Math.round(Math.sin(2 * Math.PI * frequency * t) * amp * 32767);
    view.setInt16(44 + i * 2, sample, true);
  }

  let binary = '';
  for (let i = 0; i < buffer.length; i++) {
    binary += String.fromCharCode(buffer[i]);
  }
  return btoa(binary);
}

const fileCache: Record<string, string> = {};

async function getBeepFile(frequency: number, durationMs: number): Promise<string> {
  const key = `${frequency}_${durationMs}`;
  if (fileCache[key]) return fileCache[key];

  const path = `${FileSystem.cacheDirectory}beep_${key}.wav`;
  const info = await FileSystem.getInfoAsync(path);
  if (!info.exists) {
    const base64 = buildWavBase64(frequency, durationMs);
    await FileSystem.writeAsStringAsync(path, base64, {
      encoding: FileSystem.EncodingType.Base64,
    });
  }
  fileCache[key] = path;
  return path;
}

async function playBeep(frequency: number, durationMs: number): Promise<void> {
  try {
    await ensureAudioMode();
    const uri = await getBeepFile(frequency, durationMs);
    const { sound } = await Audio.Sound.createAsync({ uri });
    await sound.playAsync();
    sound.setOnPlaybackStatusUpdate((status) => {
      if (status.isLoaded && status.didJustFinish) {
        sound.unloadAsync();
      }
    });
  } catch {
    // Audio failure is non-fatal
  }
}

// 残り10秒の警告音（660Hz、Web版 playWarningBeep 相当）
export function playWarningBeep(): void {
  playBeep(660, 200);
}

// インターバル終了アラーム（1047Hz×3回、Web版 playEndAlarm 相当）
export async function playEndAlarm(): Promise<void> {
  await playBeep(1047, 300);
  await new Promise<void>((r) => setTimeout(r, 120));
  await playBeep(1047, 300);
  await new Promise<void>((r) => setTimeout(r, 120));
  await playBeep(1047, 300);
}
