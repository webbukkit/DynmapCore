package org.dynmap.utils;

/* Represents last step of movement of the ray (don't alter order here - ordinal sensitive) */
public enum BlockStep {
    X_PLUS,
    Y_PLUS,
    Z_PLUS,
    X_MINUS,
    Y_MINUS,
    Z_MINUS;
    
    private static final BlockStep op[] = { X_MINUS, Y_MINUS, Z_MINUS, X_PLUS, Y_PLUS, Z_PLUS };
            
    public final BlockStep opposite() {
        return op[ordinal()];
    }
}

