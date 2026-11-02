package io.github.mcheliceport.client.model;

import java.util.ArrayList;

/** Small immutable box meshes for authored openings; vanilla block textures supply the finish. */
public final class DetailBoxes {
    private final ArrayList<Float> data=new ArrayList<>();
    public void box(float x0,float y0,float z0,float x1,float y1,float z1){
        if(x1<=x0||y1<=y0||z1<=z0)return;
        face(new float[][]{{x0,y0,z0},{x0,y0,z1},{x0,y1,z1},{x0,y1,z0}},-1,0,0);
        face(new float[][]{{x1,y0,z1},{x1,y0,z0},{x1,y1,z0},{x1,y1,z1}},1,0,0);
        face(new float[][]{{x0,y0,z1},{x0,y0,z0},{x1,y0,z0},{x1,y0,z1}},0,-1,0);
        face(new float[][]{{x0,y1,z0},{x0,y1,z1},{x1,y1,z1},{x1,y1,z0}},0,1,0);
        face(new float[][]{{x1,y0,z0},{x0,y0,z0},{x0,y1,z0},{x1,y1,z0}},0,0,-1);
        face(new float[][]{{x0,y0,z1},{x1,y0,z1},{x1,y1,z1},{x0,y1,z1}},0,0,1);
    }
    private void face(float[][] p,float nx,float ny,float nz){
        int[] triangles={0,1,2,0,2,3};float[][] uv={{0,1},{1,1},{1,0},{0,0}};
        for(int k:triangles){for(float v:p[k])data.add(v);data.add(uv[k][0]);data.add(uv[k][1]);data.add(nx);data.add(ny);data.add(nz);}
    }
    public McheliModel.Group group(String name){float[] out=new float[data.size()];for(int i=0;i<out.length;i++)out[i]=data.get(i);return new McheliModel.Group(name,out);}
}
