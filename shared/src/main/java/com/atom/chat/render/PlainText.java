package com.atom.chat.render;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import java.util.*;

/** Literal editable text: retain source characters, including spaces at soft wraps and section signs. */
public final class PlainText {
    private static final Map<Font,Map<String,Float>> WIDTHS = new IdentityHashMap<>();
    private PlainText() { }
    public static float width(Font font,String text) {
        if (text == null || text.isEmpty()) return 0;
        var cache=WIDTHS.get(font);
        if(cache==null) {
            if(WIDTHS.size()>=16) WIDTHS.clear();
            cache=new LinkedHashMap<>(128,.75F,true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String,Float> e) { return size()>4096; }
            }; WIDTHS.put(font,cache);
        }
        Float value=cache.get(text); if(value!=null)return value;
        float measured=SkiaFontRenderer.literalWidth(font,text);cache.put(text,measured);return measured;
    }
    public static void draw(Canvas canvas,Font font,String text,float x,float baseline,int color) {
        SkiaFontRenderer.drawLiteralText(canvas,font,text,x,baseline,color);
    }
    public static List<String> wrap(Font font,String text,float maxWidth) {
        if(text==null || text.isEmpty())return List.of();
        var lines=new ArrayList<String>();var line=new StringBuilder();float used=0;
        for(int i=0;i<text.length();) {
            int cp=text.codePointAt(i),count=Character.charCount(cp);
            if(cp=='\n') {lines.add(line.toString());line.setLength(0);used=0;i+=count;continue;}
            String glyph=text.substring(i,i+count);float advance=width(font,glyph);
            if(line.length()>0 && used+advance>maxWidth) {lines.add(line.toString());line.setLength(0);used=0;}
            line.append(glyph);used+=advance;i+=count;
        }
        if(!line.isEmpty())lines.add(line.toString());return List.copyOf(lines);
    }
}
