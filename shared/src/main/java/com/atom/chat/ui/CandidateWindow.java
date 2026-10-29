package com.atom.chat.ui;

import com.mojang.brigadier.suggestion.Suggestion;
import java.util.List;

/** Mapping-neutral view of the vanilla completion window. Vanilla still applies the selected suggestion. */
public interface CandidateWindow {
    List<Suggestion> atomchat$getCandidates();
    int atomchat$getOffset();
    void atomchat$setOffset(int offset);
    int atomchat$getSelected();
    void atomchat$select(int index);
    void atomchat$complete();
}
