#!/usr/bin/env python3
"""Mechanical 26.2 -> 1.21.1 rewrites for the client package (on top of backport.py).

The 26.2 client is written against the queued render pipeline; the shim.client package gives the
same names over 1.21.1's immediate rendering. Re-running it is a no-op.
"""
import re
import sys
from pathlib import Path

S = 'io.github.mcheliceport.shim.client.'
RULES = [
    (r'\bnet\.minecraft\.client\.gui\.GuiGraphicsExtractor\b', S + 'GuiGraphicsExtractor'),
    (r'\bnet\.minecraft\.client\.renderer\.SubmitNodeCollector\b', S + 'SubmitNodeCollector'),
    (r'\bnet\.minecraft\.client\.renderer\.OrderedSubmitNodeCollector\b', S + 'SubmitNodeCollector'),
    (r'\bOrderedSubmitNodeCollector\b', 'SubmitNodeCollector'),
    (r'\bnet\.minecraft\.client\.renderer\.state\.level\.CameraRenderState\b', S + 'CameraRenderState'),
    (r'\bnet\.minecraft\.client\.renderer\.entity\.state\.(EntityRenderState|LivingEntityRenderState)\b', S + r'\1'),
    (r'\bimport net\.minecraft\.client\.renderer\.rendertype\.RenderTypes;\n', ''),
    (r'\bnet\.minecraft\.client\.renderer\.rendertype\.RenderTypes\.', 'net.minecraft.client.renderer.RenderType.'),
    (r'(?<![\w.])RenderTypes\.(entityTranslucent|entityTranslucentEmissive|lines|lightning|entityCutout|entityTranslucentCull|entityCutoutNoCull|eyes|entitySolid)\(',
     r'RenderType.\1('),
    (r'\bnet\.minecraft\.client\.renderer\.rendertype\.RenderType\b', 'net.minecraft.client.renderer.RenderType'),
    (r'\bimport net\.minecraft\.client\.renderer\.RenderPipelines;', 'import ' + S + 'RenderPipelines;'),
    (r'\bnet\.minecraft\.util\.LightCoordsUtil\b', S + 'LightCoordsUtil'),
    (r'\bnet\.minecraft\.util\.ARGB\b', S + 'ARGB'),
    (r'\bnet\.minecraft\.util\.Util\b', 'net.minecraft.Util'),
    (r'\bextends EntityRenderer<', 'extends ' + S + 'StateEntityRenderer<'),
    (r'\bextends net\.minecraft\.client\.renderer\.entity\.EntityRenderer<', 'extends ' + S + 'StateEntityRenderer<'),
    (r'\.mainCamera\(\)', '.getMainCamera()'),
    (r'(?:Minecraft\.getInstance\(\)|mc)\.gameRenderer\.gameRenderState\(\)\.levelRenderState\s*\.cameraRenderState', S + 'CameraRenderState.current()'),
    # Screens: 26.2's extract* overrides are 1.21.1's render* methods.
    (r'public void extractRenderState\(GuiGraphicsExtractor (\w+), int (\w+), int (\w+), float (\w+)\) \{',
     r'public void render(net.minecraft.client.gui.GuiGraphics \1$v, int \2, int \3, float \4) { GuiGraphicsExtractor \1 = GuiGraphicsExtractor.of(\1$v);'),
    (r'super\.extractRenderState\((\w+), (\w+), (\w+), (\w+)\)', r'super.render(\1.vanilla(), \2, \3, \4)'),
    (r'protected void extractLabels\(GuiGraphicsExtractor (\w+), int (\w+), int (\w+)\) \{',
     r'protected void renderLabels(net.minecraft.client.gui.GuiGraphics \1$v, int \2, int \3) { GuiGraphicsExtractor \1 = GuiGraphicsExtractor.of(\1$v);'),
    (r'protected void extractWidgetRenderState\(GuiGraphicsExtractor (\w+), int (\w+), int (\w+), float (\w+)\) \{',
     r'protected void renderWidget(net.minecraft.client.gui.GuiGraphics \1$v, int \2, int \3, float \4) { GuiGraphicsExtractor \1 = GuiGraphicsExtractor.of(\1$v);'),
]

EV = 'io.github.mcheliceport.shim.client.event.'
EVENTS = ('ClientTickEvent|RenderFrameEvent|ViewportEvent|InputEvent|ScreenEvent|ClientPlayerNetworkEvent|RenderHandEvent'
          '|RenderNameTagEvent|CalculateDetachedCameraDistanceEvent|SubmitCustomGeometryEvent|RegisterGuiLayersEvent'
          '|RegisterKeyMappingsEvent|AddClientReloadListenersEvent|EntityRenderersEvent|RegisterMenuScreensEvent')
