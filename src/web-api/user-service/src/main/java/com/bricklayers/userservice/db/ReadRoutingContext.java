package com.bricklayers.userservice.db;

import java.util.function.Supplier;

public final class ReadRoutingContext {

    private static final ThreadLocal<Integer> EXPLICIT_PRIMARY = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Boolean> STICKY_PRIMARY = ThreadLocal.withInitial(() -> false);

    private ReadRoutingContext() {
    }

    public static <T> T onPrimary(Supplier<T> action) {
        enterExplicitPrimary();
        try {
            return action.get();
        } finally {
            exitExplicitPrimary();
        }
    }

    public static void enterExplicitPrimary() {
        EXPLICIT_PRIMARY.set(EXPLICIT_PRIMARY.get() + 1);
    }

    public static void exitExplicitPrimary() {
        int depth = EXPLICIT_PRIMARY.get() - 1;
        if (depth <= 0) {
            EXPLICIT_PRIMARY.remove();
        } else {
            EXPLICIT_PRIMARY.set(depth);
        }
    }

    public static boolean isExplicitPrimary() {
        return EXPLICIT_PRIMARY.get() > 0;
    }

    public static void setStickyPrimary(boolean sticky) {
        if (sticky) {
            STICKY_PRIMARY.set(true);
        } else {
            STICKY_PRIMARY.remove();
        }
    }

    public static boolean isStickyPrimary() {
        return STICKY_PRIMARY.get();
    }

    public static void clear() {
        EXPLICIT_PRIMARY.remove();
        STICKY_PRIMARY.remove();
    }
}
