package io.github.mcheliceport.client.gui;

import io.github.mcheliceport.net.MarkerUpdatePayload;
import io.github.mcheliceport.net.OpenMarkerScreenPayload;
import io.github.mcheliceport.wingman.block.MarkerType;
import io.github.mcheliceport.wingman.block.WingmanMarkerBlock;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Upstream's {@code GuiMarkerConfig}, opened by right-clicking a wingman marker: two rows of type
 * buttons with a line on what the selected type is for, the marker ID (a base's name), the parent
 * base with buttons for the bases nearby, the parking heading for a parking spot, and Save or
 * Cancel. The selected type is underlined and the selected heading outlined in orange.
 *
 * <p>The port's labels are English, an automatic ID follows the type (a parking spot {@code p_1}
 * turned helipad becomes {@code hp_1}), and the screen warns of an ID another marker has or a
 * parent base that is not nearby. Everything is laid out from the top down, so nothing overlaps.
 */
public class MarkerConfigScreen extends Screen {

    private static final MarkerType[] TYPE_ORDER = {
            MarkerType.BASE, MarkerType.RUNWAY_A, MarkerType.RUNWAY_B, MarkerType.PARKING,
            MarkerType.WAYPOINT, MarkerType.HELIPAD, MarkerType.HELIPAD_B};
    /** Upstream's headings: any, north, east, south, west. */
    private static final String[] HEADINGS = {"Any", "N ↑", "E →", "S ↓", "W ←"};
    private static final int WIDTH = 330;
    private static final int MAX_BASE_BUTTONS = 4;
    private static final int FIELD_W = 150;

    private final BlockPos pos;
    private final List<String> nearbyBases;
    private final List<String> otherIds;
    private MarkerType selectedType;
    private int parkingHeading;
    private String idText;
    private String baseText;
    private boolean baseFocused;
    private final Button[] typeButtons = new Button[TYPE_ORDER.length];
    private final Button[] headingButtons = new Button[HEADINGS.length];
    private final List<Button> baseButtons = new ArrayList<>();
    private EditBox idField;
    private EditBox baseIdField;
    /** Where the labels go, worked out with the widgets so the two always line up. */
    private int top;
    private int descY;
    private int idLabelY;
    private int idNoteY;
    private int baseLabelY;
    private int baseNoteY;
    private int headingLabelY;

