package com.atom.chat.ui.input;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CompletionInputTest {
    @Test void firstEscapeClosesCandidatesAndSecondEscapeReachesTheBorrowingEditor() {
        var calls = new ArrayList<String>(); boolean[] window = {true};
        var completion = new CompletionInput(new CompletionInput.Host() {
            public boolean available() { return true; }
            public boolean click(double x,double y,int button) { return false; }
            public boolean scroll(double x,double y,double amount) { return false; }
            public boolean key(int key,int scan,int modifiers) {
                calls.add("native:"+key);
                if (key==256 && window[0]) { window[0]=false; return true; } return false;
            }
        });
        var router = new InputRouter().add(completion).add(new InputHandler() {
            public boolean onKey(int key,int scan,int modifiers) { calls.add("phrase-editor:"+key); return true; }
        });
        assertTrue(router.key(256,0,0)); assertEquals(List.of("native:256"), calls);
        assertTrue(router.key(256,0,0)); assertEquals(List.of("native:256","native:256","phrase-editor:256"), calls);
    }
    @Test void blockedModesNeverDispatchKeysOrPointerToHiddenCandidates() {
        var completion = new CompletionInput(new CompletionInput.Host() {
            public boolean available() { return false; }
            public boolean click(double x,double y,int button) { fail(); return true; }
            public boolean scroll(double x,double y,double amount) { fail(); return true; }
            public boolean key(int key,int scan,int modifiers) { fail(); return true; }
        });
        assertFalse(completion.onKey(258,0,1)); assertFalse(completion.onClick(1,1,1)); assertFalse(completion.onScroll(1,1,0,-1));
        assertFalse(completion.onChar('中',0));
    }
}
