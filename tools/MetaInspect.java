import com.jcraft.jorbis.VorbisFile;
import com.jcraft.jorbis.Info;
import java.io.File;

/** 列出 ogg 的采样率/声道/时长。用法: java -cp lib/jorbis-0.0.17.jar MetaInspect <dir> */
public class MetaInspect {
    public static void main(String[] args) throws Exception {
        File dir = new File(args[0]);
        File[] fs = dir.listFiles((d, n) -> n.endsWith(".ogg"));
        java.util.Arrays.sort(fs, (a, b) -> a.getName().compareTo(b.getName()));
        java.util.Set<Integer> rates = new java.util.TreeSet<>();
        for (File f : fs) {
            VorbisFile vf = new VorbisFile(f.getPath());
            Info vi = vf.getInfo(0);
            rates.add(vi.rate);
            System.out.printf("%-14s rate=%5d ch=%d dur=%.3fs%n", f.getName(), vi.rate, vi.channels, vf.time_total(0));
            vf.close();
        }
        System.out.println("=== 采样率集合: " + rates + " ===");
    }
}
