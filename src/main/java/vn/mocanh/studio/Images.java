package vn.mocanh.studio;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;

public final class Images {
  public static byte[] normalize(
      byte[] data, int maxPixels, int minSize, String format, int maxEdge) {
    return normalize(data, maxPixels, minSize, format, maxEdge, false);
  }

  public static byte[] normalize(
      byte[] data, int maxPixels, int minSize, String format, int maxEdge, boolean preserveAlpha) {
    try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
      var readers = ImageIO.getImageReaders(in);
      if (!readers.hasNext()) throw new IOException();
      ImageReader r = readers.next();
      BufferedImage source;
      try {
        r.setInput(in);
        int w = r.getWidth(0), h = r.getHeight(0);
        if ((long) w * h > maxPixels || Math.min(w, h) < minSize) throw new IOException();
        source = r.read(0);
      } finally {
        r.dispose();
      }
      int w = source.getWidth(), h = source.getHeight();
      if (maxEdge > 0 && Math.max(w, h) > maxEdge) {
        double scale = (double) maxEdge / Math.max(w, h);
        w = Math.max(1, (int) (w * scale));
        h = Math.max(1, (int) (h * scale));
      }
      BufferedImage image =
          new BufferedImage(
              w, h, preserveAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
      Graphics2D g = image.createGraphics();
      g.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      g.drawImage(source, 0, 0, w, h, null);
      g.dispose();
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(image, format, out);
      return out.toByteArray();
    } catch (Exception e) {
      throw new ApiError(422, "Ảnh không hợp lệ hoặc quá lớn.");
    }
  }

  public static byte[] demo() {
    try {
      BufferedImage im = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = im.createGraphics();
      g.setColor(new Color(249, 245, 237));
      g.fillRect(0, 0, 1024, 1024);
      g.setColor(new Color(120, 150, 125));
      g.setStroke(new BasicStroke(9));
      for (int i = 0; i < 15; i++) g.drawLine(512, 840, 290 + i * 32, 300 + (i % 3) * 80);
      for (int i = 0; i < 15; i++) {
        g.setColor(i % 3 == 0 ? new Color(244, 233, 223) : new Color(216, 144, 164));
        g.fillOval(250 + i * 32, 220 + (i % 3) * 80, 95, 95);
      }
      g.dispose();
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(im, "png", out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
