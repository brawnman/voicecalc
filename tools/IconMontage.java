import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

/** 把目录里的小图标拼成带文件名标注的网格大图，便于人工识别。 */
public class IconMontage {
    public static void main(String[] args) throws Exception {
        File dir = new File(args[0]);
        String outPath = args[1];
        int cols = Integer.parseInt(args.length > 2 ? args[2] : "12");
        int cell = Integer.parseInt(args.length > 3 ? args[3] : "90");   // 单元格尺寸
        int label = 14;                                                   // 标签高度

        File[] files = dir.listFiles();
        Arrays.sort(files, new Comparator<File>() {
            public int compare(File a, File b) { return a.getName().compareTo(b.getName()); }
        });
        // 可选分片：args[4]=分片索引(0起), args[5]=分片数
        int from = 0, to = files.length;
        if (args.length > 5) {
            int part = Integer.parseInt(args[4]), total = Integer.parseInt(args[5]);
            int per = (files.length + total - 1) / total;
            from = part * per;
            to = Math.min(files.length, from + per);
        }
        File[] all = files;
        files = Arrays.copyOfRange(all, from, to);

        int rows = (files.length + cols - 1) / cols;
        BufferedImage img = new BufferedImage(cols * cell, rows * (cell + label), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(60, 63, 65));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setFont(new Font("Monospaced", Font.BOLD, label - 3));

        for (int i = 0; i < files.length; i++) {
            int cx = (i % cols) * cell, cy = (i / cols) * (cell + label);
            int x = cx + cell / 2, y = cy + cell / 2;
            try {
                BufferedImage ic = ImageIO.read(files[i]);
                if (ic != null) {
                    int w = ic.getWidth(), h = ic.getHeight();
                    double sc = Math.min((cell - 16) / (double) w, (cell - 16) / (double) h);
                    if (sc > 4) sc = 4; // 小图放大不超过4倍，便于看清
                    int dw = Math.max(1, (int) (w * sc)), dh = Math.max(1, (int) (h * sc));
                    g.setColor(new Color(100, 100, 100));
                    g.drawRect(cx + 2, cy + 2, cell - 4, cell - 4);
                    g.drawImage(ic, x - dw / 2, y - dh / 2, dw, dh, null);
                }
            } catch (Exception ignored) {}
            g.setColor(Color.WHITE);
            String nm = files[i].getName();
            int idx = from + i;
            if (nm.length() > 12) nm = nm.substring(0, 12);
            g.drawString(idx + ":" + nm, cx + 3, cy + cell + 11);
        }
        g.dispose();
        ImageIO.write(img, "png", new File(outPath));
        System.out.println("生成 " + outPath + "  " + img.getWidth() + "x" + img.getHeight() + "  共" + files.length + "个");
    }
}
