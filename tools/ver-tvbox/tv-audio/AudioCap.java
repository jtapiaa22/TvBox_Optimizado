import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.io.OutputStream;
import java.lang.reflect.Method;

/**
 * Lee el audio de salida del TV box ("remote submix") y lo escribe como PCM 16 bits, 48 kHz, estéreo, en stdout.
 * Se ejecuta con app_process desde adb. Experimental: Android 7 no trae captura de audio para el usuario.
 */
public class AudioCap {
    public static void main(String[] args) throws Exception {
        // Hace falta un ActivityThread para que AudioRecord conozca el nombre de paquete (si no, falla en nativo).
        try {
            android.os.Looper.prepareMainLooper();
            Class<?> at = Class.forName("android.app.ActivityThread");
            Method sys = at.getMethod("systemMain");
            sys.invoke(null);
        } catch (Throwable t) {
            System.err.println("aviso: no se pudo crear ActivityThread: " + t);
        }

        int rate = 48000;
        int ch = AudioFormat.CHANNEL_IN_STEREO;
        int enc = AudioFormat.ENCODING_PCM_16BIT;
        int min = AudioRecord.getMinBufferSize(rate, ch, enc);
        System.err.println("minBuffer=" + min);
        AudioRecord r = new AudioRecord(MediaRecorder.AudioSource.REMOTE_SUBMIX, rate, ch, enc, Math.max(min * 4, 65536));
        System.err.println("estado=" + r.getState() + " (1 = listo)");
        if (r.getState() != AudioRecord.STATE_INITIALIZED) System.exit(2);
        r.startRecording();
        System.err.println("grabando=" + r.getRecordingState() + " (3 = grabando)");

        byte[] b = new byte[7680];       // 40 ms
        OutputStream o = System.out;
        while (true) {
            int n = r.read(b, 0, b.length);
            if (n < 0) { System.err.println("read=" + n); System.exit(3); }
            if (n > 0) { o.write(b, 0, n); o.flush(); }
        }
    }
}
