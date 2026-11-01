#!/usr/bin/env python3
"""Mechanical 26.2 -> 1.21.1 rewrites for sources copied from ../src/main/java.

Run on a file or a folder: python3 scripts/backport.py common/src/main/java/...
Only renames that are safe everywhere live here; anything that needs judgement is fixed by
hand. Re-running it on already converted code is a no-op.
"""
import re
import sys
from pathlib import Path

# (regex, replacement) applied in order.
RULES = [
    # Identifier was ResourceLocation before 1.21.11.
    (r'\bimport net\.minecraft\.resources\.Identifier;', 'import net.minecraft.resources.ResourceLocation;'),
    (r'\bnet\.minecraft\.resources\.Identifier\b', 'net.minecraft.resources.ResourceLocation'),
    (r'(?<![\w.])Identifier\b', 'ResourceLocation'),
    (r'\.identifier\(\)', '.location()'),
    # NBT: 26.2's ValueInput/ValueOutput are emulated over CompoundTag by the shim package.
    (r'\bimport net\.minecraft\.world\.level\.storage\.ValueInput;', 'import io.github.mcheliceport.shim.ValueInput;'),
    (r'\bimport net\.minecraft\.world\.level\.storage\.ValueOutput;', 'import io.github.mcheliceport.shim.ValueOutput;'),
    (r'\bnet\.minecraft\.world\.level\.storage\.ValueInput\b', 'io.github.mcheliceport.shim.ValueInput'),
    (r'\bnet\.minecraft\.world\.level\.storage\.ValueOutput\b', 'io.github.mcheliceport.shim.ValueOutput'),
    # Entity
    (r'\.snapTo\(', '.moveTo('),
    (r'(?<![\w.])snapTo\(', 'moveTo('),
    (r'\bimport net\.minecraft\.world\.entity\.EntitySpawnReason;', 'import net.minecraft.world.entity.MobSpawnType;'),
    (r'\bnet\.minecraft\.world\.entity\.EntitySpawnReason\b', 'net.minecraft.world.entity.MobSpawnType'),
    (r'(?<![\w.])EntitySpawnReason\b', 'MobSpawnType'),
    (r'\bimport net\.minecraft\.world\.item\.ItemUseAnimation;', 'import net.minecraft.world.item.UseAnim;'),
    (r'\bnet\.minecraft\.world\.item\.ItemUseAnimation\b', 'net.minecraft.world.item.UseAnim'),
    (r'(?<![\w.])ItemUseAnimation\b', 'UseAnim'),
    # Loader registries: our own DeferredRegister look-alikes.
    (r'\bnet\.neoforged\.neoforge\.registries\.(DeferredRegister|DeferredHolder|DeferredItem|DeferredBlock)\b',
     r'io.github.mcheliceport.platform.registry.\1'),
    # Networking: our loader-neutral look-alikes.
    (r'\bnet\.neoforged\.neoforge\.network\.handling\.IPayloadContext\b', 'io.github.mcheliceport.platform.network.IPayloadContext'),
    (r'\bnet\.neoforged\.neoforge\.network\.registration\.PayloadRegistrar\b', 'io.github.mcheliceport.platform.network.PayloadRegistrar'),
    (r'\bnet\.neoforged\.neoforge\.network\.PacketDistributor\b', 'io.github.mcheliceport.platform.network.PacketDistributor'),
    (r'\bnet\.neoforged\.neoforge\.client\.network\.ClientPacketDistributor\b', 'io.github.mcheliceport.platform.network.ClientPacketDistributor'),
    (r'\bnet\.neoforged\.neoforge\.server\.ServerLifecycleHooks\.getCurrentServer\(\)', 'io.github.mcheliceport.platform.Services.PLATFORM.currentServer()'),
    (r'(?<![\w.])ServerLifecycleHooks\.getCurrentServer\(\)', 'io.github.mcheliceport.platform.Services.PLATFORM.currentServer()'),
    (r'import net\.neoforged\.neoforge\.server\.ServerLifecycleHooks;\n', ''),
    (r'\bnet\.neoforged\.neoforge\.entity\.PartEntity\b', 'io.github.mcheliceport.shim.PartEntity'),
    (r'\bnet\.neoforged\.neoforge\.entity\.IEntityWithComplexSpawn\b', 'io.github.mcheliceport.shim.ComplexSpawn'),
    (r'(?<![\w.])IEntityWithComplexSpawn\b', 'ComplexSpawn'),
    (r'\bnet\.neoforged\.neoforge\.common\.world\.chunk\.TicketController\b', 'io.github.mcheliceport.shim.TicketController'),
    (r'\.getGameProfile\(\)\.name\(\)', '.getGameProfile().getName()'),
    (r'\bisInvulnerableToBase\(', 'isInvulnerableTo('),
    (r'\.getMinY\(\)', '.getMinBuildHeight()'),
    (r'((?:this\.)?\w+(?:\(\))?)\.getMaxY\(\)', r'(\1.getMaxBuildHeight() - 1)'),
    (r'\.getNonEquipmentItems\(\)', '.items'),
    (r'\bnet\.minecraft\.advancements\.triggers\.CriterionTrigger\b', 'net.minecraft.advancements.CriterionTrigger'),
    (r'\bnet\.minecraft\.advancements\.triggers\.', 'net.minecraft.advancements.critereon.'),
    (r'\bnet\.minecraft\.world\.level\.gamerules\.GameRules\b', 'net.minecraft.world.level.GameRules'),
    # 26.2 passes the reason to EntityType#create and a "send event" flag to startRiding.
    (r'\.create\(([^,()]+(?:\(\))?), (?:net\.minecraft\.world\.entity\.)?MobSpawnType\.\w+\)', r'.create(\1)'),
    (r'\bstartRiding\(((?:[^,()\[\]]|\([^()]*\)|\[[^\]]*\])+), (true|false), (?:true|false)\)', r'startRiding(\1, \2)'),
    (r'\bspawnAtLocation\((?:\(ServerLevel\) )?[\w.]+(?:\(\))?, ', 'spawnAtLocation('),
    (r'\bContainerHelper\.(loadAllItems|saveAllItems)\(', r'io.github.mcheliceport.shim.Compat.\1('),
    (r'import net\.minecraft\.world\.level\.portal\.TeleportTransition;', 'import io.github.mcheliceport.shim.TeleportTransition;'),
    (r'\bnet\.minecraft\.world\.level\.portal\.TeleportTransition\b', 'io.github.mcheliceport.shim.TeleportTransition'),
    (r'((?:this\.)?[\w]+(?:\(\))?(?:\.\w+(?:\(\))?)*)\.teleport\(new ', r'io.github.mcheliceport.shim.TeleportTransition.teleport(\1, new '),
    (r'\bCommands\.hasPermission\((Commands\.\w+)\)', r'source$ -> source$.hasPermission(\1)'),
    (r'\.get\((\w+)\)\.map\(h -> h\.value\(\)\)', r'.getOptional(\1)'),
]

