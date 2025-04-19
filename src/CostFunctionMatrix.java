import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.File;
import java.util.Arrays;

public class CostFunctionMatrix {
    private BufferedImage image;
    private int width;
    private int height;
    private double[][] costMatrix;
    private double[][] gradientMagnitude;
    private double[][] gradientX;
    private double[][] gradientY;
    private double[][] laplacian;

    // 调整以下权重可以改变边缘检测的敏感度
    private static final double LAPLACIAN_WEIGHT = 0.43;
    private static final double GRADIENT_MAG_WEIGHT = 0.43;
    private static final double GRADIENT_DIR_WEIGHT = 0.14;

    public CostFunctionMatrix(BufferedImage image) {
        this.image = image;
        this.width = image.getWidth();
        this.height = image.getHeight();
        this.gradientX = new double[height][width];
        this.gradientY = new double[height][width];
        this.gradientMagnitude = new double[height][width];
        this.laplacian = new double[height][width];
        this.costMatrix = new double[height][width];

        // 计算所有中间图像
        computeGradients();
        computeGradientMagnitude();
        computeLaplacian();
        computeCostMatrix();
    }

    // 获取像素点RGB或灰度值
    private double[] getPixelValue(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return new double[]{0, 0, 0};
        }

        Raster raster = image.getRaster();
        int numBands = raster.getNumBands();
        double[] pixelValue;

        if (numBands >= 3) {
            // RGB图像
            pixelValue = new double[3];
            for (int b = 0; b < 3; b++) {
                pixelValue[b] = raster.getSample(x, y, b);
            }
        } else {
            // 灰度图像
            pixelValue = new double[1];
            pixelValue[0] = raster.getSample(x, y, 0);
        }

