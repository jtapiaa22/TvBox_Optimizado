import android.media.MediaCodecInfo;
import android.media.MediaCodecList;

/** Diagnostico de solo lectura: que perfiles/niveles y tamanos informa cada decodificador de video de la caja. */
public class CodecInfo {
    static String level(int mime, int l) { return Integer.toHexString(l); }

    public static void main(String[] a) {
        for (MediaCodecInfo ci : new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos()) {
            if (ci.isEncoder()) continue;
            for (String t : ci.getSupportedTypes()) {
                if (!t.equals("video/avc") && !t.equals("video/hevc")) continue;
                MediaCodecInfo.CodecCapabilities c = ci.getCapabilitiesForType(t);
                StringBuilder sb = new StringBuilder(ci.getName() + "  [" + t + "]");
                sb.append("  seguro=").append(c.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback));
                MediaCodecInfo.VideoCapabilities v = c.getVideoCapabilities();
                if (v != null) {
                    sb.append("\n    ancho ").append(v.getSupportedWidths()).append("  alto ").append(v.getSupportedHeights());
                    sb.append("  bitrate ").append(v.getBitrateRange());
                    sb.append("\n    1080p@30=").append(v.areSizeAndRateSupported(1920, 1080, 30))
                      .append("  720p@30=").append(v.areSizeAndRateSupported(1280, 720, 30))
                      .append("  576p@25=").append(v.areSizeAndRateSupported(1024, 576, 25))
                      .append("  480p@30=").append(v.areSizeAndRateSupported(854, 480, 30));
                }
                sb.append("\n    perfil:nivel (hex) =");
                for (MediaCodecInfo.CodecProfileLevel pl : c.profileLevels)
                    sb.append(' ').append(Integer.toHexString(pl.profile)).append(':').append(Integer.toHexString(pl.level));
                System.out.println(sb);
            }
        }
    }
}
