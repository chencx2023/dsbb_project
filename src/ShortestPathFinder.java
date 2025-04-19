import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * 智能剪刀最短路径查找器
 * 使用Dijkstra算法计算从起始点到目标点（鼠标位置）的最短路径
 */
public class ShortestPathFinder {
    private CostFunctionMatrix costMatrix;
    private int width;
    private int height;

    // 存储节点的父节点信息，用于重建路径
    private Map<Point, Point> parentMap;

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
        this.parentMap = new HashMap<>();
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
            return new ArrayList<>(); // 返回空路径
        }

        // 清除之前的路径信息
        parentMap.clear();

        // 创建优先队列，按照成本排序
        PriorityQueue<PointWithCost> queue = new PriorityQueue<>(
                Comparator.comparingDouble(PointWithCost::getCost)
        );

        // 存储每个点的累计最小成本
        Map<Point, Double> distanceMap = new HashMap<>();

        // 已处理的点集合
        Set<Point> visited = new HashSet<>();

        // 初始化起点
        Point start = new Point(startX, startY);
        queue.add(new PointWithCost(start, 0));
        distanceMap.put(start, 0.0);

        Point end = new Point(endX, endY);

        // Dijkstra算法主循环
        while (!queue.isEmpty()) {
            // 获取当前成本最小的点
            PointWithCost current = queue.poll();
            Point currentPoint = current.getPoint();

            // 如果到达终点，结束搜索
            if (currentPoint.equals(end)) {
                break;
            }

            // 如果该点已被访问，跳过
            if (visited.contains(currentPoint)) {
                continue;
            }

            // 标记当前点为已访问
            visited.add(currentPoint);

            // 遍历所有相邻的点
            for (int i = 0; i < 8; i++) {
                int nx = currentPoint.x + DX[i];
                int ny = currentPoint.y + DY[i];

                // 检查边界
                if (nx < 0 || nx >= width || ny < 0 || ny >= height) {
                    continue;
                }

                Point neighbor = new Point(nx, ny);

                // 如果已访问过该邻居，跳过
                if (visited.contains(neighbor)) {
                    continue;
                }

                // 计算到邻居的成本
                // 使用欧几里得距离确定对角线成本的权重
                double weight = (i % 2 == 0) ? 1.0 : Math.sqrt(2);
                double edgeCost = costMatrix.getCost(nx, ny) * weight;

                // 计算从起点经由当前点到邻居的总成本
                double newDistance = distanceMap.get(currentPoint) + edgeCost;

                // 如果找到更短的路径，或者是第一次访问该点
                if (!distanceMap.containsKey(neighbor) || newDistance < distanceMap.get(neighbor)) {
                    distanceMap.put(neighbor, newDistance);
                    parentMap.put(neighbor, currentPoint);
                    queue.add(new PointWithCost(neighbor, newDistance));
                }
            }
        }

        // 重建路径
        return reconstructPath(start, end);
    }

    /**
     * 根据父节点信息重建路径
     * @param start 起点
     * @param end 终点
     * @return 路径点列表
     */
    private List<Point> reconstructPath(Point start, Point end) {
        List<Point> path = new ArrayList<>();

        // 如果没有找到路径
        if (!parentMap.containsKey(end) && !end.equals(start)) {
            return path;
        }

        // 从终点回溯到起点
        Point current = end;
        while (current != null && !current.equals(start)) {
            path.add(current);
            current = parentMap.get(current);
        }

        // 添加起点
        path.add(start);

        // 反转路径，使其从起点到终点
        Collections.reverse(path);

        return path;
    }

    /**
     * 内部类：带成本的点
     * 用于优先队列按成本排序
     */
    private static class PointWithCost {
        private Point point;
        private double cost;

        public PointWithCost(Point point, double cost) {
            this.point = point;
            this.cost = cost;
        }

        public Point getPoint() {
            return point;
        }

        public double getCost() {
            return cost;
        }
    }

    /**
     * 获取实时路径（用于鼠标移动时）
     * @param startX 固定起点X坐标
     * @param startY 固定起点Y坐标
     * @param mouseX 当前鼠标X坐标
     * @param mouseY 当前鼠标Y坐标
     * @return 从起点到鼠标位置的路径
     */
    public List<Point> getLiveWirePath(int startX, int startY, int mouseX, int mouseY) {
        return findShortestPath(startX, startY, mouseX, mouseY);
    }

    /**
     * 示例使用方法
     */
    public static void main(String[] args) {
        // 假设我们已经有了成本矩阵
        // CostFunctionMatrix costMatrix = new CostFunctionMatrix(image);

        // 创建路径查找器
        // ShortestPathFinder pathFinder = new ShortestPathFinder(costMatrix);

        // 获取从起点(100, 100)到鼠标位置(200, 150)的路径
        // List<Point> path = pathFinder.findShortestPath(100, 100, 200, 150);

        // 使用路径进行绘制或其他操作
    }
}