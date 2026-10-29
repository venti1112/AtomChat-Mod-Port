package com.atom.chat.probe;

import com.atom.chat.fun.*;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Real GL, same-frame snapshots, reset between frames; never launches Minecraft. */
public final class ExperimentalEffectsProbe {
    public static void main(String[] args)throws Exception {
        if(!GLFW.glfwInit())throw new AssertionError("GLFW unavailable");
        Path output=Path.of(args[0]);Files.createDirectories(output);
        try { for(int width:new int[]{960,1920})run(width,width*9/16,output); }
        finally {GLFW.glfwTerminate();}
    }
    private static void run(int width,int height,Path output)throws Exception {
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_STENCIL_BITS,8);
        long window=GLFW.glfwCreateWindow(width,height,"AtomChat experimental effects probe",0,0);
        if(window==0)throw new AssertionError("No hidden GL window");
        GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
        try(var context=DirectContext.makeGL();var target=BackendRenderTarget.makeGL(width,height,0,8,0,GL11C.GL_RGBA8);
            var surface=Surface.wrapBackendRenderTarget(context,target,SurfaceOrigin.BOTTOM_LEFT,ColorType.RGBA_8888,ColorSpace.getSRGB());
            var renderer=new ExperimentalEffectRenderer();var paint=new Paint()) {
            if(surface==null)throw new AssertionError("No GPU surface");
            var canvas=surface.getCanvas();
            System.out.printf("GPU=%s; viewport=%dx%d%n",GL11C.glGetString(GL11C.GL_RENDERER),width,height);
            for(var type:ExperimentalEffectType.values()) {
                long[] costs=new long[60];long first=0;
                for(int frame=0;frame<80;frame++) {
                    context.resetAll();drawScene(canvas,paint,width,height,frame);
                    context.flushAndSubmit(surface);GL11C.glFinish();
                    int saves=canvas.getSaveCount();
                    long start=System.nanoTime();
                    renderer.drawDisplayFramebuffer(surface,type,.18f+(frame%60)/59f*.64f);
                    context.flushAndSubmit(surface);GL11C.glFinish();
                    long elapsed=System.nanoTime()-start;if(frame==0)first=elapsed;
                    if(frame>=20)costs[frame-20]=elapsed;
                    if(canvas.getSaveCount()!=saves)throw new AssertionError("Canvas leaked");
                    if(GL11C.glGetError()!=0)throw new AssertionError("OpenGL error");
                    if(frame==24) {
                        try(var bitmap=new Bitmap()) {
                            bitmap.allocN32Pixels(width,height);
                            if(!surface.readPixels(bitmap,0,0))throw new AssertionError("Screenshot readback failed");
                            try(var image=Image.makeFromBitmap(bitmap);var data=image.encodeToData()) {
                                Files.write(output.resolve(type.name().toLowerCase()+"-"+width+".png"),data.getBytes());
                            }
                        }
                    }
                }
                Arrays.sort(costs);
                System.out.printf("%s first=%.3fms warmed-p50=%.3fms p95=%.3fms max=%.3fms; 80 reset/snapshot/submit/finish frames%n",
                    type,first/1e6,costs[30]/1e6,costs[57]/1e6,costs[59]/1e6);
            }
            // Source rows must not flip when snapshotting a BOTTOM_LEFT backend.
            canvas.clear(0xFF0000FF);canvas.drawRect(Rect.makeXYWH(0,0,width,height/2),paint.setColor(0xFFFF0000));
            renderer.drawDisplayFramebuffer(surface,ExperimentalEffectType.WAVE,.08f);context.flushAndSubmit(surface);GL11C.glFinish();
            try(var bitmap=new Bitmap()) {
                bitmap.allocN32Pixels(width,height);if(!surface.readPixels(bitmap,0,0))throw new AssertionError("Readback failed");
                int top=bitmap.getColor(width/2,5),bottom=bitmap.getColor(width/2,height-5);
                if(((top>>>16)&255)<(top&255)||(bottom&255)<((bottom>>>16)&255))throw new AssertionError("Framebuffer snapshot flipped");
            }
            System.out.println("Same-frame resources, matrix/stack and vertical sampling checks passed");
            int[] sampleX={0,width-1,Math.round(width*.04f)-1,Math.round(width*.04f),Math.round(width*.04f)+1};
            for(var type:ExperimentalEffectType.values()) {
                int[] expected=null;
                for(float sourceAlpha:new float[]{1,0,.2f,.5f}) {
                    context.resetAll();drawScene(canvas,paint,width,height,0);context.flushAndSubmit(surface);
                    // Keep resolved RGB intact and replace only the shaderpack's non-coverage alpha.
                    org.lwjgl.opengl.GL30C.glBindFramebuffer(org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER,0);
                    GL11C.glDisable(GL11C.GL_SCISSOR_TEST);GL11C.glColorMask(false,false,false,true);
                    GL11C.glClearColor(0,0,0,sourceAlpha);GL11C.glClear(GL11C.GL_COLOR_BUFFER_BIT);
                    GL11C.glColorMask(true,true,true,true);context.resetAll();
                    renderer.drawDisplayFramebuffer(surface,type,.43f);context.flushAndSubmit(surface);GL11C.glFinish();
                    try(var bitmap=new Bitmap()) {
                        bitmap.allocN32Pixels(width,height);if(!surface.readPixels(bitmap,0,0))throw new AssertionError("display-alpha readback failed");
                        int[] actual=new int[sampleX.length*3];int next=0;
                        for(int x:sampleX)for(int y:new int[]{0,height/2,height-1}) {
                            int color=bitmap.getColor(x,y);actual[next++]=color;
                            if((color>>>24)!=255)throw new AssertionError("display output is not opaque");
                        }
                        if(expected==null)expected=actual;
                        else if(!Arrays.equals(expected,actual))throw new AssertionError(type+" world/UI boundary or screen edge differs at alpha="+sourceAlpha);
                        if(sourceAlpha==0)try(var image=Image.makeFromBitmap(bitmap);var data=image.encodeToData()) {
                            Files.write(output.resolve("iris-alpha0-"+type.name().toLowerCase()+"-"+width+".png"),data.getBytes());
                        }
                    }
                    if(GL11C.glGetError()!=0)throw new AssertionError("display-alpha GL error");
                }
            }
            System.out.println("All four effects: alpha=0/0.2/0.5/1 match at 15 world/UI boundary and screen-edge samples");
        } finally {GLFW.glfwDestroyWindow(window);}
    }
    private static void drawScene(Canvas canvas,Paint paint,int w,int h,int frame) {
        canvas.clear(0xFF26384C);
        for(int x=0;x<w;x+=w/24)canvas.drawRect(Rect.makeXYWH(x,0,2,h),paint.setColor(0xFF506070));
        for(int y=0;y<h;y+=h/14)canvas.drawRect(Rect.makeXYWH(0,y,w,2),paint.setColor(0xFF506070));
        canvas.drawRRect(io.github.humbleui.types.RRect.makeXYWH(w*.04f,h*.07f,w*.3f,h*.86f,24),paint.setColor(0xEE181B21));
        for(int i=0;i<6;i++)canvas.drawRRect(io.github.humbleui.types.RRect.makeXYWH(w*.06f,h*(.12f+i*.11f),w*(.20f+(i%2)*.05f),h*.075f,14),paint.setColor(i%2==0?0xFF345B76:0xFF3C4048));
        canvas.drawCircle(w*.65f+(float)Math.sin(frame*.1)*w*.02f,h*.3f,h*.12f,paint.setColor(0xFF96B9B4));
    }
}
