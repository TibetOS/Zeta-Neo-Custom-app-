package at.planqton.fytfm;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * TWUtilHelper - Wrapper für android.tw.john.TWUtil
 *
 * TWUtil ist die FYT-spezifische API zur Kommunikation mit der MCU
 * (Microcontroller Unit) des Head Units. Diese Klasse ermöglicht:
 * - Audio-Routing zum FM-Tuner
 * - RDS-Initialisierung
 * - Hardware-Steuerung
 */
public class TWUtilHelper {
    private static final String TAG = "TWUtilHelper";
    private static final String TWUTIL_CLASS = "android.tw.john.TWUtil";

    // TWUtil Commands
    public static final int CMD_RADIO_POWER = 0x101;
    public static final int CMD_RADIO_FREQ = 0x102;
    public static final int CMD_RADIO_SEEK = 0x103;
    public static final int CMD_RADIO_BAND = 0x104;
    public static final int CMD_RADIO_MUTE = 0x105;
    public static final int CMD_AUDIO_SOURCE = 0x110;
    public static final int CMD_AUDIO_VOLUME = 0x111;
    public static final int CMD_RADIO_AREA = 0x112;

    // Audio Sources
    public static final int AUDIO_SOURCE_FM = 0x01;
    // 0x02 ist die übliche Konvention auf SYU/FYT-MCUs; muss am Gerät verifiziert
    // werden (kein 100%iger Beleg aus den dekompilierten FYT-Apps verfügbar).
    public static final int AUDIO_SOURCE_AM = 0x02;

    private Object twUtilInstance;
    private Class<?> twUtilClass;
    private boolean isAvailable = false;
    private TWUtilCallback callback;