    public MarkerConfigScreen(OpenMarkerScreenPayload marker) {
        super(Component.literal("Wingman Marker"));
        this.pos = marker.pos();
        this.nearbyBases = marker.nearbyBases();
        this.otherIds = marker.otherIds();
        this.selectedType = marker.markerType();
        this.parkingHeading = Math.clamp(marker.parkingHeading(), -1, 3);
        this.idText = marker.id();
        this.baseText = marker.baseId();
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int left = cx - WIDTH / 2;
        boolean base = this.selectedType == MarkerType.BASE;
        boolean parking = this.selectedType == MarkerType.PARKING;
        boolean picker = !base && !this.nearbyBases.isEmpty();
        int height = 44 + 24 + 30 + (base ? 0 : 30) + (picker ? 18 : 0) + (parking ? 30 : 0) + 22;
        this.top = Math.max(20, (this.height - height) / 2);
        int y = this.top;

        int btnW = 78;
        int gap = 4;
        int row1 = 4;
        int row1X = cx - (btnW * row1 + gap * (row1 - 1)) / 2;
        int row2 = TYPE_ORDER.length - row1;
        int row2X = cx - (btnW * row2 + gap * (row2 - 1)) / 2;
        for (int i = 0; i < TYPE_ORDER.length; i++) {
            MarkerType type = TYPE_ORDER[i];
            int x = i < row1 ? row1X + i * (btnW + gap) : row2X + (i - row1) * (btnW + gap);
            this.typeButtons[i] = this.addRenderableWidget(Button.builder(Component.literal(type.shortName()),
                    b -> this.selectType(type)).bounds(x, i < row1 ? y : y + 22, btnW, 20).build());
        }
        y += 44;
        this.descY = y;
        y += 24;

        // Each field has its label above and its note to the right.
        this.idLabelY = y;
        y += 10;
        this.idField = this.field(left, y, this.idText, "Marker ID");
        this.idNoteY = y;
        y += 20;

        this.baseButtons.clear();
        this.baseLabelY = y;
        this.baseIdField = this.field(left, y + 10, this.baseText, "Parent Base");
        this.baseIdField.visible = !base;
        this.baseNoteY = y + 10;
        if (!base) {
            y += 30;
            if (picker) {
                int shown = Math.min(this.nearbyBases.size(), MAX_BASE_BUTTONS);
                int bw = (WIDTH - gap * (MAX_BASE_BUTTONS - 1)) / MAX_BASE_BUTTONS;
                for (int i = 0; i < shown; i++) {
                    String id = this.nearbyBases.get(i);
                    this.baseButtons.add(this.addRenderableWidget(Button.builder(Component.literal(shorten(id)), b -> {
                        this.baseIdField.setValue(id);
                        this.setFocused(this.baseIdField);
                    }).bounds(left + i * (bw + gap), y, bw, 16).build()));
                }
                y += 18;
            }
        }

        if (parking) {
            this.headingLabelY = y;
            y += 10;
            int hdgW = 44;
            int hdgGap = 4;
            for (int i = 0; i < HEADINGS.length; i++) {
                int heading = i - 1;
                this.headingButtons[i] = this.addRenderableWidget(Button.builder(Component.literal(HEADINGS[i]),
                        b -> this.parkingHeading = heading).bounds(left + i * (hdgW + hdgGap), y, hdgW, 16).build());
            }
            y += 20;
        }
        this.addRenderableWidget(Button.builder(Component.literal("Save"), b -> {
            this.save();
            this.onClose();
        }).bounds(cx - 54, y + 2, 50, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> this.onClose())
                .bounds(cx + 4, y + 2, 50, 20).build());
    }

    private EditBox field(int x, int y, String text, String name) {
        EditBox field = new EditBox(this.font, x, y, FIELD_W, 18, Component.literal(name));
        field.setMaxLength(64);
        field.setValue(text);
        return this.addRenderableWidget(field);
    }

    private static String shorten(String label) {
        return label.length() > 11 ? label.substring(0, 10) + "…" : label;
    }

    @Override
    protected void setInitialFocus() {
        this.setInitialFocus(this.baseFocused && this.baseIdField.visible ? this.baseIdField : this.idField);
    }

    /**
     * Upstream {@code actionPerformed} for a type button: a base has no parent base, and only a
     * parking spot keeps a heading. The screen is laid out again for the type's rows; unlike
     * upstream, what was typed is kept, and an automatic ID of the old type becomes a free one of
     * the new type.
     */
    private void selectType(MarkerType type) {
        String id = this.idField.getValue().trim();
        if (type != MarkerType.BASE && (id.isEmpty() || WingmanMarkerBlock.isAutoId(id)
                && !id.startsWith(type.idPrefix() + "_"))) {
            id = WingmanMarkerBlock.nextAutoId(type, this.otherIds);
        }
        this.idText = id;
        this.baseText = type == MarkerType.BASE ? "" : this.baseIdField.getValue();
        this.baseFocused = false;
        this.selectedType = type;
        if (type != MarkerType.PARKING) {
            this.parkingHeading = -1;
        }
        this.rebuildWidgets();
    }

