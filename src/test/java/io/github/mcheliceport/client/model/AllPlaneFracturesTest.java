package io.github.mcheliceport.client.model;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.content.ContentTestSupport;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Every stock plane body, torn at both wings, closes each cut on the kept and the falling side; a lost tail keeps its fuselage. */
class AllPlaneFracturesTest {
    @Test void everyStockPlaneClosesEveryTornWingAndTail() throws Exception {
        Path root=ContentTestSupport.projectDir().resolve("src/main/resources/assets/mcheli/models/planes");
        List<Path> files;try(var walk=Files.list(root)){files=walk.filter(p->p.toString().endsWith(".mqo")||p.toString().endsWith(".obj")).sorted().toList();}
        assertTrue(files.size()>30,"expected the stock planes, found "+files.size());
        var failures=new ArrayList<String>();int checked=0;
        for(Path file:files) {
            String name=file.getFileName().toString();
            McheliModel model;try(var in=Files.newInputStream(file)){model=name.endsWith(".obj")?ObjParser.parse(name,in):MqoParser.parse(name,in);}
            var body=model.groups.stream().filter(g->g.name().equals("$body")).findFirst().orElse(null);
            if(body==null||body.vertexCount()<3||body.vertexCount()>60000)continue;
            var frame=BreakupMeshes.airframeBuild(body,7);
            for(int i=0;i<2;i++) {
                int bit=1<<i;if((frame.mask()&bit)==0||!frame.openings().containsKey(bit))continue;
                int axis=0,outward=i==1?1:-1;float cut=i==0?frame.leftCut():frame.rightCut();
                var kept=FractureInteriorMeshes.build(frame.kept(),axis,cut,outward).cavity();
                var section=FractureInteriorMeshes.shell(frame.sections().get(i),axis,cut,-outward,4096);
                checked++;
                if(BreakupMeshes.area(kept)<1.0E-3)failures.add(name+" kept side of cut "+i+" has no cap");
                if(section.cap().vertexCount()==0&&section.lining().vertexCount()==0)failures.add(name+" falling section "+i+" is not closed");
                for(var g:List.of(kept,section.cap(),section.lining()))for(float f:g.data())if(!Float.isFinite(f)){failures.add(name+" non-finite geometry");break;}
            }
            if((frame.mask()&3)!=0&&frame.lining().vertexCount()==0)failures.add(name+" kept body has no inner lining");
        }
        assertTrue(checked>40,"too few cuts exercised: "+checked);
        if(!failures.isEmpty())fail(failures.size()+" open cuts:\n"+String.join("\n",failures));
    }
    /** Losing the tail snaps off fins and stabilisers; the rear fuselage, with its engines, stays on the airframe. */
    @Test void aLostTailLeavesTheRearFuselageAttached() throws Exception {
        Path root=ContentTestSupport.projectDir().resolve("src/main/resources/assets/mcheli/models/planes");
        var failures=new ArrayList<String>();
        for(String name:List.of("a-10","a6m2","ac-130","b29","c5","cessna172","e767","f-15e","f-35a","f14d","f22a","f4a","fa18f","md90","mig29","p-51d","su37")) {
            McheliModel model;try(var in=Files.newInputStream(root.resolve(name+".mqo"))){model=MqoParser.parse(name,in);}
            var body=model.groups.stream().filter(g->g.name().equals("$body")).findFirst().orElseThrow();
            var frame=BreakupMeshes.airframeBuild(body,4);var tail=frame.sections().get(2);
            // Halfway down the tail the kept skin still wraps a whole fuselage cross-section.
            var b=BreakupMeshes.bounds(body);float mid=(frame.tailCut()+b.low().z)*.5F;
            float low=Float.POSITIVE_INFINITY,high=Float.NEGATIVE_INFINITY;var d=frame.kept().data();
            for(int t=0;t+23<d.length;t+=24){
                float z0=Math.min(d[t+2],Math.min(d[t+10],d[t+18])),z1=Math.max(d[t+2],Math.max(d[t+10],d[t+18]));
                if(z0<=mid&&z1>=mid)for(int k=0;k<3;k++){low=Math.min(low,d[t+k*8+1]);high=Math.max(high,d[t+k*8+1]);}
            }
            if(!(high-low>.5F))failures.add(name+" has no fuselage left at z="+mid);
            if(tail.vertexCount()==0)failures.add(name+" sheds nothing");
        }
        if(!failures.isEmpty())fail(String.join("\n",failures));
    }
    /** A flying wing's torn wing keeps its trailing edge; nothing outboard is left floating on the stump. */
    @Test void theB2LosesWholeWingsAndOnlyItsCentreTail() throws Exception {
        Path file=ContentTestSupport.projectDir().resolve("src/main/resources/assets/mcheli/models/planes/b-2a.mqo");
        McheliModel model;try(var in=Files.newInputStream(file)){model=MqoParser.parse("b-2a.mqo",in);}
        var body=model.groups.stream().filter(g->g.name().equals("$body")).findFirst().orElseThrow();
        var b=BreakupMeshes.bounds(body);var frame=BreakupMeshes.airframeBuild(body,7);
        var left=BreakupMeshes.bounds(frame.sections().get(0));var right=BreakupMeshes.bounds(frame.sections().get(1));
        assertEquals(b.low().z,left.low().z,.05);assertEquals(b.low().z,right.low().z,.05);
        var tail=BreakupMeshes.bounds(frame.sections().get(2));
        assertTrue(tail.low().x>=frame.leftCut()-.01&&tail.high().x<=frame.rightCut()+.01,"tail "+tail);
        var kept=BreakupMeshes.bounds(BreakupMeshes.airframeBuild(body,1).kept());
        assertTrue(kept.low().x>=frame.leftCut()-.01,"nothing of the left wing stays outboard of its cut: "+kept);
    }
}