RULES += [
    (r'\bnet\.neoforged\.neoforge\.client\.event\.(' + EVENTS + r')\b', EV + r'\1'),
    (r'\bnet\.neoforged\.neoforge\.event\.tick\.EntityTickEvent\b', EV + 'EntityTickEvent'),
    (r'\bnet\.neoforged\.neoforge\.client\.gui\.(VanillaGuiLayers|GuiLayer)\b', EV + r'\1'),
    (r'\bnet\.neoforged\.bus\.api\.SubscribeEvent\b', EV + 'SubscribeEvent'),
    (r'@SubscribeEvent\([^)]*\)', '@SubscribeEvent'),
    (r'\bimport net\.neoforged\.bus\.api\.EventPriority;\n', ''),
    (r'\bimport net\.neoforged\.fml\.common\.EventBusSubscriber;\n', ''),
    (r'\bimport net\.neoforged\.api\.distmarker\.Dist;\n', ''),
    (r'@(net\.neoforged\.fml\.common\.)?EventBusSubscriber(\([^)]*\))?\s*\n', '@' + EV + 'ClientSubscriber\n'),
    (r'\b(?:net\.neoforged\.neoforge\.common\.)?NeoForge\.EVENT_BUS\.addListener\(([\w.]+)::(\w+)\)',
     EV + r'ClientBus.listen(\1.class, "\2")'),
    (r'\bmodBus\.addListener\(([\w.]+)::(\w+)\)', EV + r'ClientBus.listen(\1.class, "\2")'),
    (r'\b(?:net\.neoforged\.neoforge\.common\.)?NeoForge\.EVENT_BUS\.addListener\(\(([\w.]+) (\w+)\) ->',
     EV + r'ClientBus.addListener(\1.class, (\1 \2) ->'),
    (r'\bmodBus\.addListener\(\(([\w.]+) (\w+)\) ->', EV + r'ClientBus.addListener(\1.class, (\1 \2) ->'),
    (r'\b(?:net\.minecraft\.util|net\.neoforged\.neoforge\.common\.util)\.TriState\b', S + 'TriState'),
    (r'\bnet\.minecraft\.client\.input\.(KeyEvent|MouseButtonEvent)\b', S + r'\1'),
    # Screen input: 26.2 passes event records, 1.21.1 the raw values.
    (r'public boolean keyPressed\(KeyEvent (\w+)\) \{',
     r'public boolean keyPressed(int \1$k, int \1$s, int \1$m) { KeyEvent \1 = new KeyEvent(\1$k, \1$s, \1$m);'),
    (r'super\.keyPressed\((\w+)\)', r'super.keyPressed(\1.key(), \1.scancode(), \1.modifiers())'),
    (r'public boolean mouseClicked\(MouseButtonEvent (\w+), boolean (\w+)\) \{',
     r'public boolean mouseClicked(double \1$x, double \1$y, int \1$b) { MouseButtonEvent \1 = new MouseButtonEvent(\1$x, \1$y, \1$b); boolean \2 = false;'),
    (r'super\.mouseClicked\((\w+), \w+\)', r'super.mouseClicked(\1.x(), \1.y(), \1.button())'),
    (r'public boolean mouseReleased\(MouseButtonEvent (\w+)\) \{',
     r'public boolean mouseReleased(double \1$x, double \1$y, int \1$b) { MouseButtonEvent \1 = new MouseButtonEvent(\1$x, \1$y, \1$b);'),
    (r'super\.mouseReleased\((\w+)\)', r'super.mouseReleased(\1.x(), \1.y(), \1.button())'),
    (r'public boolean mouseDragged\(MouseButtonEvent (\w+), double (\w+), double (\w+)\) \{',
     r'public boolean mouseDragged(double \1$x, double \1$y, int \1$b, double \2, double \3) { MouseButtonEvent \1 = new MouseButtonEvent(\1$x, \1$y, \1$b);'),
    (r'super\.mouseDragged\((\w+), (\w+), (\w+)\)', r'super.mouseDragged(\1.x(), \1.y(), \1.button(), \2, \3)'),
    (r'public void onClick\(MouseButtonEvent (\w+), boolean (\w+)\) \{',
     r'public void onClick(double \1$x, double \1$y) { MouseButtonEvent \1 = new MouseButtonEvent(\1$x, \1$y, 0); boolean \2 = false;'),
    (r'protected void onDrag\(MouseButtonEvent (\w+), double (\w+), double (\w+)\) \{',
     r'protected void onDrag(double \1$x, double \1$y, double \2, double \3) { MouseButtonEvent \1 = new MouseButtonEvent(\1$x, \1$y, 0);'),
    (r'\.matches\((\w+)\.getKeyEvent\(\)\)', r'.matches(\1.getKeyEvent().key(), \1.getKeyEvent().scancode())'),
    (r'\.matches\((\w+)\)(?=\s*[)&|])', r'.matches(\1)'),
]

RULES += [
    (r'\.getMainCamera\(\)\.position\(\)', '.getMainCamera().getPosition()'),
    (r'\.getMainCamera\(\)\.entity\(\)', '.getMainCamera().getEntity()'),
    (r'(\w+(?:\(\))?)\.gui\.screen\(\)', r'\1.screen'),
    (r'(\w+(?:\(\))?)\.gui\.setScreen\(', r'\1.setScreen('),
    (r'\b(\w+)\.gui\.hud\.isHidden\(\)', r'\1.options.hideGui'),
    (r'\b(\w+)\.gui\.hud\.', r'\1.gui.'),
    (r'\.getDeltaTracker\(\)', '.getTimer()'),
]

def convert(text):
    for pattern, repl in RULES:
        text = re.sub(pattern, repl, text)
    return text


def main(args):
    for arg in args:
        p = Path(arg)
        for f in ([p] if p.is_file() else sorted(p.rglob('*.java'))):
            old = f.read_text()
            new = convert(old)
            if new != old:
                f.write_text(new)


if __name__ == '__main__':
    main(sys.argv[1:])