SAVE_SIGS = [
    # Entity save hooks take a CompoundTag on 1.21.1; wrap it in the shim so the bodies stay as in 26.2.
    (r'(void|boolean) (readAdditionalSaveData|addAdditionalSaveData|saveAsPassenger|load|saveWithoutId)\((?:final )?Value(Input|Output) (\w+)\)\s*\{',
     lambda m: f'{m.group(1)} {m.group(2)}(net.minecraft.nbt.CompoundTag {m.group(4)}$tag) {{ '
               f'Value{m.group(3)} {m.group(4)} = Value{m.group(3)}.of({m.group(4)}$tag, this.registryAccess());'),
    (r'void (loadAdditional|saveAdditional)\((?:final )?Value(Input|Output) (\w+)\)\s*\{',
     lambda m: f'void {m.group(1)}(net.minecraft.nbt.CompoundTag {m.group(3)}$tag, '
               f'net.minecraft.core.HolderLookup.Provider {m.group(3)}$registries) {{ '
               f'Value{m.group(2)} {m.group(3)} = Value{m.group(2)}.of({m.group(3)}$tag, {m.group(3)}$registries);'),
]


def _hurt(m):
    lvl, src, amt = m.group(1), m.group(2), m.group(3)
    return (f'public boolean hurt(DamageSource {src}, float {amt}) {{ '
            f'if (!(this.level() instanceof ServerLevel {lvl})) {{ return this.hurtClient$26({src}); }}')


