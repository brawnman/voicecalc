import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 把一张高清 PNG 图标缩放成 Android 5 档启动图标，保留 alpha（圆角透明）。
 * 用法：java IconResize.java <源png> <输出res目录>
 * JDK11+ 可直接 java 单文件运行，无需先 javac。
 */
public class IconResize {
    // 档位 -> 边长(dp 48 对应 mdpi 48px，密度倍率放大)
    private static final String[] DENS = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
    private static final int[] SIZES = {48, 72, 96, 144, 192};

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("用法: java IconResize.java <源png> <res目录>");
            return;
        }
        File src = new File(args[0]);
        File resDir = new File(args[1]);
        BufferedImage img = ImageIO.read(src);
        if (img == null) throw new RuntimeException("无法读取图片: " + src);
        System.out.println("源图尺寸: " + img.getWidth() + "x" + img.getHeight()
                + "  alpha=" + img.getColorModel().hasAlpha());

        // 统一转成 ARGB，保留透明
        BufferedImage base = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = base.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();

        for (int i = 0; i < DENS.length; i++) {
            int size = SIZES[i];
            BufferedImage out = scaleQuality(base, size);
            File dir = new File(resDir, "drawable-" + DENS[i]);
            if (!dir.exists()) dir.mkdirs();
            File outFile = new File(dir, "ic_launcher.png");
            ImageIO.write(out, "png", outFile);
            System.out.println("  " + DENS[i] + " " + size + "x" + size
                    + " -> " + outFile + " (" + outFile.length() + " bytes)");
        }
        System.out.println("完成");
    }

    // 渐进式减半缩放，比一次性缩小到目标更锐利
    private static BufferedImage scaleQuality(BufferedImage src, int target) {
        BufferedImage cur = src;
        int w = cur.getWidth(), h = cur.getHeight();
        while (w / 2 >= target && h / 2 >= target) {
            w = Math.max(target, w / 2);
            h = Math.max(target, h / 2);
            cur = scaleStep(cur, w, h);
        }
        return scaleStep(cur, target, target);
    }

    private static BufferedImage scaleStep(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }
}
