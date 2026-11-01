import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import java.nio.ByteBuffer;

/** Decodifica un archivo H.264 directo con un decodificador por nombre, SIN el chequeo de perfil/nivel que hace una app. */
public class DecodeTest {
    public static void main(String[] a) throws Exception {
        String file = a[0], codec = a[1];
        MediaExtractor ex = new MediaExtractor();
        ex.setDataSource(file);
        MediaFormat f = ex.getTrackFormat(0);
        ex.selectTrack(0);
        String mime = f.getString(MediaFormat.KEY_MIME);
        MediaCodec c = MediaCodec.createByCodecName(codec);
        c.configure(f, null, null, 0);
        c.start();
        long t0 = System.currentTimeMillis();
        int frames = 0; boolean in = false, out = false;
        MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
        int w = f.getInteger(MediaFormat.KEY_WIDTH), h = f.getInteger(MediaFormat.KEY_HEIGHT);
        while (!out && System.currentTimeMillis() - t0 < 20000) {
            if (!in) {
                int i = c.dequeueInputBuffer(10000);
                if (i >= 0) {
                    ByteBuffer b = c.getInputBuffer(i);
                    int n = ex.readSampleData(b, 0);
                    if (n < 0) { c.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); in = true; }
                    else { c.queueInputBuffer(i, 0, n, ex.getSampleTime(), 0); ex.advance(); }
                }
            }
            int o = c.dequeueOutputBuffer(bi, 10000);
            if (o >= 0) {
                if (bi.size > 0) frames++;
                c.releaseOutputBuffer(o, false);
                if ((bi.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) out = true;
            } else if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                System.out.println("  formato de salida: " + c.getOutputFormat());
            }
        }
        long ms = System.currentTimeMillis() - t0;
        System.out.println(codec + "  " + mime + " " + w + "x" + h + "  -> " + frames + " cuadros en " + ms + " ms"
                + (out ? "  (termino)" : "  (NO termino)"));
        c.stop(); c.release(); ex.release();
    }
}
