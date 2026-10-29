package com.atom.chat.fun;

/** Screen-owned playback; config loading is a baseline, never an activation event. */
public final class ExperimentalEffects implements AutoCloseable {
    public static final long DURATION_MS=3000;
    private final ExperimentalEffectMatcher matcher=new ExperimentalEffectMatcher();
    private final ExperimentalEffectRenderer renderer=new ExperimentalEffectRenderer();
    private boolean observedEnabled,allowed,closed;
    private ExperimentalEffectType type;
    private long started;

    public ExperimentalEffects(boolean initiallyEnabled) { observedEnabled=initiallyEnabled; }
    public boolean advance(boolean enabled,boolean animation,long now) {
        if(closed)return false;
        boolean newlyEnabled=enabled&&!observedEnabled;observedEnabled=enabled;
        allowed=enabled&&animation;
        if(!allowed){clear();return false;}
        if(newlyEnabled){type=ExperimentalEffectType.IGNITION;started=now;}
        if(type!=null&&(now<started||now-started>=DURATION_MS))clear();
        return type!=null;
    }
    public void onMessage(String text,long now) {
        if(!allowed||closed||type!=null)return;
        matcher.match(text).ifPresent(next->{type=next;started=now;});
    }
    public ExperimentalEffectType type(){return type;}
    public void clear(){type=null;}
    public void render(io.github.humbleui.skija.Surface surface,long now) {
        if(type!=null)renderer.drawDisplayFramebuffer(surface,type,Math.max(0,Math.min(1,(now-started)/(float)DURATION_MS)));
    }
    @Override public void close(){clear();allowed=false;closed=true;renderer.close();}
}
