package com.dbexplorer.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * A transparent full-screen overlay that plays a 5-second particle animation
 * when certain themes are selected (e.g. rose petals for "Rose Garden",
 * leaves for "Forest Green").
 */
public class ThemeAnimationOverlay extends JComponent {

    public enum AnimationType { ROSE_PETALS, LEAVES, SNOWFLAKES, STARS, BUBBLES, NONE }

    private static final int DURATION_MS = 5000;
    private static final int FPS = 60;
    private static final int PARTICLE_COUNT = 40;

    private final List<Particle> particles = new ArrayList<>();
    private final Random rng = new Random();
    private Timer animTimer;
    private long startTime;
    private AnimationType type = AnimationType.NONE;

    public ThemeAnimationOverlay() {
        setOpaque(false);
        setFocusable(false);
    }

    /** Map a theme name to its animation type. */
    public static AnimationType animationFor(String themeName) {
        if (themeName == null) return AnimationType.NONE;
        return switch (themeName) {
            case "Rose Garden"   -> AnimationType.ROSE_PETALS;
            case "Forest Green"  -> AnimationType.LEAVES;
            case "Arctic Frost"  -> AnimationType.SNOWFLAKES;
            case "Sunset Purple" -> AnimationType.STARS;
            case "Ocean Blue"    -> AnimationType.BUBBLES;
            default              -> AnimationType.NONE;
        };
    }

    /** Start the animation for the given type. Safe to call on EDT. */
    public void play(AnimationType animType) {
        if (animType == AnimationType.NONE) return;
        stop();
        this.type = animType;
        
        // Ensure overlay matches parent size
        Container parent = getParent();
        if (parent != null) {
            setBounds(0, 0, parent.getWidth(), parent.getHeight());
        }
        
        spawnParticles();
        startTime = System.currentTimeMillis();
        setVisible(true);
        animTimer = new Timer(1000 / FPS, e -> tick());
        animTimer.start();
    }

    private void stop() {
        if (animTimer != null) { animTimer.stop(); animTimer = null; }
        particles.clear();
        setVisible(false);
    }

