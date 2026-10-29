"""Original 5x7 pixel glyphs and sparse paint chips; writes a new asset, reads no images."""
from pathlib import Path
from PIL import Image, ImageDraw
DIGITS=['11111/10001/10011/10101/11001/10001/11111','00100/01100/00100/00100/00100/00100/01110','11110/00001/00001/11110/10000/10000/11111','11110/00001/00001/01110/00001/00001/11110','10010/10010/10010/11111/00010/00010/00010','11111/10000/10000/11110/00001/00001/11110','01111/10000/10000/11110/10001/10001/01110','11111/00001/00010/00100/01000/01000/01000','01110/10001/10001/01110/10001/10001/01110','01110/10001/10001/01111/00001/00001/11110']
SYMBOLS=['00100/01110/11011/10001/00000/00000/00000','00100/00100/11111/01110/01110/11011/10001','00000/11111/00000/11111/00000/11111/00000']
im=Image.new('RGBA',(64,64));draw=ImageDraw.Draw(im)
for tile,glyph in enumerate(DIGITS+SYMBOLS):
    ox,oy=(tile%4)*16+3,(tile//4)*16+1
    for y,line in enumerate(glyph.split('/')):
        for x,pixel in enumerate(line):
            if pixel=='1':draw.rectangle((ox+x*2,oy+y*2,ox+x*2+1,oy+y*2+1),fill=(255,255,255,235))
# Asymmetric little bare-metal flecks, with transparent space keeping the paint readable.
for x,y,w in [(1,3,2),(4,9,1),(7,2,1),(10,12,2),(13,5,1),(3,13,2),(9,7,1)]:
    draw.rectangle((48+x,48+y,48+x+w,48+y+1),fill=(255,255,255,180))
im.save(Path(__file__).resolve().parents[1]/'src/main/resources/assets/mcheli_ce_port/textures/effect/m8_aircraft_markings.png')
