import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 智能剪刀工具UI类
 * 实现交互式图像分割功能
 */
public class IntelligentScissorsUI extends JFrame {
    private BufferedImage originalImage;
    private BufferedImage displayImage;
    private BufferedImage resultImage;
    private CostFunctionMatrix costMatrix;
    private ShortestPathFinder pathFinder;

    // 画布面板
    private JPanel imagePanel;

    // 状态
    private boolean hasStartPoint = false;
    private Point startPoint = null;
    private Point currentPoint = null;
    private Point firstPoint = null; // 记录第一个点，用于闭合

    // 路径点集合
    private List<Point> currentPath = new ArrayList<>();
    private List<List<Point>> completedPaths = new ArrayList<>();

    // 闭合阈值（像素距离）
    private static final int CLOSURE_THRESHOLD = 10;

    // 导出按钮
    private JButton exportButton;

    // 状态标签
    private JLabel statusLabel;

    private List<Point> lastPath = new ArrayList<>();
    private Timer stabilityTimer;


    /**
     * 构造函数
     */
    public IntelligentScissorsUI() {
        setTitle("智能剪刀工具");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        initUI();
        // 设置窗口初始状态为最大化
        setExtendedState(JFrame.MAXIMIZED_BOTH);
    }
    /**
     * 初始化UI组件
     */
    private void initUI() {
        setLayout(new BorderLayout());

        // 顶部工具栏
        JPanel toolBar = new JPanel();
        JButton openButton = new JButton("打开图像");
        exportButton = new JButton("导出选区");
        exportButton.setEnabled(false);

        openButton.addActionListener(e -> openImage());
        exportButton.addActionListener(e -> exportSelection());

        toolBar.add(openButton);
        toolBar.add(exportButton);

        add(toolBar, BorderLayout.NORTH);

        // 状态栏
        statusLabel = new JLabel("请打开一张图像开始");
        add(statusLabel, BorderLayout.SOUTH);

        // 图像面板
        imagePanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawImage(g);
            }
        };

        imagePanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleMouseClick(e.getX(), e.getY());
            }
        });

        imagePanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                handleMouseMove(e.getX(), e.getY());
            }
        });

        // 添加滚动面板以支持大图像
        JScrollPane scrollPane = new JScrollPane(imagePanel);
        add(scrollPane, BorderLayout.CENTER);
        stabilityTimer = new Timer(600, e -> {
            if (arePathsEqual(currentPath, lastPath)) {
                // 路径稳定时添加至完成路径
                List<Point> stablePath = new ArrayList<>(currentPath);
                completedPaths.add(stablePath);
                drawCompletedPath(stablePath);  // 绘制绿线
                startPoint = currentPoint;
                currentPath.clear(); // 清空当前路径
                lastPath.clear();
                statusLabel.setText("自动添加控制点，继续移动");
                imagePanel.repaint(); // 立即重绘
            } else {
                lastPath = new ArrayList<>(currentPath);
            }
        });
    }

    /**
     * 打开图像文件
     */
    private void openImage() {
        JFileChooser fileChooser = new JFileChooser();
        int result = fileChooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            try {
                // 加载原始图像
                originalImage = ImageIO.read(selectedFile);

                // 创建显示图像（可以在上面绘制）
                displayImage = new BufferedImage(
                        originalImage.getWidth(),
                        originalImage.getHeight(),
                        BufferedImage.TYPE_INT_ARGB
                );

                // 复制原始图像到显示图像
                Graphics2D g2d = displayImage.createGraphics();
                g2d.drawImage(originalImage, 0, 0, null);
                g2d.dispose();

                // 设置图像面板大小
                imagePanel.setPreferredSize(new Dimension(originalImage.getWidth(), originalImage.getHeight()));

                // 计算成本矩阵
                statusLabel.setText("正在计算成本矩阵...");
                SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
                    @Override
                    protected Void doInBackground() {
                        costMatrix = new CostFunctionMatrix(originalImage);
                        pathFinder = new ShortestPathFinder(costMatrix);
                        return null;
                    }

                    @Override
                    protected void done() {
                        statusLabel.setText("请在图像上点击以设置起始点");
                        resetState();
                    }
                };
                worker.execute();

            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "无法打开图像: " + e.getMessage());
            }
        }
    }

    /**
     * 重置所有状态
     */
    private void resetState() {
        hasStartPoint = false;
        startPoint = null;
        currentPoint = null;
        firstPoint = null;
        currentPath.clear();
        completedPaths.clear();
        exportButton.setEnabled(false);

        // 重绘界面
        if (displayImage != null) {
            Graphics2D g2d = displayImage.createGraphics();
            g2d.drawImage(originalImage, 0, 0, null);
            g2d.dispose();
            imagePanel.repaint();
        }
    }

    /**
     * 处理鼠标点击事件
     */
    private void handleMouseClick(int x, int y) {
        if (originalImage == null || costMatrix == null) {
            return;
        }

        // 确保点击在图像范围内
        if (x < 0 || y < 0 || x >= originalImage.getWidth() || y >= originalImage.getHeight()) {
            return;
        }

        if (!hasStartPoint) {
            // 设置第一个起始点
            startPoint = new Point(x, y);
            firstPoint = startPoint; // 记录第一个点
            hasStartPoint = true;
            statusLabel.setText("起始点已设置，移动鼠标以查看路径");
        } else {
            Point clickPoint = new Point(x, y);
            if (isClosedPath(clickPoint)) {
                // 闭合路径逻辑
                List<Point> finalPath = pathFinder.findShortestPath(
                        startPoint.x, startPoint.y, firstPoint.x, firstPoint.y
                );
                completedPaths.add(finalPath);
                drawCompletedPath(finalPath);
                createSelectionMask();
                exportButton.setEnabled(true);
                statusLabel.setText("选区已闭合！可以导出或重新开始");
                hasStartPoint = false;
                startPoint = null;
                currentPoint = null;
                currentPath.clear();
                stabilityTimer.stop(); // 停止自动添加
                imagePanel.repaint();
                return; // 直接返回，避免后续逻辑
            }else {
                // 非闭合逻辑
                if (!currentPath.isEmpty()) {
                    completedPaths.add(new ArrayList<>(currentPath));
                    drawCompletedPath(currentPath);
                }
                startPoint = clickPoint;
                statusLabel.setText("已添加控制点，继续移动鼠标");
            }
        }

        imagePanel.repaint();
    }
    /**
     * 处理鼠标移动事件
     */
    private void handleMouseMove(int x, int y) {
        if (!hasStartPoint || startPoint == null) return;

        Point adjusted = snapToEdge(x, y);
        currentPoint = adjusted;

        // 计算当前路径
        currentPath = pathFinder.findShortestPath(
                startPoint.x, startPoint.y, adjusted.x, adjusted.y
        );

        // 检查是否接近第一个点
        if (firstPoint != null && distance(adjusted, firstPoint) <= CLOSURE_THRESHOLD) {
            statusLabel.setText("点击以闭合选区");
        } else {
            statusLabel.setText("移动鼠标继续绘制路径");
        }

        imagePanel.repaint();
        stabilityTimer.restart();
    }

    /**
     * 计算两点之间的欧几里得距离
     */
    private double distance(Point p1, Point p2) {
        return Math.sqrt(Math.pow(p1.x - p2.x, 2) + Math.pow(p1.y - p2.y, 2));
    }

    /**
     * 绘制已完成的路径到显示图像
     */
    private void drawCompletedPath(List<Point> path) {
        if (path == null || path.isEmpty()) return;

        Graphics2D g2d = displayImage.createGraphics();
        g2d.setColor(new Color(0, 255, 0, 200)); // 半透明绿色
        g2d.setStroke(new BasicStroke(3));

        // 确保路径点坐标在图像范围内
        for (int i = 0; i < path.size() - 1; i++) {
            Point p1 = path.get(i);
            Point p2 = path.get(i + 1);
            if (isWithinImage(p1) && isWithinImage(p2)) {
                g2d.drawLine(p1.x, p1.y, p2.x, p2.y);
            }
        }
        g2d.dispose();
    }

    private boolean isWithinImage(Point p) {
        return p.x >= 0 && p.x < originalImage.getWidth() &&
                p.y >= 0 && p.y < originalImage.getHeight();
    }
    /**
     * 绘制图像和路径
     */
    private void drawImage(Graphics g) {
        if (displayImage == null) {
            return;
        }

        // 绘制主图像（包含已完成的路径）
        g.drawImage(displayImage, 0, 0, null);

        // 绘制当前路径（未完成）
        if (hasStartPoint && currentPath != null && !currentPath.isEmpty()) {
            g.setColor(Color.RED);
            for (int i = 0; i < currentPath.size() - 1; i++) {
                Point p1 = currentPath.get(i);
                Point p2 = currentPath.get(i + 1);
                g.drawLine(p1.x, p1.y, p2.x, p2.y);
            }
        }

        // 绘制控制点
        if (firstPoint != null) {
            g.setColor(Color.BLUE);
            g.fillOval(firstPoint.x - 4, firstPoint.y - 4, 8, 8);
        }

        if (startPoint != null) {
            g.setColor(Color.RED);
            g.fillOval(startPoint.x - 4, startPoint.y - 4, 8, 8);
        }
    }

    /**
     * 创建选区蒙版并提取选中区域
     * 修复版本：确保只提取闭合路径内的图像区域
     */
    private void createSelectionMask() {
        if (originalImage == null || completedPaths.isEmpty()) {
            return;
        }

        // 创建蒙版图像
        BufferedImage mask = new BufferedImage(
                originalImage.getWidth(),
                originalImage.getHeight(),
                BufferedImage.TYPE_BYTE_BINARY
        );

        // 创建完整的路径轮廓
        GeneralPath path = new GeneralPath();

        // 添加所有路径段到总路径
        boolean firstSegment = true;
        for (List<Point> pathSegment : completedPaths) {
            if (pathSegment.isEmpty()) {
                continue;
            }

            Point first = pathSegment.get(0);
            if (firstSegment) {
                path.moveTo(first.x, first.y);
                firstSegment = false;
            }

            for (int i = 1; i < pathSegment.size(); i++) {
                Point p = pathSegment.get(i);
                path.lineTo(p.x, p.y);
            }
        }

        // 闭合路径
        path.closePath();

        // 填充路径区域
        Graphics2D g2d = mask.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fill(path);
        g2d.dispose();

        // 创建结果图像 - 使用带Alpha通道的ARGB类型
        resultImage = new BufferedImage(
                originalImage.getWidth(),
                originalImage.getHeight(),
                BufferedImage.TYPE_INT_ARGB
        );

        // 应用蒙版提取选中区域
        for (int y = 0; y < originalImage.getHeight(); y++) {
            for (int x = 0; x < originalImage.getWidth(); x++) {
                if ((mask.getRGB(x, y) & 0xFF) > 0) { // 白色区域（选中）
                    // 保留原图像中的RGB值，设置Alpha通道为完全不透明
                    int originalRGB = originalImage.getRGB(x, y);
                    int alpha = 0xFF000000; // 完全不透明
                    resultImage.setRGB(x, y, originalRGB | alpha);
                } else {
                    // 设置为完全透明
                    resultImage.setRGB(x, y, 0);
                }
            }
        }

        // 裁剪图像到选区的最小矩形（可选）
        resultImage = cropToSelection(resultImage, mask);
    }

    /**
     * 将图像裁剪到选区的最小矩形
     * @param image 带Alpha通道的图像
     * @param mask 二值蒙版
     * @return 裁剪后的图像
     */
    private BufferedImage cropToSelection(BufferedImage image, BufferedImage mask) {
        // 计算包含选区的最小矩形
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = 0;
        int maxY = 0;

        for (int y = 0; y < mask.getHeight(); y++) {
            for (int x = 0; x < mask.getWidth(); x++) {
                if ((mask.getRGB(x, y) & 0xFF) > 0) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        // 确保找到了有效的边界
        if (minX > maxX || minY > maxY) {
            return image; // 返回原图，无效的选区
        }

        // 计算宽度和高度，添加一些边距
        int padding = 5;
        minX = Math.max(0, minX - padding);
        minY = Math.max(0, minY - padding);
        maxX = Math.min(image.getWidth() - 1, maxX + padding);
        maxY = Math.min(image.getHeight() - 1, maxY + padding);

        int width = maxX - minX + 1;
        int height = maxY - minY + 1;

        // 创建裁剪后的图像
        BufferedImage croppedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        // 复制选区到新图像
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int srcX = x + minX;
                int srcY = y + minY;

                if (srcX >= 0 && srcY >= 0 && srcX < image.getWidth() && srcY < image.getHeight()) {
                    croppedImage.setRGB(x, y, image.getRGB(srcX, srcY));
                }
            }
        }

        return croppedImage;
    }

    /**
     * 导出选中的区域
     */
    private void exportSelection() {
        if (resultImage == null) {
            JOptionPane.showMessageDialog(this, "没有可导出的选区");
            return;
        }

        // 步骤1：先弹出预览窗口让用户确认
        JPanel previewPanel = new JPanel(new BorderLayout());
        previewPanel.add(new JLabel(new ImageIcon(resultImage)), BorderLayout.CENTER);

        int confirm = JOptionPane.showConfirmDialog(
                this,
                previewPanel,
                "保存前预览 - 确认是否继续",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        // 用户取消操作
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        // 步骤2：用户确认后立即弹出路径选择对话框
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File("selection.png"));
        int result = fileChooser.showSaveDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            String filePath = file.getPath();
            if (!filePath.toLowerCase().endsWith(".png")) {
                file = new File(filePath + ".png");
            }

            try {
                ImageIO.write(resultImage, "PNG", file);
                JOptionPane.showMessageDialog(
                        this,
                        "选区已成功导出到: " + file.getPath(),
                        "导出成功",
                        JOptionPane.INFORMATION_MESSAGE
                );
            } catch (IOException e) {
                JOptionPane.showMessageDialog(
                        this,
                        "导出失败: " + e.getMessage(),
                        "错误",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }


    private void showResultImage() {
        if (resultImage == null) return;

        // 创建新窗口
        JFrame resultFrame = new JFrame("选区预览 - 智能剪刀工具");
        resultFrame.setSize(800, 600);
        resultFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // 创建图像面板
        JPanel resultPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (resultImage != null) {
                    g.drawImage(resultImage, 0, 0, null);
                }
            }
        };

        // 设置面板尺寸
        resultPanel.setPreferredSize(new Dimension(
                resultImage.getWidth(),
                resultImage.getHeight()
        ));

        // 添加滚动面板
        JScrollPane scrollPane = new JScrollPane(resultPanel);
        resultFrame.add(scrollPane, BorderLayout.CENTER);

        // 添加底部工具栏
        JPanel bottomPanel = new JPanel();
        JButton saveButton = new JButton("另存为");
        saveButton.addActionListener(e -> exportSelection());

        bottomPanel.add(saveButton);
        resultFrame.add(bottomPanel, BorderLayout.SOUTH);

        // 显示窗口
        resultFrame.pack();
        resultFrame.setLocationRelativeTo(null);
        resultFrame.setVisible(true);
    }


    private Point snapToEdge(int x, int y) {
        double maxGrad = -1;
        Point snapped = new Point(x, y);
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                if (nx < 0 || ny < 0 || nx >= originalImage.getWidth() || ny >= originalImage.getHeight())
                    continue;
                double grad = costMatrix.getGradientMagnitude(ny, nx);
                if (grad > maxGrad) { // 仅判断梯度幅值
                    maxGrad = grad;
                    snapped.setLocation(nx, ny);
                }
            }
        }
        return snapped;
    }

    private boolean arePathsEqual(List<Point> path1, List<Point> path2) {
        if (path1.size() != path2.size()) return false;
        for (int i = 0; i < path1.size(); i++) {
            Point p1 = path1.get(i);
            Point p2 = path2.get(i);
            if (p1.x != p2.x || p1.y != p2.y) return false;
        }
        return true;
    }

    private boolean isClosedPath(Point currentPoint) {
        if (firstPoint == null || currentPoint == null) return false;
        return distance(currentPoint, firstPoint) <= CLOSURE_THRESHOLD;
    }



    /**
     * 主函数
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            IntelligentScissorsUI app = new IntelligentScissorsUI();
            app.setVisible(true);
        });
    }
}