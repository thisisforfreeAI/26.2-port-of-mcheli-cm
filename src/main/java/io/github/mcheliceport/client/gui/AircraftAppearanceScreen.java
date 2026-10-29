package io.github.mcheliceport.client.gui;

import io.github.mcheliceport.appearance.AircraftAppearance;
import io.github.mcheliceport.client.model.AircraftMarkingMesh;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.menu.AircraftMenu;
import io.github.mcheliceport.net.AircraftAppearancePayload;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** An explicit apply/cancel selection; changing a button does not send packets or repaint the vehicle. */
public final class AircraftAppearanceScreen extends Screen {
    private final AircraftScreen parent;
    private final AircraftMenu menu;
    private final AircraftEntity aircraft;
    private final List<String> textures;
    private int texture,symbol,wear,ink;
    private String digits;
    private EditBox number;
    public AircraftAppearanceScreen(AircraftScreen parent,AircraftMenu menu,AircraftEntity aircraft){
        super(Component.literal("Aircraft appearance"));this.parent=parent;this.menu=menu;this.aircraft=aircraft;
        textures=List.copyOf(aircraft.getAcInfo().getTextureNames());texture=Math.max(0,textures.indexOf(aircraft.getTextureName()));
        var look=aircraft.getAppearance();symbol=look.symbol();wear=look.wear();ink=look.ink();digits=look.number()<0?"":String.format(java.util.Locale.ROOT,"%03d",look.number());
    }
    @Override protected void init(){
        int x=width/2-110,y=Math.max(38,height/2-78);boolean supported=AircraftMarkingMesh.supported(aircraft.getAcInfo().name);
        addRenderableWidget(Button.builder(liveryLabel(),b->{texture=(texture+1)%textures.size();b.setMessage(liveryLabel());}).bounds(x,y,220,20).build());
        number=addRenderableWidget(new EditBox(font,x+130,y+24,90,20,Component.literal("Identification number")){
            @Override public void insertText(String text){if(text.matches("[0-9]*"))super.insertText(text);}
        });
        number.setMaxLength(3);number.setValue(digits);number.setResponder(s->digits=s);number.setEditable(supported);
        var symbolButton=addRenderableWidget(Button.builder(symbolLabel(),b->{symbol=(symbol+1)%4;b.setMessage(symbolLabel());}).bounds(x,y+48,220,20).build());symbolButton.active=supported;
        var inkButton=addRenderableWidget(Button.builder(inkLabel(),b->{ink=(ink+1)%3;b.setMessage(inkLabel());}).bounds(x,y+72,108,20).build());inkButton.active=supported;
        var wearButton=addRenderableWidget(Button.builder(wearLabel(),b->{wear=(wear+1)%3;b.setMessage(wearLabel());}).bounds(x+112,y+72,108,20).build());wearButton.active=supported;
        var apply=addRenderableWidget(Button.builder(Component.literal("Apply"),b->{
            if(!digits.matches("[0-9]{0,3}"))return;
            int id=digits.isEmpty()?-1:Integer.parseInt(digits);
            ClientPacketDistributor.sendToServer(new AircraftAppearancePayload(aircraft.getId(),textures.get(texture),new AircraftAppearance(id,symbol,wear,ink).packed()));onClose();
        }).bounds(x,y+124,108,20).build());apply.active=!aircraft.isDestroyed()&&aircraft.getCurrentThrottle()<=.05&&aircraft.getDeltaMovement().lengthSqr()<=.04;
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),b->onClose()).bounds(x+112,y+124,108,20).build());
    }
    private Component liveryLabel(){return Component.literal("Livery: "+font.plainSubstrByWidth(textures.get(texture),175));}
    private Component symbolLabel(){return Component.literal("Squadron mark: "+List.of("None","Chevron","Star","Bars").get(symbol));}
    private Component inkLabel(){return Component.literal(List.of("Pale paint","Low visibility","Olive paint").get(ink));}
    private Component wearLabel(){return Component.literal(List.of("Clean","Service wear","Worn paint").get(wear));}
    @Override public void tick(){if(minecraft.player==null||minecraft.player.containerMenu!=menu||aircraft.isRemoved())minecraft.gui.setScreen(null);}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float partial){
        super.extractRenderState(g,mx,my,partial);int x=width/2-110,y=Math.max(38,height/2-78);
        g.centeredText(font,title,width/2,y-24,0xFFFFFFFF);g.text(font,"Number (blank: none)",x,y+30,0xFFFFFFFF);
        boolean supported=AircraftMarkingMesh.supported(aircraft.getAcInfo().name);
        g.centeredText(font,supported?"Park nearby with the engine off to apply.":"This aircraft supports liveries only.",width/2,y+100,0xFFCCCCCC);
        g.centeredText(font,"Markings: Enhanced / Polished effects",width/2,y+111,0xFFAAAAAA);
    }
}
