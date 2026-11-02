#!/usr/bin/env python3
"""Builds the 1.21.1 copy of the 26.2 data-generated resources.

Reads ../src/generated/resources and the hand-made item model definitions in
../src/main/resources/assets, and writes common/src/generated/resources:

- recipes: 26.2 writes an ingredient as a string ("minecraft:coal", "#c:ingots/iron") or a list of
  them; 1.21.1 wants {"item": ...} / {"tag": ...} objects.
- item models: 26.2 names an item's model in assets/<ns>/items/<item>.json; 1.21.1 reads
  assets/<ns>/models/item/<item>.json, so each definition becomes a model that inherits the one it
  names (a tool's fallback, a block item's block model). The fuel bucket uses NeoForge's fluid
  container loader, which Fabric ignores (it draws a plain bucket).
- everything else (blockstates, block models, lang, loot tables, advancements, tags) is copied.

Run from mc1.21.1/: python3 scripts/backport_resources.py
"""
import json
import pathlib
import shutil

ROOT = pathlib.Path(__file__).resolve().parent.parent
GENERATED = ROOT.parent / 'src' / 'generated' / 'resources'
MAIN_ASSETS = ROOT.parent / 'src' / 'main' / 'resources' / 'assets'
OUT = ROOT / 'common' / 'src' / 'generated' / 'resources'


def ingredient(value):
    if isinstance(value, list):
        return [ingredient(v) for v in value]
    if isinstance(value, str):
        return {'tag': value[1:]} if value.startswith('#') else {'item': value}
    return value


def recipe(data):
    if 'key' in data:
        data['key'] = {k: ingredient(v) for k, v in data['key'].items()}
    if 'ingredients' in data:
        data['ingredients'] = [ingredient(v) for v in data['ingredients']]
    for key in ('ingredient', 'base', 'addition', 'template'):
        if key in data:
            data[key] = ingredient(data[key])
    return data


def first_model(node, prefer_fallback=True):
    """The model a 26.2 item model definition draws, preferring a custom type's fallback."""
    if isinstance(node, dict):
        if prefer_fallback and 'fallback' in node:
            found = first_model(node['fallback'])
            if found:
                return found
        if node.get('type') == 'minecraft:model' and isinstance(node.get('model'), str):
            return node['model']
        for value in node.values():
            found = first_model(value)
            if found:
                return found
    elif isinstance(node, list):
        for value in node:
            found = first_model(value)
            if found:
                return found
    return None


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + '\n')


def main():
    if OUT.exists():
        shutil.rmtree(OUT)
    for src in GENERATED.rglob('*'):
        if not src.is_file():
            continue
        rel = src.relative_to(GENERATED)
        parts = rel.parts
        if parts[0] == 'assets' and len(parts) > 2 and parts[2] == 'items':
            continue  # 26.2 item model definitions, turned into models below
        dest = OUT / rel
        if parts[0] == 'data' and len(parts) > 2 and parts[2] == 'recipe':
            write(dest, recipe(json.loads(src.read_text())))
        elif parts[0] == 'assets' and len(parts) > 2 and parts[2] == 'lang':
            # The mod's name in the key categories and config screen titles.
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_text(src.read_text(encoding='utf-8').replace('(26.2 Port)', '(1.21.1 Port)'), encoding='utf-8')
        else:
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(src, dest)
    for base in (GENERATED / 'assets', MAIN_ASSETS):
        for definition in base.glob('*/items/*.json'):
            ns = definition.parent.parent.name
            name = definition.stem
            target = OUT / 'assets' / ns / 'models' / 'item' / (name + '.json')
            data = json.loads(definition.read_text())
            if data.get('model', {}).get('type') == 'neoforge:fluid_container':
                textures = data['model']['textures']
                write(target, {
                    'loader': 'neoforge:fluid_container',
                    'fluid': data['model']['fluid'],
                    'parent': 'minecraft:item/generated',
                    'textures': {'layer0': textures['base'], 'particle': textures['base'], **textures},
                })
                continue
            model = first_model(data)
            if model is None or model == f'{ns}:item/{name}':
                continue
            write(target, {'parent': model})


if __name__ == '__main__':
    main()
