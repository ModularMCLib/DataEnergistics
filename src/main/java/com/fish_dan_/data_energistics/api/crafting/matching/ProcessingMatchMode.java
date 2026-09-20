package com.fish_dan_.data_energistics.api.crafting.matching;

/** User-selected item equivalence; TAG means the recipe's declared tags, never the item's unrelated tags. */
public enum ProcessingMatchMode {

    EXACT,
    ID,
    TAG;

    public ProcessingMatchMode next() {
        return this == EXACT ? ID : this == ID ? TAG : EXACT;
    }
}