    /** Upstream's Tab: the focus moves between the two fields. */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_TAB) {
            boolean toBase = !this.baseIdField.isFocused() && this.baseIdField.visible;
            this.setFocused(toBase ? this.baseIdField : this.idField);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int cx = this.width / 2;
        int left = cx - WIDTH / 2;
        if (this.selectedType == MarkerType.PARKING) {
            Button b = this.headingButtons[this.parkingHeading + 1];
            graphics.fill(b.getX() - 1, b.getY() - 1, b.getX() + b.getWidth() + 1, b.getY() + b.getHeight() + 1,
                    0xAAFFAA00);
        }
        String baseValue = this.baseIdField.getValue().trim();
        for (int i = 0; i < this.baseButtons.size(); i++) {
            if (this.nearbyBases.get(i).equals(baseValue)) {
                Button b = this.baseButtons.get(i);
                graphics.fill(b.getX() - 1, b.getY() - 1, b.getX() + b.getWidth() + 1, b.getY() + b.getHeight() + 1,
                        0xAAFFAA00);
            }
        }
        super.extractRenderState(graphics, mouseX, mouseY, a);
        graphics.centeredText(this.font, "Wingman Marker", cx, this.top - 12, 0xFFFFFFFF);
        for (int i = 0; i < TYPE_ORDER.length; i++) {
            if (TYPE_ORDER[i] == this.selectedType) {
                Button b = this.typeButtons[i];
                graphics.fill(b.getX(), b.getY() + b.getHeight(), b.getX() + b.getWidth(), b.getY() + b.getHeight() + 2,
                        0xFFFFAA00);
            }
        }
        List<FormattedCharSequence> desc = this.font.split(Component.literal(this.selectedType.displayName() + " §7"
                + this.selectedType.description()), WIDTH);
        for (int i = 0; i < desc.size() && i < 2; i++) {
            graphics.text(this.font, desc.get(i), left, this.descY + i * 10, 0xFFFFFFFF, true);
        }

        boolean base = this.selectedType == MarkerType.BASE;
        graphics.text(this.font, base ? "Base name:" : "Marker ID:", left, this.idLabelY, 0xFFAAAAAA);
        this.note(graphics, this.idNote(), left, this.idNoteY);
        if (!base) {
            graphics.text(this.font, this.nearbyBases.isEmpty() ? "Parent base:"
                    : "Parent base §7(type it, or pick a nearby base):", left, this.baseLabelY, 0xFFAAAAAA);
            this.note(graphics, this.baseNote(baseValue), left, this.baseNoteY);
        }
        if (this.selectedType == MarkerType.PARKING) {
            graphics.text(this.font, "Parking heading §7(the way a parked aircraft faces):", left,
                    this.headingLabelY, 0xFFAAAAAA);
        }
    }

    /** A note of up to two lines to the right of a field. */
    private void note(GuiGraphicsExtractor graphics, String text, int left, int y) {
        List<FormattedCharSequence> lines = this.font.split(Component.literal(text), WIDTH - FIELD_W - 6);
        int top = lines.size() > 1 ? y : y + 5;
        for (int i = 0; i < lines.size() && i < 2; i++) {
            graphics.text(this.font, lines.get(i), left + FIELD_W + 6, top + i * 10, 0xFFFFFFFF, true);
        }
    }

    private String idNote() {
        String id = this.idField.getValue().trim();
        if (id.isEmpty()) {
            return this.selectedType == MarkerType.BASE ? "§cA base needs a name."
                    : "§7Empty: an automatic ID is given.";
        }
        if (this.otherIds.contains(id)) {
            return "§cAnother marker already has this ID.";
        }
        return this.selectedType == MarkerType.BASE ? "§7Its markers name it as their base."
                : "§7Routes use this ID.";
    }

    private String baseNote(String baseValue) {
        if (baseValue.isEmpty()) {
            return this.nearbyBases.isEmpty()
                    ? "§eNo base nearby. Make a marker a Base first."
                    : "§eNo parent base: in no base's routes.";
        }
        if (!this.nearbyBases.contains(baseValue)) {
            return "§eNo base named §f" + baseValue + "§e nearby.";
        }
        return this.selectedType == MarkerType.HELIPAD_B ? "§7Use its helipad's base."
                : "§aOn " + baseValue + "'s base screen.";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void save() {
        String id = this.idField.getValue().trim();
        String base = this.selectedType == MarkerType.BASE ? "" : this.baseIdField.getValue().trim();
        int heading = this.selectedType == MarkerType.PARKING ? this.parkingHeading : -1;
        ClientPacketDistributor.sendToServer(new MarkerUpdatePayload(this.pos, this.selectedType, id, base, heading));
    }
}
