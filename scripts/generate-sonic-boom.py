#!/usr/bin/env python3
"""Generate the project's original mono double-pressure-pulse/rumble cue. Requires ffmpeg."""
import math
from pathlib import Path
import random
import struct
import subprocess
import tempfile
import wave

RATE = 44100
DURATION = 1.6
DESTINATION = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/mcheli_ce_port/sounds/m8_sonic_boom.ogg'


def samples():
    rng = random.Random(80226)
    result = []
    low = 0.0
    for index in range(round(RATE * DURATION)):
        t = index / RATE
        white = rng.uniform(-1, 1)
        low = low * .965 + white * .035
        rumble = (low * 1.3 + .09 * math.sin(2 * math.pi * 63 * t)
                  + .06 * math.sin(2 * math.pi * 91 * t))
        value = rumble * min(1, t * 80) * math.exp(-t * 3.7)
        for start, amplitude in [(0.012, .9), (.098, .63)]:
            age = t - start
            if 0 <= age < .07:
                pressure = (1 - 2 * age / .044) * math.exp(-age * 65)
                attack = min(1, age * 2000)
                value += amplitude * attack * (pressure + white * .14 * math.exp(-age * 100))
        value *= min(1, (DURATION - t) / .08)
        result.append(value)
    mean = sum(result) / len(result)
    result = [v - mean for v in result]
    peak = max(abs(v) for v in result)
    return b''.join(struct.pack('<h', round(v / peak * .82 * 32767)) for v in result)


def main():
    DESTINATION.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='m8-boom-') as directory:
        wav = Path(directory) / 'pressure.wav'
        with wave.open(str(wav), 'wb') as output:
            output.setnchannels(1)
            output.setsampwidth(2)
            output.setframerate(RATE)
            output.writeframes(samples())
        subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-i', str(wav),
                        '-c:a', 'libvorbis', '-q:a', '5', str(DESTINATION)], check=True)
    print(DESTINATION)


if __name__ == '__main__':
    main()
