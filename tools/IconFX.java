import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 给原版扁平图标加「一点点」立体效果：
 *  1) 背景垂直渐变（上微亮 / 下微暗）+ 顶部高光带 + 边缘极淡暗角
 *  2) 白色运算符下方柔和投影（符号浮起感）
 *  3) 符号本体保持纯白，仅极轻微上下明暗
 * 圆角与透明角完全保留。
 * 用法: java IconFX.java <输入高清png> <输出目录>
 */
public class IconFX {
    static int clamp(double v) { return Math.max(0, Math.min(255, (int) Math.round(v))); }

    public static void main(String[] args) throws Exception {
        File src = new File(args[0]);
        File outDir = new File(args[1]);
        outDir.mkdirs();

        BufferedImage img = ImageIO.read(src);
        int W = img.getWidth(), H = img.getHeight();
        int[] px = img.getRGB(0, 0, W, H, null, 0, W);

        boolean[] valid = new boolean[W * H];
        boolean[] isSym = new boolean[W * H];
        for (int i = 0; i < px.length; i++) {
            int a = (px[i] >>> 24) & 0xff;
            if (a < 10) continue;              // 透明角
            valid[i] = true;
            int r = (px[i] >> 16) & 0xff, g = (px[i] >> 8) & 0xff, b = px[i] & 0xff;
            if (r > 230 && g > 230 && b > 230) isSym[i] = true; // 白色符号
        }

        // 由符号像素向下偏移生成投影遮罩
        int off = Math.max(3, H / 90);
        float[] sh = new float[W * H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int i = y * W + x;
                if (!isSym[i]) continue;
                int sy = Math.min(H - 1, y + off);
                sh[sy * W + x] += 0.30f;
                if (x > 0) sh[sy * W + x - 1] += 0.10f;
                if (x < W - 1) sh[sy * W + x + 1] += 0.10f;
                if (sy < H - 1) sh[(sy + 1) * W + x] += 0.09f;
            }
        }
        // 3x3 均值模糊两遍，让投影柔和
        sh = blur(sh, W, H);
        sh = blur(sh, W, H);

        // 合成 512 主图
        int[] out = new int[W * H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int i = y * W + x;
                if (!valid[i]) { out[i] = px[i]; continue; }  // 保留透明角
                int origAlpha = px[i] & 0xFF000000;            // 保留抗锯齿半透明边缘
                float t = (float) y / H;
                if (isSym[i]) {
                    int v = clamp(255 - 8 * t);               // 符号极轻微下暗
                    out[i] = origAlpha | (v << 16) | (v << 8) | v;
                } else {
                    // 背景渐变：顶部 +6%，底部 -7%
                    float f = 1.06f - 0.13f * t;
                    // 边缘极淡暗角（径向）
                    double dx = (x - W / 2.0) / (W / 2.0), dy = (y - H / 2.0) / (H / 2.0);
                    double rad = Math.sqrt(dx * dx + dy * dy);
                    f -= Math.max(0, rad - 0.72) * 0.11f;
                    int r = clamp(74 * f), g = clamp(155 * f), b = clamp(114 * f); // #4A9B72
                    // 顶部高光带（淡白，前 15% 高度）
                    if (t < 0.15f) {
                        float hi = (1 - t / 0.15f) * 0.16f;
                        r = clamp(r + (255 - r) * hi);
                        g = clamp(g + (255 - g) * hi);
                        b = clamp(b + (255 - b) * hi);
                    }
                    // 符号投影压暗背景
                    float s = Math.min(0.45f, sh[i]);
                    if (s > 0) { r = clamp(r * (1 - s)); g = clamp(g * (1 - s)); b = clamp(b * (1 - s)); }
                    out[i] = origAlpha | (r << 16) | (g << 8) | b;
                }
            }
        }
        BufferedImage master = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        master.setRGB(0, 0, W, H, out, 0, W);
        File masterFile = new File(outDir, "master_3d.png");
        ImageIO.write(master, "png", masterFile);
        System.out.println("主图已生成: " + masterFile.getAbsolutePath());

        // 缩放到各密度档（保持项目现有尺寸）
        Object[][] sizes = { {192, "mdpi"}, {256, "hdpi"}, {256, "xhdpi"}, {384, "xxhdpi"}, {512, "xxxhdpi"} };
        for (Object[] sz : sizes) {
            int dim = (Integer) sz[0];
            BufferedImage scaled = new BufferedImage(dim, dim, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = scaled.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.drawImage(master, 0, 0, dim, dim, null);
            g2.dispose();
            File f = new File(outDir, "ic_" + sz[1] + "_" + dim + ".png");
            ImageIO.write(scaled, "png", f);
            System.out.println("  " + sz[1] + " " + dim + "x" + dim + " -> " + f.getName());
        }
    }

    static float[] blur(float[] src, int w, int h) {
        float[] dst = new float[src.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float sum = 0; int n = 0;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx, ny = y + dy;
                    if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                    sum += src[ny * w + nx]; n++;
                }
                dst[y * w + x] = sum / n;
            }
        }
        return dst;
    }
}
