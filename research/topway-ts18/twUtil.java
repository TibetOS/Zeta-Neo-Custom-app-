package com.tw.bt;

import android.tw.john.TWUtil;

public class twUtil extends TWUtil {
    private static int mCount;
    private static twUtil n;
    private int aj;

    static {
        n = new twUtil(8);
        mCount = 0;
    }

    public twUtil() {
        this.aj = 0;
    }

    public twUtil(int i) {
        super(i);
        this.aj = 0;
    }

    public static twUtil e() {
        int i = mCount;
        mCount = i + 1;
        if (i == 0) {
            if (n.open(new short[]{(short) 513, (short) 769, (short) -25080, (short) -25059}, 115200) != 0) {
                mCount--;
                return null;
            }
            n.start();
        }
        return n;
    }

    public int a(int i, int i2, String str) {
        return write(i, i2, 0, str, null);
    }

    public int a(int i, String str) {
        return write(i, 0, 0, str, null);
    }

    public void b(boolean z) {
        e(z ? 8 : 136);
    }

    public void close() {
        if (mCount > 0) {
            int i = mCount - 1;
            mCount = i;
            if (i == 0) {
                stop();
                super.close();
            }
        }
    }

    public void e(int i) {
        write(40465, 192, i);
    }

    public void f(int i) {
        this.aj = i;
        write(40448, i);
    }
}