        return pixelValue;
    }

    // 将RGB值转换为灰度
    private double rgbToGray(double[] rgb) {
        if (rgb.length == 1) {
            return rgb[0]; // 已经是灰度
        }
        // 使用标准的RGB到灰度转换公式
        return 0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2];
    }

    // 计算x和y方向的梯度
    private void computeGradients() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // 使用Sobel算子计算梯度
                double gx = 0;
                double gy = 0;

                // 3x3 Sobel算子
                for (int j = -1; j <= 1; j++) {
                    for (int i = -1; i <= 1; i++) {
                        double[] pixel = getPixelValue(x + i, y + j);
                        double grayValue = rgbToGray(pixel);

                        // Sobel x方向
                        int sobelX = i * (2 - Math.abs(j));
                        // Sobel y方向
                        int sobelY = j * (2 - Math.abs(i));

                        gx += sobelX * grayValue;
                        gy += sobelY * grayValue;
                    }
                }

                gradientX[y][x] = gx;
                gradientY[y][x] = gy;
            }
        }
    }

    // 计算梯度幅值
    private void computeGradientMagnitude() {
        double maxMagnitude = 0;

        // 计算梯度幅值
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double gx = gradientX[y][x];
                double gy = gradientY[y][x];
                double magnitude = Math.sqrt(gx * gx + gy * gy);
                gradientMagnitude[y][x] = magnitude;

                // 跟踪最大幅值用于后续归一化
                if (magnitude > maxMagnitude) {
                    maxMagnitude = magnitude;
                }
            }
        }

        // 归一化梯度幅值到[0,1]区间
        if (maxMagnitude > 0) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    gradientMagnitude[y][x] /= maxMagnitude;
                }
            }
        }
    }

    // 计算拉普拉斯零交叉
    private void computeLaplacian() {
        // 首先计算拉普拉斯算子结果（二阶导数）
        double[][] lap = new double[height][width];
        double maxLap = 0;

        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                // 使用标准拉普拉斯核 [0,1,0; 1,-4,1; 0,1,0]
                double[] center = getPixelValue(x, y);
                double[] top = getPixelValue(x, y - 1);
                double[] bottom = getPixelValue(x, y + 1);
                double[] left = getPixelValue(x - 1, y);
                double[] right = getPixelValue(x + 1, y);

                double centerGray = rgbToGray(center);
                double topGray = rgbToGray(top);
                double bottomGray = rgbToGray(bottom);
                double leftGray = rgbToGray(left);
                double rightGray = rgbToGray(right);

                double lapResult = topGray + bottomGray + leftGray + rightGray - 4 * centerGray;
                lap[y][x] = Math.abs(lapResult);

                if (lap[y][x] > maxLap) {
                    maxLap = lap[y][x];
                }
            }
        }

        // 归一化拉普拉斯值
        if (maxLap > 0) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    lap[y][x] /= maxLap;
                }
            }
        }

        // 检测零交叉
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                // 检查周围的8个邻居是否有符号变化
                boolean hasZeroCrossing = false;
                double centerVal = lap[y][x];

                for (int j = -1; j <= 1; j++) {
                    for (int i = -1; i <= 1; i++) {
                        if (i == 0 && j == 0) continue;

                        int nx = x + i;
                        int ny = y + j;

                        if (nx >= 0 && ny >= 0 && nx < width && ny < height) {
                            // 简化的零交叉检测：相邻像素值差异大
                            if (Math.abs(lap[ny][nx] - centerVal) > 0.5) {
                                hasZeroCrossing = true;
                                break;
                            }
                        }
                    }
                    if (hasZeroCrossing) break;
                }

                // 零交叉点应该有较低的成本
                laplacian[y][x] = hasZeroCrossing ? 0 : 1;
            }
        }
    }

    // 计算成本矩阵
    private void computeCostMatrix() {
        // 初始化所有成本为最大值
        for (double[] row : costMatrix) {
            Arrays.fill(row, 1.0);
        }

        // 计算每个像素的成本函数
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                // 计算像素成本基于三个因素的加权和
                double fZ = laplacian[y][x];      // 拉普拉斯零交叉
                double fG = 1 - gradientMagnitude[y][x]; // 梯度幅值，取反使边缘有低成本

                // 计算梯度方向成本 - 在8个方向上评估
                double[] dirCosts = new double[8];
                int idx = 0;

                // 考虑八个邻居的梯度方向一致性
                for (int j = -1; j <= 1; j++) {
                    for (int i = -1; i <= 1; i++) {
                        if (i == 0 && j == 0) continue;

                        int nx = x + i;
                        int ny = y + j;

                        if (nx >= 0 && ny >= 0 && nx < width && ny < height) {
                            // 计算方向向量
                            double dx = i;
                            double dy = j;
                            double len = Math.sqrt(dx * dx + dy * dy);
                            dx /= len;
                            dy /= len;

                            // 获取当前点和邻居的梯度向量
                            double gxC = gradientX[y][x];
                            double gyC = gradientY[y][x];
                            double gxN = gradientX[ny][nx];
                            double gyN = gradientY[ny][nx];

                            // 梯度单位化
                            double gLen = Math.sqrt(gxC * gxC + gyC * gyC);
                            if (gLen > 0) {
                                gxC /= gLen;
                                gyC /= gLen;
                            }

                            gLen = Math.sqrt(gxN * gxN + gyN * gyN);
                            if (gLen > 0) {
                                gxN /= gLen;
                                gyN /= gLen;
                            }

                            // 计算链接方向与梯度方向的垂直度
                            double dp1 = Math.abs(dx * gyC - dy * gxC);
                            double dp2 = Math.abs(dx * gyN - dy * gxN);

                            // 梯度方向成本（较低表示更好）
                            dirCosts[idx] = Math.min(dp1, dp2);
                            idx++;
                        } else {
                            dirCosts[idx] = 1.0; // 边界外设为最大成本
                            idx++;
                        }
                    }
                }

                // 取平均方向成本
                double fD = 0;
                for (double cost : dirCosts) {
                    fD += cost;
                }
                fD /= dirCosts.length;

                // 组合三个因素计算最终成本
                double cost = LAPLACIAN_WEIGHT * fZ +
                        GRADIENT_MAG_WEIGHT * fG +
                        GRADIENT_DIR_WEIGHT * fD;

                // 确保成本在[0,1]范围内
                cost = Math.max(0, Math.min(1, cost));
                costMatrix[y][x] = cost;
            }
        }
    }

    // 获取指定坐标的成本
    public double getCost(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return Double.MAX_VALUE; // 边界外返回无穷大成本
        }
        return costMatrix[y][x];
    }

    // 获取完整的成本矩阵
    public double[][] getCostMatrix() {
        return costMatrix;
    }

    // 获取成本矩阵用于可视化（将成本转换为0-255的整数值）
    public int[][] getVisualizedCostMatrix() {
        int[][] visualized = new int[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                visualized[y][x] = (int)(costMatrix[y][x] * 255);
            }
        }
        return visualized;
    }

    // 示例使用方法
    public static void main(String[] args) {
        // 加载图像并计算成本矩阵
        BufferedImage image = null;
        try {
//             加载图像代码
             image = ImageIO.read(new File("C:\\Users\\ccx\\IdeaProjects\\project\\屏幕截图 2025-04-05 170112.png"));

            // 创建成本矩阵
            CostFunctionMatrix costFunction = new CostFunctionMatrix(image);

            // 输出一些示例成本值
            System.out.println("成本矩阵计算完成");
            System.out.println("中心点成本: " +
                    costFunction.getCost(image.getWidth()/2, image.getHeight()/2));
            System.out.println("成本矩阵："+ Arrays.deepToString(costFunction.getCostMatrix()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}