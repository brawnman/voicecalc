import com.jcraft.jorbis.VorbisFile;
import com.jcraft.jorbis.Info;
import java.io.*;
import java.lang.reflect.Method;

/** 测试 VorbisFile.read 反射解码，输出首尾振幅。 */
public class OggInspect {
    static Method readMethod;
    public static void main(String[] args) throws Exception {
        readMethod = VorbisFile.class.getDeclaredMethod("read",
                byte[].class, int.class, int.class, int.class, int.class, int[].class);
        readMethod.setAccessible(true);
        for (String path : args) {
            VorbisFile vf = new VorbisFile(path);
            Info vi = vf.getInfo(0);
            int rate = vi.rate, ch = vi.channels;
            byte[] buf = new byte[4096 * 4];
            int[] section = new int[1];
            int totalBytes = 0;
            byte[] all = new byte[1 << 22];
            while (totalBytes < all.length) {
                int ret = (Integer) readMethod.invoke(vf, buf, buf.length, 2, 1, 0, section);
                if (ret <= 0) break;
                int n = Math.min(ret, all.length - totalBytes);
                System.arraycopy(buf, 0, all, totalBytes, n);
                totalBytes += n;
            }
            vf.close();
            // 16bit LE
            int nSamples = totalBytes / 2;
            float first = totalBytes >= 2 ? (short)(((all[1]&0xFF)<<8)|(all[0]&0xFF)) / 32768f : 0;
            float last = totalBytes >= 2 ? (short)(((all[totalBytes-1]&0xFF)<<8)|(all[totalBytes-2]&0xFF)) / 32768f : 0;
            System.out.printf("%-12s rate=%d ch=%d dur=%.3fs samples=%d 首=%+.4f 尾=%+.4f%n",
                    new File(path).getName(), rate, ch, nSamples/(double)rate, nSamples, first, last);
        }
    }
}
