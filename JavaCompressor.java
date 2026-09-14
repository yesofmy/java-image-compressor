import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.awt.Color;

public class JavaCompressor {
    private static final int BLOCK_SIZE = 8;
    public static void main(String[] args) {
        try {
            File inputFile = new File("D:\\programs\\Java\\input.png");
            BufferedImage image = ImageIO.read(inputFile);
            System.out.println("Завантажено зображення: " + inputFile.getName());


            long startJPEG = System.currentTimeMillis();
            double[][] gray = toGrayMatrix(image);
            double[][] compressed = jpegCompress(gray);
            BufferedImage jpegOutput = jpegDecompress(compressed);
            ImageIO.write(jpegOutput, "jpg", new File("D:\\programs\\Java\\jpeg_output.jpg"));
            long endJPEG = System.currentTimeMillis();


            long startPNG = System.currentTimeMillis();
            byte[] encoded = rleEncode(image);
            BufferedImage decoded = rleDecode(encoded, image.getWidth(), image.getHeight());
            ImageIO.write(decoded, "png", new File("D:\\programs\\Java\\png_output.png"));
            long endPNG = System.currentTimeMillis();

            double psnrJPEG = psnr(gray, toGrayMatrix(jpegOutput));
            double psnrPNG = psnr(toGrayMatrix(image), toGrayMatrix(decoded));
            long sizeJPEG = new File("D:\\programs\\Java\\jpeg_output.jpg").length();
            long sizePNG = new File("D:\\programs\\Java\\png_output.png").length();

            System.out.println("\nПорівняння результатів:");
            System.out.printf("%-15s %-15s %-15s %-15s\n", "Метод", "Розмір (байт)", "PSNR", "Час (мс)");
            System.out.println("-------------------------------------------------------------");
            System.out.printf("%-15s %-15d %-15.2f %-15d\n", "JPEG", sizeJPEG, psnrJPEG, (endJPEG - startJPEG));
            System.out.printf("%-15s %-15d %-15.2f %-15d\n", "PNG (RLE)", sizePNG, psnrPNG, (endPNG - startPNG));

            System.out.println("\nФайли збережено як:");
            System.out.println("jpeg_output.jpg");
            System.out.println("png_output.png");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }




    private static double[][] jpegCompress(double[][] matrix) {
        int h = matrix.length, w = matrix[0].length;
        double[][] out = new double[h][w];
        double[][] Q = {
                {16,11,10,16,24,40,51,61},
                {12,12,14,19,26,58,60,55},
                {14,13,16,24,40,57,69,56},
                {14,17,22,29,51,87,80,62},
                {18,22,37,56,68,109,103,77},
                {24,35,55,64,81,104,113,92},
                {49,64,78,87,103,121,120,101},
                {72,92,95,98,112,100,103,99}
        };

        for (int i = 0; i < h; i += BLOCK_SIZE)
            for (int j = 0; j < w; j += BLOCK_SIZE) {
                double[][] block = getBlock(matrix, i, j);
                double[][] dct = dct(block);
                quantize(dct, Q);
                setBlock(out, dct, i, j);
            }
        return out;
    }

    private static BufferedImage jpegDecompress(double[][] data) {
        int h = data.length, w = data[0].length;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        double[][] Q = {
                {16,11,10,16,24,40,51,61},
                {12,12,14,19,26,58,60,55},
                {14,13,16,24,40,57,69,56},
                {14,17,22,29,51,87,80,62},
                {18,22,37,56,68,109,103,77},
                {24,35,55,64,81,104,113,92},
                {49,64,78,87,103,121,120,101},
                {72,92,95,98,112,100,103,99}
        };

        for (int i = 0; i < h; i += BLOCK_SIZE)
            for (int j = 0; j < w; j += BLOCK_SIZE) {
                double[][] block = getBlock(data, i, j);
                dequantize(block, Q);
                double[][] idct = idct(block);
                setBlock(data, idct, i, j);
            }

        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int g = (int) Math.round(data[y][x] + 128);
                g = Math.max(0, Math.min(255, g));
                int rgb = (g << 16) | (g << 8) | g;
                img.setRGB(x, y, rgb);
            }

        return img;
    }

    private static double[][] getBlock(double[][] src, int y, int x) {
        double[][] block = new double[BLOCK_SIZE][BLOCK_SIZE];
        for (int i = 0; i < BLOCK_SIZE; i++)
            for (int j = 0; j < BLOCK_SIZE; j++)
                if (y + i < src.length && x + j < src[0].length)
                    block[i][j] = src[y + i][x + j];
        return block;
    }

