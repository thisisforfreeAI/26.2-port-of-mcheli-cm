#!/usr/bin/env python3
"""Original, mono synthesized air-rush / shell-whistle cues. Requires ffmpeg; no sampled assets."""
import math
from pathlib import Path
import random
import struct
import subprocess
import tempfile
import wave

RATE = 44100
ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/mcheli_ce_port/sounds'


def generate(name, shell):
    duration = .85
    rng = random.Random(80326 + shell)
    low = slow = 0
    values = []
    for i in range(round(RATE * duration)):
        t = i / RATE
        white = rng.uniform(-1, 1)
        low = low * .82 + white * .18
        slow = slow * .98 + white * .02
        envelope = math.sin(math.pi * t / duration) ** 1.4
        if shell:
            # Narrow whistling band with noisy edges; no explosion or extra gunshot.
            phase = 2 * math.pi * (1700 * t - 420 * t * t)
            value = (low - slow) * .7 + math.sin(phase) * .16 + math.sin(phase * 1.43) * .06
        else:
            value = slow * 2.8 + low * .6 + .04 * math.sin(2 * math.pi * 83 * t)
        values.append(value * envelope)
    mean = sum(values) / len(values)
    values = [v - mean for v in values]
    peak = max(abs(v) for v in values)
    pcm = b''.join(struct.pack('<h', round(v / peak * .75 * 32767)) for v in values)
    ROOT.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='m8-flyby-') as directory:
        source = Path(directory) / 'cue.wav'
        with wave.open(str(source), 'wb') as output:
            output.setnchannels(1)
            output.setsampwidth(2)
            output.setframerate(RATE)
            output.writeframes(pcm)
        subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-i', str(source),
                        '-c:a', 'libvorbis', '-q:a', '5', str(ROOT / (name + '.ogg'))], check=True)


if __name__ == '__main__':
    generate('m8_rocket_flyby', False)
    generate('m8_shell_flyby', True)