    // Handler für MCU Events
    private Handler mcuHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (callback != null) {
                callback.onMcuMessage(msg.what, msg.arg1, msg.arg2, msg.obj);
            }
        }
    };

    public interface TWUtilCallback {
        void onMcuMessage(int what, int arg1, int arg2, Object obj);
    }

    public TWUtilHelper() {
        checkAvailability();
    }

    private void checkAvailability() {
        try {
            twUtilClass = Class.forName(TWUTIL_CLASS);
            isAvailable = true;
            Log.i(TAG, "TWUtil is available on this device");
        } catch (ClassNotFoundException e) {
            isAvailable = false;
            Log.w(TAG, "TWUtil not available");
        }
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public void setCallback(TWUtilCallback callback) {
        this.callback = callback;
    }

    public boolean open() {
        if (!isAvailable) {
            Log.e(TAG, "TWUtil not available");
            return false;
        }

        try {
            Constructor<?> constructor = twUtilClass.getConstructor(int.class);
            twUtilInstance = constructor.newInstance(1);

            short[] commands = new short[] {
                0x101, 0x102, 0x103, 0x104, 0x105, 0x106,
                0x110, 0x111, 0x112, 0x113, 0x114, 0x115
            };

            Method openMethod = twUtilClass.getMethod("open", short[].class);
            int result = (int) openMethod.invoke(twUtilInstance, commands);

            if (result != 0) {
                Log.e(TAG, "TWUtil.open() failed with code: " + result);
                return false;
            }

            Method startMethod = twUtilClass.getMethod("start");
            startMethod.invoke(twUtilInstance);

            Method addHandlerMethod = twUtilClass.getMethod("addHandler", String.class, Handler.class);
            addHandlerMethod.invoke(twUtilInstance, "radio", mcuHandler);

            Log.i(TAG, "TWUtil opened successfully");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Failed to open TWUtil: " + e.getMessage());
            return false;
        }
    }

    public void close() {
        if (twUtilInstance == null) return;

        try {
            Method stopMethod = twUtilClass.getMethod("stop");
            stopMethod.invoke(twUtilInstance);

            Method closeMethod = twUtilClass.getMethod("close");
            closeMethod.invoke(twUtilInstance);

            twUtilInstance = null;
            Log.i(TAG, "TWUtil closed");

        } catch (Exception e) {
            Log.e(TAG, "Failed to close TWUtil: " + e.getMessage());
        }
    }

    public int write(int cmd, int value) {
        if (twUtilInstance == null) return -1;

        try {
            Method writeMethod = twUtilClass.getMethod("write", int.class, int.class);
            return (int) writeMethod.invoke(twUtilInstance, cmd, value);
        } catch (Exception e) {
            Log.e(TAG, "write failed: " + e.getMessage());
            return -1;
        }
    }

    public int write(int cmd, int value1, int value2) {
        if (twUtilInstance == null) return -1;

        try {
            Method writeMethod = twUtilClass.getMethod("write", int.class, int.class, int.class);
            return (int) writeMethod.invoke(twUtilInstance, cmd, value1, value2);
        } catch (Exception e) {
            Log.e(TAG, "write failed: " + e.getMessage());
            return -1;
        }
    }

    public void setAudioSourceFm() {
        int result = write(CMD_AUDIO_SOURCE, AUDIO_SOURCE_FM);
        Log.i(TAG, "setAudioSourceFm: CMD_AUDIO_SOURCE(FM) = " + result);
    }

    public void setAudioSourceAm() {
        int result = write(CMD_AUDIO_SOURCE, AUDIO_SOURCE_AM);
        Log.i(TAG, "setAudioSourceAm: CMD_AUDIO_SOURCE(AM) = " + result);
    }

    /** FM-Default-Variante: Power-On + FM-Audio-Routing. Entspricht der historischen radioOn(). */
    public void radioOnFm() {
        Log.i(TAG, "radioOnFm() called");
        int powerResult = write(CMD_RADIO_POWER, 1);
        Log.i(TAG, "CMD_RADIO_POWER(1) = " + powerResult);
        setAudioSourceFm();
    }

    /** AM-Variante: Power-On + AM-Audio-Routing. */
    public void radioOnAm() {
        Log.i(TAG, "radioOnAm() called");
        int powerResult = write(CMD_RADIO_POWER, 1);
        Log.i(TAG, "CMD_RADIO_POWER(1) = " + powerResult);
        setAudioSourceAm();
    }

    /** Backward-Compat-Wrapper für DebugReceiver/externe Aufrufer — leitet auf FM weiter. */
    public void radioOn() {
        radioOnFm();
    }

    public void radioOff() {
        write(CMD_RADIO_POWER, 0);
    }

    public void unmute() {
        int result = write(CMD_RADIO_MUTE, 0);
        Log.i(TAG, "unmute: CMD_RADIO_MUTE(0) = " + result);
    }

    public void mute() {
        int result = write(CMD_RADIO_MUTE, 1);
        Log.i(TAG, "mute: CMD_RADIO_MUTE(1) = " + result);
    }

    /**
     * Initialisierungs-Sequenz für FYT Radio - CRITICAL for RDS!
     * This must be called before FM powerOn to enable RDS properly.
     */
    public void initRadioSequence() {
        write(CMD_RADIO_POWER, 0xFF);     // Power Query
        write(CMD_RADIO_FREQ, 0xFF);      // Freq Query
        write(CMD_RADIO_FREQ, 0xFF, 1);   // Freq Init
        write(CMD_RADIO_AREA, 0xFF);      // Area Query
        write(CMD_RADIO_FREQ, 0xFF, 0);   // Reset
        write(CMD_RADIO_BAND, 0xFF);      // Band Query
        write(CMD_RADIO_SEEK, 0);         // Seek Init
        write(CMD_RADIO_MUTE, 0xFF);      // Mute Query
        write(CMD_RADIO_POWER, 0xFF);     // Power Query
        write(CMD_AUDIO_SOURCE, 0xFF);    // Source Query
        Log.d(TAG, "Radio init sequence sent");
    }
}
