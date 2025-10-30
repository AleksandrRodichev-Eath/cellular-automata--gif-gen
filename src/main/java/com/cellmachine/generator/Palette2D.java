package com.cellmachine.generator;

public enum Palette2D {
    Paperback2("#382b26", "#b8c2b9"),
    ysNeutralGreen("#004c3d", "#ffeaf9"),
    bitbee("#292b30", "#cfab4a"),
    casioBasic("#000000", "#83b07e"),
    ibm51("#323c39", "#d3c9a1"),
    ysConcreteJungle("#121216", "#e8e6e1"),
    oneBitPepper("#100101", "#ebb5b5"),
    razSandwich("#400927", "#ffe1c5"),
    cgaPastel("#360072", "#ffbf83");

    String deadColor;
    String aliveColor;

    Palette2D(String deadColor, String aliveColor) {
        this.deadColor = deadColor;
        this.aliveColor = aliveColor;
    }
}
