"""Original periodic turbine tone for the startup layer; requires Python and ffmpeg.

No samples or third-party recordings. Pitch/volume envelopes are driven in the game.
"""
import math
import struct
import subprocess
import tempfile
import wave
from pathlib import Path

target = Path(__file__).resolve().parents[1] / "src/main/resources/assets/mcheli/sounds/turbine_spool.ogg"
with tempfile.TemporaryDirectory() as work:
    source = Path(work) / "turbine.wav"
    with wave.open(str(source), "wb") as output:
        output.setparams((1, 2, 22050, 0, "NONE", "not compressed"))
        samples = bytearray()
        for frame in range(44100):
            t = frame / 22050
            value = sum(amplitude * math.sin(math.tau * frequency * t)
                        for frequency, amplitude in [(360, .11), (720, .045), (1440, .025), (2880, .008)])
            value += sum(.003 * math.sin(math.tau * (1103 + 137 * harmonic) * t + harmonic)
                         for harmonic in range(12))
            samples.extend(struct.pack("<h", round(value * 32767)))
        output.writeframes(samples)
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(source), "-c:a", "libvorbis", "-q:a", "3",
                    str(target)], check=True)
