package com.atom.chat.fun;

import java.util.Optional;
import java.util.regex.Pattern;

/** Deliberately small trigger vocabulary; shares the existing quote/link/command exclusions. */
public final class ExperimentalEffectMatcher {
    private static final Pattern[] RULES={
        Pattern.compile("震惊|冲击波|🌊|(?<![\\p{L}\\p{N}_])(?:shocked|shockwave)(?![\\p{L}\\p{N}_])",Pattern.CASE_INSENSITIVE),
        Pattern.compile("冷死了|结冰|❄|(?<![\\p{L}\\p{N}_])(?:freezing|frost)(?![\\p{L}\\p{N}_])",Pattern.CASE_INSENSITIVE),
        Pattern.compile("黑洞|🕳|(?<![\\p{L}\\p{N}_])black\\s+hole(?![\\p{L}\\p{N}_])",Pattern.CASE_INSENSITIVE)
    };
    private static final ExperimentalEffectType[] TYPES={ExperimentalEffectType.WAVE,ExperimentalEffectType.FROST,ExperimentalEffectType.GRAVITY};
    public Optional<ExperimentalEffectType> match(String body) {
        String text=ChatEffectMatcher.matchableText(body);if(text==null)return Optional.empty();
        int earliest=Integer.MAX_VALUE;ExperimentalEffectType found=null;
        for(int i=0;i<RULES.length;i++){var m=RULES[i].matcher(text);if(m.find()&&m.start()<earliest){earliest=m.start();found=TYPES[i];}}
        return Optional.ofNullable(found);
    }
}
