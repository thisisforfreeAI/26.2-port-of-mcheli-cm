package io.github.mcheliceport.client.model;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.content.ContentTestSupport;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** A torn tail comes apart into its fins and stabilisers instead of leaving as one rigid cluster. */
class TailShatterTest {
    private static McheliModel.Group tail(String name) throws Exception {
        Path file=ContentTestSupport.projectDir().resolve("src/main/resources/assets/mcheli/models/planes/"+name+".mqo");
        McheliModel model;try(var in=Files.newInputStream(file)){model=MqoParser.parse(name,in);}
        var body=model.groups.stream().filter(g->g.name().equals("$body")).findFirst().orElseThrow();
        return BreakupMeshes.airframeBuild(body,4).sections().get(2);
    }
    @Test void stockTailsBreakIntoSeveralPiecesWithNothingLost() throws Exception {
        var report=new ArrayList<String>();var failures=new ArrayList<String>();
        for(String name:List.of("f22a","f-15e","b29","a-10","cessna172","md90","f-35a","p-51d","su37","c5","ac-130","mv-22")){
            var tail=tail(name);var shards=BreakupMeshes.shatterBuild(tail);
            double whole=BreakupMeshes.area(tail),sum=shards.stream().mapToDouble(BreakupMeshes::area).sum();
            float largest=0;for(var s:shards){var b=BreakupMeshes.bounds(s);largest=Math.max(largest,Math.max(b.high().x-b.low().x,Math.max(b.high().y-b.low().y,b.high().z-b.low().z)));}
            report.add(name+": "+shards.size()+" pieces, largest "+String.format("%.2f",largest));
            if(shards.size()<3)failures.add(name+" tail left in "+shards.size()+" pieces");
            if(shards.size()>BreakupMeshes.MAX_TOTAL)failures.add(name+" tail in too many pieces: "+shards.size());
            if(Math.abs(sum-whole)>whole*1e-3)failures.add(name+" area "+sum+" vs "+whole);
        }
        System.out.println(String.join("\n",report));
        if(!failures.isEmpty())fail(String.join("\n",failures)+"\n"+String.join("\n",report));
    }
    @Test void aFinWeldedToAStabiliserComesOffSeparately(){
        // One closed mesh: a flat stabiliser straight across, and an upright fin standing on it.
        var b=new DetailBoxes();b.box(-2.5F,0,-1,2.5F,.06F,0);b.box(-.03F,.06F,-1,.03F,2,0);
        var shards=BreakupMeshes.shatterBuild(b.group("$tail"));
        assertTrue(shards.size()>=3,"left and right stabiliser and the fin: "+shards.size());
        boolean upright=false;for(var s:shards){var o=BreakupMeshes.bounds(s);if(o.high().y-o.low().y>1.5F&&o.high().x-o.low().x<.2F)upright=true;}
        assertTrue(upright,"the fin is its own piece");
    }
    @Test void noTailConeIsLeftHangingBehindTheBreak() throws Exception {
        // The AC-130 and MV-22 tail cones met the fuselage only through plating that breaks away with the tail,
        // so a wreck kept them floating behind a gap. They go with the tail; other rear fuselages stay.
        for(String name:List.of("ac-130","mv-22")){
            var frame=frame(name);
            var behind=BreakupMeshes.split(frame.kept(),2,frame.tailCut()-1.5F)[0];
            assertEquals(0,behind.vertexCount(),name+" tail cone goes with the tail");
        }
        for(String name:List.of("f22a","b29","c5","md90")){
            var frame=frame(name);
            assertTrue(BreakupMeshes.split(frame.kept(),2,frame.tailCut()-1.5F)[0].vertexCount()>0,name+" keeps its rear fuselage");
        }
    }
    private static BreakupMeshes.Airframe frame(String name) throws Exception {
        Path file=ContentTestSupport.projectDir().resolve("src/main/resources/assets/mcheli/models/planes/"+name+".mqo");
        McheliModel model;try(var in=Files.newInputStream(file)){model=MqoParser.parse(name,in);}
        var body=model.groups.stream().filter(g->g.name().equals("$body")).findFirst().orElseThrow();
        return BreakupMeshes.airframeBuild(body,4);
    }
}