OVERRIDES = [
    # 26.2 split Entity#hurt into hurtServer/hurtClient.
    (r'public boolean hurtServer\(ServerLevel (\w+), DamageSource (\w+), float (\w+)\) \{', _hurt),
    (r'@Override(\s+)public boolean hurtClient\(DamageSource (\w+)\)', r'\1public boolean hurtClient$26(DamageSource \2)'),
    (r'(\w+(?:\(\))?)\.hurtServer\(\w+(?:\(\))?, ', r'\1.hurt('),
    (r'(?<![\w.])hurtServer\(\w+(?:\(\))?, ', 'hurt('),
    # Entity#interact lost its hit location after 1.21.1 gained it.
    (r'public InteractionResult interact\(Player (\w+), InteractionHand (\w+), Vec3 (\w+)\) \{',
     r'public InteractionResult interact(Player \1, InteractionHand \2) { Vec3 \3 = \1.position().subtract(this.position());'),
    (r'public boolean canBeCollidedWith\(@Nullable Entity (\w+)\) \{', r'public boolean canBeCollidedWith() { Entity \1 = null;'),
    # Item#use returned InteractionResultHolder before 1.21.2.
    (r'public InteractionResult use\(Level (\w+), Player (\w+), InteractionHand (\w+)\) \{',
     r'public net.minecraft.world.InteractionResultHolder<net.minecraft.world.item.ItemStack> use(Level \1, Player \2, InteractionHand \3) { '
     r'return io.github.mcheliceport.shim.Compat.holder(this.use$26(\1, \2, \3), \2.getItemInHand(\3)); }\n'
     r'    private InteractionResult use$26(Level \1, Player \2, InteractionHand \3) {'),
    # Tooltips: 26.2 passes a consumer and a TooltipDisplay.
    (r'public void appendHoverText\(ItemStack (\w+), (?:Item\.)?TooltipContext (\w+), TooltipDisplay \w+,\s*Consumer<Component> (\w+), TooltipFlag (\w+)\) \{',
     r'public void appendHoverText(ItemStack \1, Item.TooltipContext \2, java.util.List<Component> \3$list, TooltipFlag \4) { java.util.function.Consumer<Component> \3 = \3$list::add;'),
    (r'public void hurtEnemy\(ItemStack (\w+), LivingEntity (\w+), LivingEntity (\w+)\) \{',
     r'public boolean hurtEnemy(ItemStack \1, LivingEntity \2, LivingEntity \3) { this.hurtEnemy$26(\1, \2, \3); return true; }\n'
     r'    private void hurtEnemy$26(ItemStack \1, LivingEntity \2, LivingEntity \3) {'),
    (r'public boolean releaseUsing\(ItemStack (\w+), Level (\w+), LivingEntity (\w+), int (\w+)\) \{',
     r'public void releaseUsing(ItemStack \1, Level \2, LivingEntity \3, int \4) { this.releaseUsing$26(\1, \2, \3, \4); }\n'
     r'    private boolean releaseUsing$26(ItemStack \1, Level \2, LivingEntity \3, int \4) {'),
    # NeoForge extension methods: common code cannot see the interfaces, so no @Override (NeoForge
    # still dispatches to them by signature; Fabric gets mixin hooks where they matter).
    (r'@Override(\s+public boolean (?:onEntitySwing|shouldRiderSit|isFlyingVehicle)\()', r'\1'),
    (r'@Override(\s+public void (?:onLoad|onChunkUnloaded)\(\))', r'\1'),
]


def _super_save(text):
    # super.readAdditionalSaveData(input) where input is the shim wrapper: pass the raw tag on.
    for name in set(re.findall(r'(\w+)\$tag', text)):
        text = re.sub(r'super\.(addAdditionalSaveData|readAdditionalSaveData|saveAsPassenger)\(' + name + r'\)',
                      r'super.\1(' + name + r'$tag)', text)
        text = re.sub(r'super\.(loadAdditional|saveAdditional)\(' + name + r'\)',
                      r'super.\1(' + name + r'$tag, ' + name + r'$registries)', text)
    return text


def convert(text: str) -> str:
    for pattern, repl in RULES:
        text = re.sub(pattern, repl, text)
    for pattern, repl in SAVE_SIGS:
        text = re.sub(pattern, repl, text)
    for pattern, repl in OVERRIDES:
        text = re.sub(pattern, repl, text)
    text = _super_save(text)
    return text


def main(args):
    for arg in args:
        root = Path(arg)
        files = [root] if root.is_file() else sorted(root.rglob('*.java'))
        for f in files:
            if '/shim/' in f.as_posix() or '/platform/' in f.as_posix():
                continue
            old = f.read_text(encoding='utf-8')
            new = convert(old)
            if new != old:
                f.write_text(new, encoding='utf-8')
                print('converted', f)


if __name__ == '__main__':
    main(sys.argv[1:])
