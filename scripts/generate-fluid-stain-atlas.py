"""Deterministic low-resolution oil/coolant coverage, with restrained baked wet highlights."""
import math,struct,zlib
from pathlib import Path
w,h=64,32
pixels=bytearray(w*h*4)
for tile in range(2):
    for y in range(32):
        for x in range(32):
            u,v=(x+.5-16)/16,(y+.5-16)/16
            r=math.hypot(u*.95,v*1.08);a=math.atan2(v,u)
            edge=.75+.08*math.sin(a*5+tile)+.05*math.sin(a*9+1)
            noise=((x*19+y*13+tile*7)%17)/17
            alpha=int(255*max(0,min(1,(edge-r)*8)))
            gray=int(175+noise*35+(20 if -.2<u<.4 and -.5<v<-.35 else 0))
            at=(y*w+tile*32+x)*4;pixels[at:at+4]=bytes((gray,gray,gray,alpha))
def chunk(t,d):return struct.pack('!I',len(d))+t+d+struct.pack('!I',zlib.crc32(t+d))
raw=b''.join(b'\0'+pixels[y*w*4:(y+1)*w*4] for y in range(h))
png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('!2I5B',w,h,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b'')
(Path(__file__).resolve().parents[1]/'src/main/resources/assets/mcheli_ce_port/textures/effect/m8_fluid_stains.png').write_bytes(png)
