package movieticketreservation.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

/**
 * Generates a QR-style image from arbitrary text.
 * This is a visual stand-in; integrate a full QR implementation for production scanning.
 */
public final class QrCodeGenerator {

    private QrCodeGenerator() {}

    public static Image generateImage(String data, int moduleSize, int quietZoneModules) {
        if (data == null) throw new IllegalArgumentException("Data must not be null.");
        if (moduleSize <= 0) moduleSize = 8;
        if (quietZoneModules < 0) quietZoneModules = 4;

        int modules = 33; // visual density similar to version 4
        boolean[][] grid = buildPattern(data, modules);
        int dimension = (modules + quietZoneModules * 2) * moduleSize;
        WritableImage image = new WritableImage(dimension, dimension);
        PixelWriter writer = image.getPixelWriter();
        for (int y = 0; y < dimension; y++) {
            for (int x = 0; x < dimension; x++) {
                int gx = x / moduleSize - quietZoneModules;
                int gy = y / moduleSize - quietZoneModules;
                boolean dark = (gx >= 0 && gx < modules && gy >= 0 && gy < modules) && grid[gy][gx];
                writer.setArgb(x, y, dark ? 0xFF000000 : 0xFFFFFFFF);
            }
        }
        return image;
    }

    private static boolean[][] buildPattern(String data, int modules) {
        boolean[][] grid = new boolean[modules][modules];
        drawFinder(grid, 0, 0);
        drawFinder(grid, modules - 7, 0);
        drawFinder(grid, 0, modules - 7);

        Random random = new Random(hashSeed(data));
        for (int y = 0; y < modules; y++) {
            for (int x = 0; x < modules; x++) {
                if (isFinderArea(modules, x, y)) continue;
                grid[y][x] = random.nextBoolean();
            }
        }
        return grid;
    }

    private static void drawFinder(boolean[][] grid, int startX, int startY) {
        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < 7; x++) {
                boolean dark = x == 0 || y == 0 || x == 6 || y == 6 || (x >= 2 && x <= 4 && y >= 2 && y <= 4);
                grid[startY + y][startX + x] = dark;
            }
        }
    }

    private static boolean isFinderArea(int modules, int x, int y) {
        boolean topLeft = x < 7 && y < 7;
        boolean topRight = x >= modules - 7 && y < 7;
        boolean bottomLeft = x < 7 && y >= modules - 7;
        return topLeft || topRight || bottomLeft;
    }

    private static long hashSeed(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data.getBytes(StandardCharsets.UTF_8));
            long seed = 0;
            for (int i = 0; i < Math.min(8, digest.length); i++) {
                seed = (seed << 8) | (digest[i] & 0xFFL);
            }
            return seed;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
