package com.indemnify.contract.manager.util;

import com.hedera.hashgraph.sdk.Hbar;

public class Utils {
    public static final int FILE_PART_SIZE = 5000;

    public static byte[] copyBytes(int start, int length, byte[] bytes) {
        byte[] rv = new byte[length];
        for (int i = 0; i < length; i++) {
            rv[i] = bytes[start + i];
        }
        return rv;
    }


    public static Hbar addMargin(Hbar inCost, int factor) {
        long tinybars = inCost.toTinybars();
        var finalCost = tinybars + tinybars / factor;
        return Hbar.from(finalCost);
    }

}

