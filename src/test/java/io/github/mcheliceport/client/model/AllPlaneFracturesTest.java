package io.github.mcheliceport.client.model;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.content.ContentTestSupport;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Every stock plane body, torn at both wings and the tail, closes each cut on the kept and the falling side. */
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
            for(int i=0;i<3;i++) {
                int bit=1<<i;if((frame.mask()&bit)==0||!frame.openings().containsKey(bit))continue;
                int axis=i==2?2:0,outward=i==1?1:-1;float cut=i==0?frame.leftCut():i==1?frame.rightCut():frame.tailCut();
                var kept=FractureInteriorMeshes.build(frame.kept(),axis,cut,outward).cavity();
                var section=FractureInteriorMeshes.shell(frame.sections().get(i),axis,cut,-outward,4096);
                checked++;
                if(BreakupMeshes.area(kept)<1.0E-3)failures.add(name+" kept side of cut "+i+" has no cap");
                if(section.cap().vertexCount()==0&&section.lining().vertexCount()==0)failures.add(name+" falling section "+i+" is not closed");
                for(var g:List.of(kept,section.cap(),section.lining()))for(float f:g.data())if(!Float.isFinite(f)){failures.add(name+" non-finite geometry");break;}
            }
            if(frame.mask()!=0&&frame.lining().vertexCount()==0)failures.add(name+" kept body has no inner lining");
        }
        assertTrue(checked>60,"too few cuts exercised: "+checked);
        if(!failures.isEmpty())fail(failures.size()+" open cuts:\n"+String.join("\n",failures));
    }
}