    private void tick() {
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed >= DURATION_MS) { stop(); return; }
        float progress = elapsed / (float) DURATION_MS;
        for (Particle p : particles) p.update(progress);
        repaint();
    }

    private void spawnParticles() {
        particles.clear();
        int w = getWidth() > 0 ? getWidth() : 1200;
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            particles.add(new Particle(type, w, rng));
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (particles.isEmpty()) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        long elapsed = System.currentTimeMillis() - startTime;
        float globalAlpha = 1f;
        // Fade out in last second
        if (elapsed > DURATION_MS - 1000) {
            globalAlpha = (DURATION_MS - elapsed) / 1000f;
        }
        for (Particle p : particles) p.draw(g2, globalAlpha);
        g2.dispose();
    }

    // -------------------------------------------------------------------------
    // Particle
    // -------------------------------------------------------------------------
    private static class Particle {
        final AnimationType type;
        float x, y;
        float vx, vy;
        float rotation, rotSpeed;
        float size;
        float alpha;
        Color color;
        float delay; // 0..1 — particle starts after this fraction of total time

        Particle(AnimationType type, int width, Random rng) {
            this.type = type;
            delay = rng.nextFloat() * 0.6f;
            x = rng.nextFloat() * width;
            y = -20 - rng.nextFloat() * 200;
            vx = (rng.nextFloat() - 0.5f) * 1.5f;
            vy = 1.5f + rng.nextFloat() * 2.5f;
            rotation = rng.nextFloat() * 360f;
            rotSpeed = (rng.nextFloat() - 0.5f) * 4f;
            size = 10 + rng.nextFloat() * 14f;
            alpha = 0.7f + rng.nextFloat() * 0.3f;
            color = pickColor(type, rng);
        }

        private static Color pickColor(AnimationType type, Random rng) {
            return switch (type) {
                case ROSE_PETALS -> new Color(
                        200 + rng.nextInt(55),
                        20  + rng.nextInt(80),
                        60  + rng.nextInt(60));
                case LEAVES -> new Color(
                        20  + rng.nextInt(60),
                        100 + rng.nextInt(100),
                        20  + rng.nextInt(40));
                case SNOWFLAKES -> new Color(
                        200 + rng.nextInt(55),
                        220 + rng.nextInt(35),
                        255);
                case STARS -> new Color(
                        180 + rng.nextInt(75),
                        100 + rng.nextInt(80),
                        220 + rng.nextInt(35));
                case BUBBLES -> new Color(
                        40  + rng.nextInt(60),
                        140 + rng.nextInt(80),
                        200 + rng.nextInt(55));
                default -> Color.WHITE;
            };
        }

        void update(float progress) {
            if (progress < delay) return;
            x += vx;
            y += vy;
            rotation += rotSpeed;
            // gentle horizontal sway
            x += (float) Math.sin(progress * Math.PI * 4 + x * 0.05f) * 0.5f;
        }

        void draw(Graphics2D g2, float globalAlpha) {
            if (y < -30) return;
            AffineTransform old = g2.getTransform();
            g2.translate(x, y);
            g2.rotate(Math.toRadians(rotation));
            float a = alpha * globalAlpha;
            Color c = new Color(color.getRed(), color.getGreen(), color.getBlue(),
                    Math.max(0, Math.min(255, (int)(a * 255))));
            g2.setColor(c);
            switch (type) {
                case ROSE_PETALS -> drawPetal(g2);
                case LEAVES      -> drawLeaf(g2);
                case SNOWFLAKES  -> drawSnowflake(g2);
                case STARS       -> drawStar(g2);
                case BUBBLES     -> drawBubble(g2, c);
            }
            g2.setTransform(old);
        }

        private void drawPetal(Graphics2D g2) {
            int s = (int) size;
            Path2D petal = new Path2D.Float();
            petal.moveTo(0, -s);
            petal.curveTo( s * 0.8,  -s * 0.5,  s * 0.8,  s * 0.5,  0,  s);
            petal.curveTo(-s * 0.8,   s * 0.5, -s * 0.8, -s * 0.5,  0, -s);
            g2.fill(petal);
        }

        private void drawLeaf(Graphics2D g2) {
            int s = (int) size;
            Path2D leaf = new Path2D.Float();
            leaf.moveTo(0, -s);
            leaf.curveTo( s * 0.7, -s * 0.3,  s * 0.7,  s * 0.3,  0,  s);
            leaf.curveTo(-s * 0.7,  s * 0.3, -s * 0.7, -s * 0.3,  0, -s);
            g2.fill(leaf);
            // midrib
            g2.setColor(new Color(0, 80, 0, 120));
            g2.setStroke(new BasicStroke(1f));
            g2.drawLine(0, -s, 0, s);
        }

        private void drawSnowflake(Graphics2D g2) {
            int s = (int) size;
            g2.setStroke(new BasicStroke(1.5f));
            for (int i = 0; i < 6; i++) {
                g2.drawLine(0, 0, s, 0);
                g2.drawLine((int)(s * 0.6), -(int)(s * 0.2), s, 0);
                g2.drawLine((int)(s * 0.6),  (int)(s * 0.2), s, 0);
                g2.rotate(Math.PI / 3);
            }
        }

        private void drawStar(Graphics2D g2) {
            int s = (int) size;
            int inner = s / 2;
            int pts = 5;
            Path2D star = new Path2D.Float();
            for (int i = 0; i < pts * 2; i++) {
                double angle = Math.PI / pts * i - Math.PI / 2;
                double r = (i % 2 == 0) ? s : inner;
                double px = Math.cos(angle) * r;
                double py = Math.sin(angle) * r;
                if (i == 0) star.moveTo(px, py); else star.lineTo(px, py);
            }
            star.closePath();
            g2.fill(star);
        }

        private void drawBubble(Graphics2D g2, Color c) {
            int s = (int) size;
            g2.setStroke(new BasicStroke(1.5f));
            g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 80));
            g2.fillOval(-s, -s, s * 2, s * 2);
            g2.setColor(c);
            g2.drawOval(-s, -s, s * 2, s * 2);
            // shine
            g2.setColor(new Color(255, 255, 255, 100));
            g2.fillOval(-s / 2, -s / 2, s / 2, s / 3);
        }
    }
}
