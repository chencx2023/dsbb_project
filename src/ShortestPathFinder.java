import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * 智能剪刀最短路径查找器
 * 使用Dijkstra算法计算从起始点到目标点（鼠标位置）的最短路径
 */
public class ShortestPathFinder {
    private CostFunctionMatrix costMatrix;
    private int width;
    private int height;

    // 使用二维数组存储节点信息
    private Point[][] parent;
    private double[][] distance;
    private boolean[][] visited;

    // 邻居方向：上、右上、右、右下、下、左下、左、左上（顺时针）
    private static final int[] DX = {0, 1, 1, 1, 0, -1, -1, -1};
    private static final int[] DY = {-1, -1, 0, 1, 1, 1, 0, -1};

    /**
     * 构造函数
     * @param costMatrix 成本矩阵对象
     */
    public ShortestPathFinder(CostFunctionMatrix costMatrix) {
        this.costMatrix = costMatrix;
        this.width = costMatrix.getCostMatrix()[0].length;
        this.height = costMatrix.getCostMatrix().length;
    }

    /**
     * 使用Dijkstra算法计算从起点到终点的最短路径
     * @param startX 起点X坐标
     * @param startY 起点Y坐标
     * @param endX 终点X坐标（鼠标位置）
     * @param endY 终点Y坐标（鼠标位置）
     * @return 包含路径点的列表，从起点到终点
     */
    public List<Point> findShortestPath(int startX, int startY, int endX, int endY) {
        // 检查起点和终点的有效性
        if (startX < 0 || startX >= width || startY < 0 || startY >= height ||
                endX < 0 || endX >= width || endY < 0 || endY >= height) {
            return new ArrayList<>();
        }

        // 初始化数据结构
        parent = new Point[width][height];
        distance = new double[width][height];
        visited = new boolean[width][height];

        // 将所有距离初始化为无穷大
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                distance[x][y] = Double.POSITIVE_INFINITY;
                visited[x][y] = false;
                parent[x][y] = null;
            }
        }

        // 创建优先队列，按照成本排序
        PriorityQueue<PointWithCost> queue = new PriorityQueue<>(
                Comparator.comparingDouble(PointWithCost::getCost)
        );

        // 初始化起点
        Point start = new Point(startX, startY);
        distance[startX][startY] = 0.0;
        queue.add(new PointWithCost(start, 0.0));

        Point end = new Point(endX, endY);

        // Dijkstra算法主循环
        while (!queue.isEmpty()) {
            PointWithCost current = queue.poll();
            Point p = current.getPoint();
            int cx = p.x;
            int cy = p.y;

            // 如果已访问过，跳过
            if (visited[cx][cy]) continue;
            // 标记已访问
            visited[cx][cy] = true;

            // 如果到达终点，结束搜索
            if (p.equals(end)) break;

            // 遍历相邻点
            for (int i = 0; i < 8; i++) {
                int nx = cx + DX[i];
                int ny = cy + DY[i];
                if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;

                if (visited[nx][ny]) continue;

                // 计算权重
                double weight = (i % 2 == 0) ? 1.0 : Math.sqrt(2);
                double edgeCost = costMatrix.getCost(nx, ny) * weight;
                double newDist = distance[cx][cy] + edgeCost;

                // 如果发现更短路径
                if (newDist < distance[nx][ny]) {
                    distance[nx][ny] = newDist;
                    parent[nx][ny] = p;
                    queue.add(new PointWithCost(new Point(nx, ny), newDist));
                }
            }
        }

        return reconstructPath(start, end);
    }

    /**
     * 根据父节点信息重建路径
     */
    private List<Point> reconstructPath(Point start, Point end) {
        List<Point> path = new ArrayList<>();
        int ex = end.x;
        int ey = end.y;

        // 如果无路径
        if (parent[ex][ey] == null && !end.equals(start)) {
            return path;
        }

        Point cur = end;
        while (cur != null && !cur.equals(start)) {
            path.add(cur);
            Point prev = parent[cur.x][cur.y];
            cur = prev;
        }
        path.add(start);
        Collections.reverse(path);
        return path;
    }

    /**
     * 获取实时路径（用于鼠标移动时）
     */
    public List<Point> getLiveWirePath(int startX, int startY, int mouseX, int mouseY) {
        return findShortestPath(startX, startY, mouseX, mouseY);
    }

    private static class PointWithCost {
        private Point point;
        private double cost;
        public PointWithCost(Point point, double cost) {
            this.point = point;
            this.cost = cost;
        }
        public Point getPoint() { return point; }
        public double getCost() { return cost; }
    }

    /**
     * 示例使用方法
     */
    public static void main(String[] args) {
        // CostFunctionMatrix costMatrix = new CostFunctionMatrix(image);
        // ShortestPathFinder finder = new ShortestPathFinder(costMatrix);
        // List<Point> path = finder.findShortestPath(100, 100, 200, 150);
    }
}