    private static void setBlock(double[][] dst, double[][] block, int y, int x) {
        for (int i = 0; i < BLOCK_SIZE; i++)
            for (int j = 0; j < BLOCK_SIZE; j++)
                if (y + i < dst.length && x + j < dst[0].length)
                    dst[y + i][x + j] = block[i][j];
    }

    private static double[][] dct(double[][] block) {
        double[][] res = new double[BLOCK_SIZE][BLOCK_SIZE];
        for (int u = 0; u < BLOCK_SIZE; u++)
            for (int v = 0; v < BLOCK_SIZE; v++) {
                double sum = 0.0;
                for (int x = 0; x < BLOCK_SIZE; x++)
                    for (int y = 0; y < BLOCK_SIZE; y++)
                        sum += block[x][y] *
                                Math.cos((2*x+1)*u*Math.PI/16) *
                                Math.cos((2*y+1)*v*Math.PI/16);
                double cu = (u == 0) ? 1/Math.sqrt(2) : 1;
                double cv = (v == 0) ? 1/Math.sqrt(2) : 1;
                res[u][v] = 0.25 * cu * cv * sum;
            }
        return res;
    }

    private static double[][] idct(double[][] block) {
        double[][] res = new double[BLOCK_SIZE][BLOCK_SIZE];
        for (int x = 0; x < BLOCK_SIZE; x++)
            for (int y = 0; y < BLOCK_SIZE; y++) {
                double sum = 0.0;
                for (int u = 0; u < BLOCK_SIZE; u++)
                    for (int v = 0; v < BLOCK_SIZE; v++) {
                        double cu = (u == 0) ? 1/Math.sqrt(2) : 1;
                        double cv = (v == 0) ? 1/Math.sqrt(2) : 1;
                        sum += cu * cv * block[u][v] *
                                Math.cos((2*x+1)*u*Math.PI/16) *
                                Math.cos((2*y+1)*v*Math.PI/16);
                    }
                res[x][y] = 0.25 * sum;
            }
        return res;
    }

    private static void quantize(double[][] block, double[][] Q) {
        for (int i = 0; i < BLOCK_SIZE; i++)
            for (int j = 0; j < BLOCK_SIZE; j++)
                block[i][j] = Math.round(block[i][j] / Q[i][j]);
    }

    private static void dequantize(double[][] block, double[][] Q) {
        for (int i = 0; i < BLOCK_SIZE; i++)
            for (int j = 0; j < BLOCK_SIZE; j++)
                block[i][j] *= Q[i][j];
    }




    private static byte[] rleEncode(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int y = 0; y < image.getHeight(); y++) {
            int prev = image.getRGB(0, y);
            int count = 1;
            for (int x = 1; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                if (rgb == prev && count < 255)
                    count++;
                else {
                    out.write(count);
                    out.write((prev >> 16) & 0xFF);
                    out.write((prev >> 8) & 0xFF);
                    out.write(prev & 0xFF);
                    prev = rgb;
                    count = 1;
                }
            }
            out.write(count);
            out.write((prev >> 16) & 0xFF);
            out.write((prev >> 8) & 0xFF);
            out.write(prev & 0xFF);
        }
        return out.toByteArray();
    }

    private static BufferedImage rleDecode(byte[] data, int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int x = 0, y = 0;
        for (int i = 0; i < data.length; i += 4) {
            int count = data[i] & 0xFF;
            int r = data[i + 1] & 0xFF;
            int g = data[i + 2] & 0xFF;
            int b = data[i + 3] & 0xFF;
            int rgb = (r << 16) | (g << 8) | b;
            for (int k = 0; k < count; k++) {
                img.setRGB(x, y, rgb);
                x++;
                if (x >= width) {
                    x = 0;
                    y++;
                    if (y >= height) return img;
                }
            }
        }
        return img;
    }




    private static double[][] toGrayMatrix(BufferedImage img) {
        int h = img.getHeight(), w = img.getWidth();
        double[][] gray = new double[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                gray[y][x] = 0.299*r + 0.587*g + 0.114*b - 128;
            }
        return gray;
    }

    private static double psnr(double[][] orig, double[][] rec) {
        double mse = 0.0;
        int h = orig.length, w = orig[0].length;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double diff = orig[y][x] - rec[y][x];
                mse += diff * diff;
            }
        mse /= (h * w);
        if (mse == 0) return 100;
        return 10 * Math.log10((255 * 255) / mse);
    }
}
