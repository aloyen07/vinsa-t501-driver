package ru.aloyenz.t501.driver.window;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.LinkedBlockingQueue;

public class RealTimeGraph extends JPanel {

    private final LinkedBlockingQueue<Integer> queue = new LinkedBlockingQueue<>();
    private final int MAX_POINTS = 200;
    private final java.util.List<Integer> points = new java.util.ArrayList<>();

    private int currentMax = Integer.MIN_VALUE;
    private int currentMin = Integer.MAX_VALUE;

    public RealTimeGraph() {
        new Timer(50, e -> {
            Integer value;
            boolean changed = false;
            while ((value = queue.poll()) != null) {
                points.add(value);
                if (points.size() > MAX_POINTS) points.remove(0);

                if (value > currentMax) {
                    currentMax = value;
                    changed = true;
                }
                if (value < currentMin) {
                    currentMin = value;
                    changed = true;
                }
            }
            if (changed) repaint(); // если изменился масштаб, перерисовываем
            else if (!points.isEmpty()) repaint();
        }).start();
    }

    public void addValue(int value) {
        queue.offer(value);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, getWidth(), getHeight());

        g.setColor(Color.GREEN);
        if (points.size() < 2) return;

        int width = getWidth();
        int height = getHeight();
        int step = width / MAX_POINTS;
        if (step < 1) step = 1;

        for (int i = 1; i < points.size(); i++) {
            int x1 = (i - 1) * step;
            int x2 = i * step;

            // нормализация по текущему диапазону
            int y1 = normalize(points.get(i - 1), currentMin, currentMax, height);
            int y2 = normalize(points.get(i), currentMin, currentMax, height);

            g.drawLine(x1, y1, x2, y2);
        }
    }

    private int normalize(int value, int min, int max, int height) {
        if (max == min) return height / 2; // чтобы не делить на 0
        double scaled = (double)(value - min) / (max - min);
        return height - (int)(scaled * height);
    }
}
