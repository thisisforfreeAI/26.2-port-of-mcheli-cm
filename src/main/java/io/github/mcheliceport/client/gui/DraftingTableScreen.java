package io.github.mcheliceport.client.gui;

import io.github.mcheliceport.client.model.AircraftModels;
import io.github.mcheliceport.client.render.DraftingModelRenderer;
import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.PlaneInfo;
import io.github.mcheliceport.content.aircraft.ShipInfo;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.crafting.DraftingTableRecipes;
import io.github.mcheliceport.crafting.DraftingTableRecipes.Entry;
import io.github.mcheliceport.item.AircraftItem;
import io.github.mcheliceport.menu.DraftingTableMenu;
import io.github.mcheliceport.net.DraftingTableCreatePayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The drafting table's recipe browser (upstream {@code MCH_DraftingTableGui} and
 * {@code MCH_CurrentRecipe}). The main page shows one recipe, its description pages and, for a
 * vehicle, its 3D model and its specifications; the list page shows six recipes at a time. Create
 * asks the server to make the recipe into the output slot.
 *
 * <p>On the main page, dragging in the right half of the window turns (left button), zooms (right
 * button) or moves (middle button) the model, as upstream.
 */
public class DraftingTableScreen extends AbstractContainerScreen<DraftingTableMenu> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("mcheli", "textures/gui/drafting_table.png");
    private static final int SCREEN_MAIN = 0;
    private static final int SCREEN_LIST = 1;
    /** Upstream's brown text on the specification page. */
    private static final int INFO_COLOR = -9491968;
    /** Upstream: description pages {@code drafting_table_desc/<item>#<n>.png}, at most 20. */
    private static final int MAX_DESC_PAGES = 20;
    /** Upstream: the model grows by 20 faces a frame. */
    private static final int FACES_PER_FRAME = 20;

    /** Upstream keeps the model's view in static fields, so it lasts while the game runs. */
    private static float modelZoom = 1.0F;
    private static float modelRotX;
    private static float modelRotY;
    private static float modelPosX;
    private static float modelPosY;

    private final List<List<AbstractWidget>> screenWidgets = new ArrayList<>();
    private final List<Button> listButtons = new ArrayList<>();
    private final Button[] selectButtons = new Button[6];
    private int screenId;
    private List<Entry> currentList = List.of();
    private @Nullable Current current;
    private int listRecipeCount = -1;
    private int buttonClickWait;
    private EditBox searchField;
    private VerticalSlider listSlider;
    private Button buttonCreate;
    private Button buttonNext;
    private Button buttonPrev;
    private Button buttonNextPage;
    private Button buttonPrevPage;
    private int drawFace;
    /** The mouse buttons held down, for the model help's highlights. */
    private int heldButtons;

    public DraftingTableScreen(DraftingTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 400, 240);
    }

    @Override
    protected void init() {
        super.init();
        this.screenWidgets.clear();
        this.screenWidgets.add(new ArrayList<>());
        this.screenWidgets.add(new ArrayList<>());
        this.listButtons.clear();
        List<AbstractWidget> main = this.screenWidgets.get(SCREEN_MAIN);
        String[] names = {"Helicopter List", "Plane List", "Vehicle List", "Tank List", "Item List", "Ship List"};
        int[] lists = {DraftingTableRecipes.HELICOPTER, DraftingTableRecipes.PLANE, DraftingTableRecipes.VEHICLE,
                DraftingTableRecipes.TANK, DraftingTableRecipes.ITEM, DraftingTableRecipes.SHIP};
        for (int i = 0; i < names.length; i++) {
            int list = lists[i];
            Button b = this.button(names[i], 20, 20 + i * 20, 90, () -> {
                if (list != DraftingTableRecipes.ITEM) {
                    boolean side = list == DraftingTableRecipes.PLANE || list == DraftingTableRecipes.SHIP;
                    initModelTransform(side ? 90.0F : 180.0F, side ? 180.0F : 90.0F);
                }
                this.switchRecipeList(this.list(list));
                this.switchScreen(SCREEN_LIST);
            });
            this.listButtons.add(b);
            main.add(b);
        }
        this.buttonCreate = this.button("Create", 120, 89, 50, this::create);
        this.buttonPrev = this.button("<<", 120, 111, 36, () -> this.step(-1));
        this.buttonNext = this.button(">>", 155, 111, 35, () -> this.step(1));
        this.buttonPrevPage = this.button("Prev Page", 210, 210, 60, () -> {
            if (this.current != null) {
                this.current.switchPrevPage();
            }
        });
        this.buttonNextPage = this.button("Next Page", 270, 210, 60, () -> {
            if (this.current != null) {
                this.current.switchNextPage();
            }
        });
        main.add(this.buttonCreate);
        main.add(this.buttonPrev);
        main.add(this.buttonNext);
        main.add(this.buttonPrevPage);
        main.add(this.buttonNextPage);
        List<AbstractWidget> listScreen = this.screenWidgets.get(SCREEN_LIST);
        for (int i = 0; i < 6; i++) {
            int slot = i;
            this.selectButtons[i] = this.button("Select", 30 + (i % 2) * 140, 40 + (i / 2) * 70, 45, () -> {
                int index = this.listSlider.value * 2 + slot;
                if (index < this.currentList.size()) {
                    this.setCurrentRecipe(new Current(this.currentList, index));
                    this.switchScreen(SCREEN_MAIN);
                }
            });
            listScreen.add(this.selectButtons[i]);
        }
        this.listSlider = this.addRenderableWidget(new VerticalSlider(this.leftPos + 360, this.topPos + 20, 20, 200));
        listScreen.add(this.listSlider);
        this.searchField = new EditBox(this.font, this.leftPos + 10, this.topPos + 5, 100, 12, Component.literal("Search"));
        this.searchField.setMaxLength(50);
        this.searchField.setResponder(text -> this.applySearch(text.trim()));
        this.addRenderableWidget(this.searchField);
        this.currentList = List.of();
        this.current = null;
        this.listRecipeCount = -1;
        this.drawFace = 0;
        this.switchScreen(SCREEN_MAIN);
        initModelTransform(180.0F, 90.0F);
        this.refreshRecipes();
    }

    /** Upstream {@code initModelTransform} and the angles each list sets after it. */
    private static void initModelTransform(float rotX, float rotY) {
        modelRotX = rotX;
        modelRotY = rotY;
        modelPosX = 0.0F;
        modelPosY = 0.0F;
        modelZoom = 1.0F;
    }

    private Button button(String text, int x, int y, int width, Runnable action) {
        return this.addRenderableWidget(Button.builder(Component.literal(text), b -> {
            if (this.buttonClickWait <= 0) {
                this.buttonClickWait = 3;
                action.run();
            }
        }).bounds(this.leftPos + x, this.topPos + y, width, 20).build());
    }

    /** The recipes arrive from the server after the screen opens. */
    private void refreshRecipes() {
        int count = this.menu.recipes().size();
        if (count == this.listRecipeCount) {
            return;
        }
        this.listRecipeCount = count;
        int[] lists = {DraftingTableRecipes.HELICOPTER, DraftingTableRecipes.PLANE, DraftingTableRecipes.VEHICLE,
                DraftingTableRecipes.TANK, DraftingTableRecipes.ITEM, DraftingTableRecipes.SHIP};
        for (int i = 0; i < this.listButtons.size(); i++) {
            this.listButtons.get(i).active = !this.list(lists[i]).isEmpty();
        }
        this.currentList = List.of();
        this.switchRecipeList(this.defaultRecipeList());
    }

    private List<Entry> list(int list) {
        return this.menu.recipes().stream().filter(e -> e.list() == list).toList();
    }

    /** Upstream {@code allRecipeLists}: items first, then each kind of vehicle. */
    private List<List<Entry>> allRecipeLists() {
        return List.of(this.list(DraftingTableRecipes.ITEM), this.list(DraftingTableRecipes.HELICOPTER),
                this.list(DraftingTableRecipes.PLANE), this.list(DraftingTableRecipes.VEHICLE),
                this.list(DraftingTableRecipes.TANK), this.list(DraftingTableRecipes.SHIP));
    }

    private List<Entry> defaultRecipeList() {
        for (List<Entry> list : this.allRecipeLists()) {
            if (!list.isEmpty()) {
                return list;
            }
        }
        return List.of();
    }

    private void applySearch(String text) {
        if (text.isEmpty()) {
            this.switchRecipeList(this.defaultRecipeList());
            return;
        }
        String needle = text.toLowerCase(Locale.ROOT);
        List<Entry> filtered = new ArrayList<>();
        for (List<Entry> list : this.allRecipeLists()) {
            for (Entry entry : list) {
                if (entry.result().getHoverName().getString().toLowerCase(Locale.ROOT).contains(needle)) {
                    filtered.add(entry);
                }
            }
        }
        if (!filtered.isEmpty()) {
            this.switchRecipeList(filtered);
            this.switchScreen(SCREEN_LIST);
        }
    }

    private void switchRecipeList(List<Entry> list) {
        if (!this.currentList.equals(list) || this.current == null) {
            this.setCurrentRecipe(new Current(list, 0));
            this.currentList = list;
            int rows = (list.size() + 1) / 2;
            this.listSlider.max = Math.max(0, rows - 3);
            this.listSlider.value = 0;
        } else {
            this.listSlider.setValue(this.current.index / 2);
        }
    }

    private void switchScreen(int id) {
        this.screenId = id;
        for (List<AbstractWidget> widgets : this.screenWidgets) {
            for (AbstractWidget w : widgets) {
                w.visible = false;
            }
        }
        for (AbstractWidget w : this.screenWidgets.get(id)) {
            w.visible = true;
        }
        this.menu.showSlots(id == SCREEN_MAIN);
        this.updateVisibility();
    }

    private void updateVisibility() {
        boolean pages = this.screenId == SCREEN_MAIN && this.current != null && this.current.descMaxPage > 1;
        this.buttonNextPage.visible = pages;
        this.buttonPrevPage.visible = pages;
        if (this.screenId == SCREEN_LIST) {
            for (int i = 0; i < 6; i++) {
                this.selectButtons[i].visible = this.listSlider.value * 2 + i < this.currentList.size();
            }
        }
    }

    /** Upstream: a new recipe recentres the model, and a different item draws it again from the start. */
    private void setCurrentRecipe(Current recipe) {
        modelPosX = 0.0F;
        modelPosY = 0.0F;
        if (this.current == null || this.current.entry == null || recipe.entry == null
                || !ItemStack.isSameItemSameComponents(this.current.entry.result(), recipe.entry.result())) {
            this.drawFace = 0;
        }
        this.current = recipe;
        this.updateVisibility();
        this.updateEnableCreateButton();
    }

    /** Upstream {@code BUTTON_NEXT}/{@code BUTTON_PREV}: the next recipe, keeping the page unless it was a picture. */
    private void step(int delta) {
        if (this.current == null || this.currentList.isEmpty()) {
            return;
        }
        int page = this.current.isCurrentPageTexture() ? 0 : this.current.descPage;
        int index = Math.floorMod(this.current.index + delta, this.currentList.size());
        this.setCurrentRecipe(new Current(this.currentList, index));
        this.current.setDescCurrentPage(page);
        this.updateVisibility();
    }

    private void create() {
        if (this.current != null && this.current.entry != null) {
            ClientPacketDistributor.sendToServer(new DraftingTableCreatePayload(this.menu.containerId,
                    this.current.entry.id()));
        }
    }

    private void updateEnableCreateButton() {
        this.buttonCreate.active = false;
        if (this.current == null || this.current.entry == null || this.minecraft.player == null) {
            return;
        }
        if (!this.menu.hasOutput()) {
            this.buttonCreate.active = DraftingTableRecipes.canCraft(this.minecraft.player.getInventory(),
                    this.current.entry);
        }
        if (this.minecraft.player.getAbilities().instabuild) {
            this.buttonCreate.active = true;
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (this.buttonClickWait > 0) {
            this.buttonClickWait--;
        }
        this.refreshRecipes();
        this.updateEnableCreateButton();
        this.updateVisibility();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (this.searchField.isFocused() && key != GLFW.GLFW_KEY_ESCAPE) {
            this.searchField.keyPressed(event);
            return true;
        }
        boolean inventoryKey = this.minecraft.options.keyInventory.matches(event);
        if (key == GLFW.GLFW_KEY_ESCAPE || inventoryKey) {
            if (this.screenId == SCREEN_MAIN) {
                this.onClose();
            } else {
                this.switchScreen(SCREEN_MAIN);
            }
            return true;
        }
        if (this.screenId == SCREEN_MAIN) {
            if (key == GLFW.GLFW_KEY_RIGHT) {
                this.step(1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_LEFT) {
                this.step(-1);
                return true;
            }
        } else {
            if (key == GLFW.GLFW_KEY_UP) {
                this.listSlider.setValue(this.listSlider.value - 1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_DOWN) {
                this.listSlider.setValue(this.listSlider.value + 1);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    /** Upstream: the wheel scrolls the list, or steps through recipes on the main page. */
    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (scrollY != 0.0) {
            if (this.screenId == SCREEN_LIST) {
                this.listSlider.setValue(this.listSlider.value + (scrollY > 0 ? -1 : 1));
            } else {
                this.step(scrollY > 0 ? -1 : 1);
            }
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        this.heldButtons |= 1 << event.button();
        if (!this.searchField.isMouseOver(event.x(), event.y())) {
            this.searchField.setFocused(false);
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.heldButtons &= ~(1 << event.button());
        return super.mouseReleased(event);
    }

    /**
     * Upstream {@code handleMouseInput}: on the main page, a drag in the right half of the window
     * turns, moves or zooms the model, whichever page shows. Upstream counted the drag in window
     * pixels with Y up, so the GUI's distance is scaled back to that.
     */
    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.screenId == SCREEN_MAIN && event.x() > this.width / 2.0) {
            double scale = this.minecraft.getWindow().getGuiScale();
            double rawX = dx * scale;
            double rawY = -dy * scale;
            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && (rawX != 0.0 || rawY != 0.0)) {
                modelRotX = wrap((float) (modelRotX - rawY / 2.0));
                modelRotY = wrap((float) (modelRotY - rawX / 2.0));
            }
            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && (rawX != 0.0 || rawY != 0.0)) {
                // Upstream clamped the angles to +-1000 here rather than the position, a no-op.
                modelPosX = (float) (modelPosX + rawX / 2.0);
                modelPosY = (float) (modelPosY - rawY / 2.0);
            }
            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && rawY != 0.0) {
                modelZoom = Mth.clamp((float) (modelZoom + rawY / 100.0), 0.1F, 10.0F);
            }
        }
        return super.mouseDragged(event, dx, dy);
    }

    /** Upstream: past +-360 degrees the angle comes back by one turn. */
    private static float wrap(float angle) {
        if (angle > 360.0F) {
            return angle - 360.0F;
        }
        return angle < -360.0F ? angle + 360.0F : angle;
    }

    /** Upstream {@code drawScreen}: the model goes over the rest of the screen. */
    @Override
    public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractContents(g, mouseX, mouseY, partialTick);
        if (this.screenId == SCREEN_MAIN && this.current != null && this.current.isCurrentPageModel()) {
            AircraftInfo info = this.current.acInfo;
            AircraftModels.VehicleModels models = AircraftModels.get(info.getDirectoryName(), info);
            if (models != null) {
                g.submitPictureInPictureRenderState(new DraftingModelRenderer.State(models.body(),
                        this.current.modelTexture, this.current.centreXZ, modelRotX, modelRotY, modelPosX, modelPosY,
                        modelZoom, this.drawFace, this.leftPos + 300, this.topPos + 110, 0, 0, this.width,
                        this.height, g.peekScissorStack()));
                if (this.drawFace < 10000000) {
                    this.drawFace += FACES_PER_FRAME;
                }
            }
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos, this.topPos, 0.0F,
                this.screenId == SCREEN_MAIN ? 0.0F : this.imageHeight, this.imageWidth, this.imageHeight, 512, 512);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int mx = mouseX - this.leftPos;
        int my = mouseY - this.topPos;
        if (this.screenId == SCREEN_MAIN && this.current != null) {
            Current c = this.current;
            Identifier page = c.getCurrentPageTexture();
            if (page != null) {
                g.blit(RenderPipelines.GUI_TEXTURED, page, 210, 20, 0.0F, 0.0F, 170, 190, 340, 380, 512, 512);
            } else if (c.isCurrentPageAcInfo()) {
                for (int i = 0; i < c.infoItem.size(); i++) {
                    g.text(this.font, c.infoItem.get(i), 210, 40 + 10 * i, INFO_COLOR, false);
                    String data = c.infoData.get(i);
                    if (!data.isEmpty()) {
                        g.text(this.font, data, 280, 40 + 10 * i, INFO_COLOR, false);
                    }
                }
            } else {
                g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, 340, 215, 400.0F, 60.0F, 45, 15, 90, 30, 512, 512);
                if (mx >= 350 && mx <= 400 && my >= 214 && my <= 230) {
                    g.setComponentTooltipForNextFrame(this.font, List.of(
                            this.helpLine(GLFW.GLFW_MOUSE_BUTTON_LEFT, "Mouse left button drag : Rotation model"),
                            this.helpLine(GLFW.GLFW_MOUSE_BUTTON_RIGHT, "Mouse right button drag : Zoom model"),
                            this.helpLine(GLFW.GLFW_MOUSE_BUTTON_MIDDLE, "Mouse middle button drag : Move model")),
                            mouseX - 30, mouseY);
                }
            }
            g.text(this.font, c.displayName, 120, 20, -1, true);
            this.drawItemRecipe(g, c.entry, 121, 34, mx, my);
        }
        if (this.screenId == SCREEN_LIST) {
            int index = 2 * this.listSlider.value;
            for (int r = 0; r < 3; r++) {
                for (int col = 0; col < 2; col++) {
                    int i = index + r * 2 + col;
                    if (i < this.currentList.size()) {
                        Entry entry = this.currentList.get(i);
                        g.centeredText(this.font, entry.result().getHoverName(), 110 + 140 * col, 20 + 70 * r, -1);
                        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, 80 + 140 * col - 1, 30 + 70 * r - 1, 400.0F, 0.0F,
                                75, 54, 512, 512);
                        this.drawItemRecipe(g, entry, 80 + 140 * col, 30 + 70 * r, mx, my);
                    }
                }
            }
        }
    }

    /** A line of the model help, aqua while its button is held. */
    private Component helpLine(int button, String text) {
        Component line = Component.literal(text);
        return (this.heldButtons & 1 << button) != 0 ? line.copy().withStyle(ChatFormatting.AQUA) : line;
    }

    /** Upstream {@code drawItemRecipe}: the grid, then the result beside it; hovering shows the item. */
    private void drawItemRecipe(GuiGraphicsExtractor g, @Nullable Entry entry, int x, int y, int mx, int my) {
        if (entry == null || entry.result().isEmpty()) {
            return;
        }
        List<Optional<Ingredient>> grid = entry.grid();
        for (int i = 0; i < grid.size(); i++) {
            ItemStack stack = grid.get(i).map(this::shown).orElse(ItemStack.EMPTY);
            this.drawStack(g, stack, x + i % 3 * 18, y + i / 3 * 18, mx, my);
        }
        this.drawStack(g, entry.result(), x + 54 + 3, y + 18, mx, my);
    }

    private void drawStack(GuiGraphicsExtractor g, ItemStack stack, int x, int y, int mx, int my) {
        if (stack.isEmpty()) {
            return;
        }
        g.item(stack, x, y);
        g.itemDecorations(this.font, stack, x, y);
        if (mx >= x && mx < x + 16 && my >= y && my < y + 16 && this.menu.getCarried().isEmpty()) {
            g.setTooltipForNextFrame(this.font, stack, mx + this.leftPos, my + this.topPos);
        }
    }

    /** An ingredient that accepts several items shows each in turn, a second apiece. */
    private ItemStack shown(Ingredient ingredient) {
        List<Holder<Item>> items = ingredient.items().toList();
        if (items.isEmpty()) {
            return ItemStack.EMPTY;
        }
        long second = this.minecraft.level != null ? this.minecraft.level.getGameTime() / 20 : 0;
        return new ItemStack(items.get((int) (second % items.size())));
    }

    /** Upstream {@code MCH_CurrentRecipe}: one recipe and its pages. */
    private final class Current {

        final @Nullable Entry entry;
        final int index;
        final String displayName;
        final List<Identifier> descTexture;
        final List<String> infoItem = new ArrayList<>();
        final List<String> infoData = new ArrayList<>();
        final @Nullable AircraftInfo acInfo;
        /** Whether the vehicle has a model file, so a model page (upstream: the model had loaded). */
        final boolean hasModel;
        /** Upstream {@code modelRot == 0}: planes and ships centre on X and Z. */
        final boolean centreXZ;
        final @Nullable Identifier modelTexture;
        int descMaxPage;
        int descPage;

        Current(List<Entry> list, int index) {
            this.entry = list.isEmpty() ? null : list.get(Mth.clamp(index, 0, list.size() - 1));
            this.index = index;
            this.displayName = this.entry != null ? this.entry.result().getHoverName().getString() : "None";
            this.descTexture = descTextures(this.entry);
            this.descMaxPage = this.descTexture.size();
            this.acInfo = this.entry != null && this.entry.result().getItem() instanceof AircraftItem aircraft
                    ? aircraft.getAircraftInfo() : null;
            boolean model = false;
            if (this.acInfo != null) {
                this.descMaxPage++;
                model = hasModelFile(this.acInfo);
                if (model) {
                    this.descMaxPage++;
                }
                this.addInfoText(this.acInfo);
            }
            this.hasModel = model;
            this.centreXZ = this.acInfo instanceof PlaneInfo || this.acInfo instanceof ShipInfo;
            this.modelTexture = model ? Identifier.fromNamespaceAndPath("mcheli",
                    "textures/" + this.acInfo.getDirectoryName() + "/" + this.acInfo.name + ".png") : null;
        }

        /** The body model {@link AircraftModels#loadModel} would read: an .obj, else an .mqo. */
        private static boolean hasModelFile(AircraftInfo info) {
            var resources = net.minecraft.client.Minecraft.getInstance().getResourceManager();
            String path = "models/" + (info.getDirectoryName() + "/" + info.name).toLowerCase(Locale.ROOT);
            for (String ext : new String[] {".obj", ".mqo"}) {
                Identifier id = Identifier.tryBuild("mcheli", path + ext);
                if (id != null && resources.getResource(id).isPresent()) {
                    return true;
                }
            }
            return false;
        }

        private void addInfoText(AircraftInfo info) {
            this.info("Name", this.displayName);
            this.info("HP", "" + info.maxHp);
            this.info("Num of Seat", "" + (!info.isUAV ? info.getNumSeat() : info.getNumSeat() - 1));
            this.info("GunnerMode", info.isEnableGunnerMode ? "YES" : "NO");
            this.info("NightVision", info.isEnableNightVision ? "YES" : "NO");
            this.info("Radar", info.isEnableEntityRadar ? "YES" : "NO");
            this.info("Inventory", "" + info.inventorySize);
            if (info instanceof PlaneInfo plane) {
                this.info("VTOL", plane.isEnableVtol ? "YES" : "NO");
            }
            if (info instanceof ShipInfo ship) {
                this.info("Submersible", ship.isEnableVtol ? "YES" : "NO");
            }
            if (info.getWeaponCount() > 0) {
                this.infoItem.add("Armed----------------");
                this.infoData.add("");
                for (int i = 0; i < info.getWeaponCount(); i++) {
                    AircraftInfo.WeaponSet set = info.getWeaponSetById(i);
                    WeaponInfo weapon = set != null ? ContentRegistries.weapon().get(set.type) : null;
                    if (weapon != null) {
                        this.info(weapon.getWeaponTypeName(), weapon.displayName);
                    } else {
                        this.info("ERROR", "Not found weapon " + (i + 1));
                    }
                }
            }
        }

        private void info(String item, String data) {
            this.infoItem.add(item + " :");
            this.infoData.add(data);
        }

        void switchNextPage() {
            this.descPage = this.descMaxPage >= 2 ? (this.descPage + 1) % this.descMaxPage : 0;
        }

        /** Upstream's: from the first page back to the last, but from any later page to the first. */
        void switchPrevPage() {
            this.descPage--;
            if (this.descPage < 0 && this.descMaxPage >= 2) {
                this.descPage = this.descMaxPage - 1;
            } else {
                this.descPage = 0;
            }
        }

        void setDescCurrentPage(int page) {
            this.descPage = this.descMaxPage > 0 ? Math.min(page, this.descMaxPage - 1) : 0;
        }

        @Nullable Identifier getCurrentPageTexture() {
            return this.descPage >= 0 && this.descPage < this.descTexture.size() ? this.descTexture.get(this.descPage)
                    : null;
        }

        boolean isCurrentPageTexture() {
            return this.getCurrentPageTexture() != null;
        }

        boolean isCurrentPageModel() {
            return this.acInfo != null && this.hasModel && this.descPage == this.descTexture.size();
        }

        boolean isCurrentPageAcInfo() {
            return this.acInfo != null && this.descPage == this.descMaxPage - 1;
        }

        /**
         * Upstream's {@code drafting_table_desc/<item>#<n>.png}; '#' cannot be in a 26.2 resource
         * path, so the port's pages are {@code drafting_table_desc/<item>/<n>.png}.
         */
        private List<Identifier> descTextures(@Nullable Entry entry) {
            List<Identifier> list = new ArrayList<>();
            if (entry == null) {
                return list;
            }
            String name = BuiltInRegistries.ITEM.getKey(entry.result().getItem()).getPath();
            if (entry.result().getItem() instanceof AircraftItem aircraft) {
                name = aircraft.getContentName().toLowerCase(Locale.ROOT);
            }
            var resources = DraftingTableScreen.this.minecraft.getResourceManager();
            for (int i = 0; i < MAX_DESC_PAGES; i++) {
                Identifier id = Identifier.tryBuild("mcheli", "textures/drafting_table_desc/" + name + "/" + i + ".png");
                if (id != null && resources.getResource(id).isPresent()) {
                    list.add(id);
                }
            }
            return list;
        }
    }

    /** Upstream {@code MCH_GuiSliderVertical}: the list's scroll bar, one step per row of two. */
    private static final class VerticalSlider extends AbstractWidget {

        int value;
        int max;

        VerticalSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty());
        }

        void setValue(int value) {
            this.value = Mth.clamp(value, 0, this.max);
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            g.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0xFF000000);
            g.fill(this.getX() + 1, this.getY() + 1, this.getX() + this.width - 1, this.getY() + this.height - 1,
                    0xFF555555);
            int thumb = 20;
            int top = this.getY() + (this.max > 0 ? (this.height - thumb) * this.value / this.max : 0);
            int color = this.isHovered() ? 0xFFFFFFFF : 0xFFC6C6C6;
            g.fill(this.getX(), top, this.getX() + this.width, top + thumb, 0xFF000000);
            g.fill(this.getX() + 1, top + 1, this.getX() + this.width - 1, top + thumb - 1, color);
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            this.setFromMouse(event.y());
        }

        @Override
        protected void onDrag(MouseButtonEvent event, double dx, double dy) {
            this.setFromMouse(event.y());
        }

        private void setFromMouse(double mouseY) {
            double f = (mouseY - (this.getY() + 10)) / (double) (this.height - 20);
            this.setValue((int) Math.round(Mth.clamp(f, 0.0, 1.0) * this.max));
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
        }
    }
}
