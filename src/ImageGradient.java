import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Arrays;
import javax.imageio.ImageIO;

public class ImageGradient {

    public static int[][] convertToGrayscale(String imagePath) throws IOException {
        // 读取图像文件
        BufferedImage image = ImageIO.read(new File(imagePath));
        int width = image.getWidth();
        int height = image.getHeight();

        // 创建灰度矩阵
        int[][] grayscaleMatrix = new int[height][width];

        // 遍历每个像素并计算灰度值
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);

                // 提取RGB通道
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                // 计算灰度值 (使用标准加权公式)
                int gray = (int)(0.299 * r + 0.587 * g + 0.114 * b);
                grayscaleMatrix[y][x] = gray;
            }
        }

        return grayscaleMatrix;
    }
    public static double[][] calculateGradient(int[][] grayscaleMatrix) {
        int height = grayscaleMatrix.length;
        int width = grayscaleMatrix[0].length;

        // 创建梯度矩阵
        double[][] gradientMatrix = new double[height][width];

        // 对内部像素计算梯度（跳过边缘）
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                // Sobel算子 X方向
                int gx = grayscaleMatrix[y-1][x+1] + 2*grayscaleMatrix[y][x+1] + grayscaleMatrix[y+1][x+1]
                        - grayscaleMatrix[y-1][x-1] - 2*grayscaleMatrix[y][x-1] - grayscaleMatrix[y+1][x-1];

                // Sobel算子 Y方向
                int gy = grayscaleMatrix[y-1][x-1] + 2*grayscaleMatrix[y-1][x] + grayscaleMatrix[y-1][x+1]
                        - grayscaleMatrix[y+1][x-1] - 2*grayscaleMatrix[y+1][x] - grayscaleMatrix[y+1][x+1];

                // 计算梯度幅值
                double gradientMagnitude = Math.sqrt(gx*gx + gy*gy);
                gradientMatrix[y][x] = gradientMagnitude;
            }
        }

        return gradientMatrix;
    }
    public static void main(String[] args) {
        try {
            // 图像路径
            String imagePath = "C:\\Users\\ccx\\IdeaProjects\\project\\屏幕截图 2025-04-05 170112.png";

            // 1. 转换为灰度矩阵
            int[][] grayscaleMatrix = convertToGrayscale(imagePath);
            System.out.println("灰度矩阵已生成，尺寸: " + grayscaleMatrix[0].length + "x" + grayscaleMatrix.length);

            // 2. 计算梯度矩阵
            double[][] gradientMatrix = calculateGradient(grayscaleMatrix);
            System.out.printf(Arrays.deepToString(gradientMatrix));


        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
