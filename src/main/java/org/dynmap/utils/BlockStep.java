package org.dynmap.utils;

/* Represents last step of movement of the ray (don't alter order here - ordinal sensitive) */
public enum BlockStep {
    X_PLUS(4),
    Y_PLUS(0),
    Z_PLUS(2),
    X_MINUS(5),
    Y_MINUS(1),
    Z_MINUS(3);
    
    private final int face; // Index of MC block face entered through with step (Y_MINUS = enter from top)
    
    private static final BlockStep op[] = { X_MINUS, Y_MINUS, Z_MINUS, X_PLUS, Y_PLUS, Z_PLUS };
            
    BlockStep(int f) {
        face = f;
    }
    
    public final BlockStep opposite() {
        return op[ordinal()];
    }
    // MC index of face entered by step
    public final int getFaceEntered() {
        return face;
    }
}

