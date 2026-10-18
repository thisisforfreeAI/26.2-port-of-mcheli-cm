package io.github.mcheliceport.client.gui;

import io.github.mcheliceport.net.MarkerUpdatePayload;
import io.github.mcheliceport.net.OpenMarkerScreenPayload;
import io.github.mcheliceport.wingman.block.MarkerType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Upstream's {@code GuiMarkerConfig}, opened by right-clicking a wingman marker: two rows of type
 * buttons, the marker ID (a base's name), the parent base, the parking heading for a parking spot,
 * and Save or Cancel. The selected type is underlined and the selected heading outlined in orange.
 */
public class MarkerConfigScreen extends Screen {

    private static final MarkerType[] TYPE_ORDER = {
            MarkerType.BASE, MarkerType.RUNWAY_A, MarkerType.RUNWAY_B, MarkerType.PARKING,
            MarkerType.WAYPOINT, MarkerType.HELIPAD, MarkerType.HELIPAD_B};
    /** Upstream's heading labels: any, north, east, south, west. */
    private static final String[] HEADINGS = {"任意", "北 ↑", "東 →", "南 ↓",
            "西 ←"};

    private final BlockPos pos;
    private MarkerType selectedType;
    private int parkingHeading;
    private String idText;
    private String baseText;
    private boolean baseFocused;
    private final Button[] typeButtons = new Button[TYPE_ORDER.length];
    private final Button[] headingButtons = new Button[HEADINGS.length];
    private EditBox idField;
    private EditBox baseIdField;

    public MarkerConfigScreen(OpenMarkerScreenPayload marker) {
        super(Component.literal("Wingman Marker Config"));
        this.pos = marker.pos();
        this.selectedType = marker.markerType();
        this.parkingHeading = Math.clamp(marker.parkingHeading(), -1, 3);
        this.idText = marker.id();
        this.baseText = marker.baseId();
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int top = this.height / 4;
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
                    b -> this.selectType(type)).bounds(x, i < row1 ? top : top + 24, btnW, 20).build());
        }

        this.idField = this.field(cx - 100, top + 60, this.idText, "Marker ID");
        this.baseIdField = this.field(cx - 100, top + 96, this.baseText, "Parent Base");
        this.baseIdField.visible = this.selectedType != MarkerType.BASE;

        boolean parking = this.selectedType == MarkerType.PARKING;
        if (parking) {
            int hdgW = 44;
            int hdgGap = 4;
            int hdgX = cx - (5 * hdgW + 4 * hdgGap) / 2;
            for (int i = 0; i < HEADINGS.length; i++) {
                int heading = i - 1;
                this.headingButtons[i] = this.addRenderableWidget(Button.builder(Component.literal(HEADINGS[i]),
                        b -> this.parkingHeading = heading).bounds(hdgX + i * (hdgW + hdgGap), top + 130, hdgW, 18)
                        .build());
            }
        }
        int saveY = parking ? top + 156 : top + 132;
        this.addRenderableWidget(Button.builder(Component.literal("Save"), b -> {
            this.save();
            this.onClose();
        }).bounds(cx - 54, saveY, 50, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> this.onClose())
                .bounds(cx + 4, saveY, 50, 20).build());
    }

    private EditBox field(int x, int y, String text, String name) {
        EditBox field = new EditBox(this.font, x, y, 200, 20, Component.literal(name));
        field.setMaxLength(64);
        field.setValue(text);
        return this.addRenderableWidget(field);
    }

    @Override
    protected void setInitialFocus() {
        this.setInitialFocus(this.baseFocused && this.baseIdField.visible ? this.baseIdField : this.idField);
    }

    /**
     * Upstream {@code actionPerformed} for a type button: a base has no parent base, and only a
     * parking spot keeps a heading. The screen is laid out again for the heading row; unlike
     * upstream, what was typed into the fields is kept.
     */
    private void selectType(MarkerType type) {
        this.idText = this.idField.getValue();
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
        int top = this.height / 4;
        if (this.selectedType == MarkerType.PARKING) {
            Button b = this.headingButtons[this.parkingHeading + 1];
            graphics.fill(b.getX() - 1, b.getY() - 1, b.getX() + b.getWidth() + 1, b.getY() + b.getHeight() + 1,
                    0xAAFFAA00);
        }
        super.extractRenderState(graphics, mouseX, mouseY, a);
        graphics.centeredText(this.font, "Wingman Marker Config", cx, top - 22, 0xFFFFFFFF);
        for (int i = 0; i < TYPE_ORDER.length; i++) {
            if (TYPE_ORDER[i] == this.selectedType) {
                Button b = this.typeButtons[i];
                graphics.fill(b.getX(), b.getY() + b.getHeight(), b.getX() + b.getWidth(), b.getY() + b.getHeight() + 2,
                        0xFFFFAA00);
            }
        }
        if (this.selectedType == MarkerType.BASE) {
            graphics.text(this.font, "Base Name:", cx - 100, top + 52, 0xFFAAAAAA);
        } else {
            graphics.text(this.font, "Marker ID:", cx - 100, top + 52, 0xFFAAAAAA);
            graphics.text(this.font, "Parent Base:", cx - 100, top + 88, 0xFFAAAAAA);
        }
        if (this.selectedType == MarkerType.PARKING) {
            graphics.text(this.font, "駐機方位:", cx - 100, top + 122, 0xFFAAAAAA);
        }
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
