package com.dbexplorer.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

import javax.swing.Icon;
import javax.swing.ImageIcon;

/**
 * Programmatically rendered vector icons for the database tree.
 * All icons are drawn with Java2D so they look crisp at any DPI
 * and automatically adapt to light/dark themes.
 */
public final class DbIcons {

    public static final int SIZE = 16;

    // Cached icons
    public static final Icon DATABASE_CONNECTED   = make(DbIcons::drawDatabaseConnected);
    public static final Icon DATABASE_DISCONNECTED = make(DbIcons::drawDatabaseDisconnected);
    public static final Icon DATABASE_DYNAMO       = make(DbIcons::drawDynamo);
    public static final Icon DATABASE_SQLITE       = make(DbIcons::drawSQLite);
    public static final Icon SCHEMA                = make(DbIcons::drawSchema);
    public static final Icon TABLE                 = make(DbIcons::drawTable);
    public static final Icon VIEW                  = make(DbIcons::drawView);
    public static final Icon FUNCTION              = make(DbIcons::drawFunction);
    public static final Icon PROCEDURE             = make(DbIcons::drawProcedure);
    public static final Icon INDEX                 = make(DbIcons::drawIndex);
    public static final Icon SEQUENCE              = make(DbIcons::drawSequence);
    public static final Icon COLUMN                = make(DbIcons::drawColumn);
    public static final Icon FOLDER_OPEN           = make(DbIcons::drawFolderOpen);
    public static final Icon FOLDER_CLOSED         = make(DbIcons::drawFolderClosed);
    public static final Icon LOADING               = make(DbIcons::drawLoading);

    private DbIcons() {}

    @FunctionalInterface
    private interface Painter { void paint(Graphics2D g); }

    private static Icon make(Painter p) {
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,   RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        p.paint(g);
        g.dispose();
        return new ImageIcon(img);
    }

    // ── Database cylinder (connected = green accent) ──────────────────────────
    private static void drawDatabaseConnected(Graphics2D g) {
        drawCylinder(g, new Color(41, 182, 246), new Color(2, 136, 209), new Color(100, 220, 100));
    }

    private static void drawDatabaseDisconnected(Graphics2D g) {
        drawCylinder(g, new Color(120, 120, 130), new Color(80, 80, 90), new Color(180, 60, 60));
    }

    private static void drawCylinder(Graphics2D g, Color body, Color shadow, Color dot) {
        int x = 1, y = 2, w = 14, h = 12;
        int ry = 3; // ellipse y-radius for top/bottom caps

        // Body gradient
        GradientPaint gp = new GradientPaint(x, 0, body.brighter(), x + w, 0, shadow);
        g.setPaint(gp);
        g.fillRoundRect(x, y + ry, w, h - ry, 3, 3);

        // Bottom cap ellipse
        g.setColor(shadow);
        g.fillOval(x, y + h - ry, w, ry * 2);

        // Top cap ellipse
        g.setColor(body.brighter());
        g.fillOval(x, y, w, ry * 2);

        // Horizontal lines on body (data rows feel)
        g.setColor(new Color(255, 255, 255, 40));
        g.setStroke(new BasicStroke(0.8f));
        for (int ly = y + ry + 2; ly < y + h - 1; ly += 3) {
            g.drawLine(x + 1, ly, x + w - 1, ly);
        }

        // Status dot (bottom-right)
        g.setColor(dot);
        g.fillOval(x + w - 4, y + h - 1, 5, 5);
        g.setColor(dot.darker());
        g.setStroke(new BasicStroke(0.7f));
        g.drawOval(x + w - 4, y + h - 1, 5, 5);
    }

    // ── SQLite (teal cylinder with file icon) ────────────────────────────────
    private static void drawSQLite(Graphics2D g) {
        drawCylinder(g, new Color(0, 188, 212), new Color(0, 131, 143), new Color(100, 220, 100));
        // Small "S" overlay
        g.setColor(new Color(255, 255, 255, 210));
        g.setFont(new Font("SansSerif", Font.BOLD, 8));
        g.drawString("S", 5, 10);
    }

    // ── DynamoDB (orange lightning bolt) ─────────────────────────────────────
    private static void drawDynamo(Graphics2D g) {        // Cylinder in orange
        drawCylinder(g, new Color(255, 153, 0), new Color(200, 100, 0), new Color(100, 220, 100));
        // Lightning bolt overlay
        g.setColor(new Color(255, 255, 255, 200));
        Path2D bolt = new Path2D.Float();
        bolt.moveTo(9, 2);
        bolt.lineTo(6, 8);
        bolt.lineTo(8.5, 8);
        bolt.lineTo(6, 14);
        bolt.lineTo(10, 7.5);
        bolt.lineTo(7.5, 7.5);
        bolt.closePath();
        g.fill(bolt);
    }

    // ── Schema (blueprint/layers) ─────────────────────────────────────────────
    private static void drawSchema(Graphics2D g) {
        Color c1 = new Color(100, 181, 246);
        Color c2 = new Color(66, 165, 245);
        Color c3 = new Color(30, 136, 229);

        // Three stacked rounded rectangles (layers)
        g.setColor(c3);
        g.fillRoundRect(2, 10, 12, 4, 3, 3);
        g.setColor(c2);
        g.fillRoundRect(2, 6,  12, 4, 3, 3);
        g.setColor(c1);
        g.fillRoundRect(2, 2,  12, 4, 3, 3);

        // Subtle borders
        g.setStroke(new BasicStroke(0.6f));
        g.setColor(new Color(0, 0, 0, 50));
        g.drawRoundRect(2, 10, 12, 4, 3, 3);
        g.drawRoundRect(2, 6,  12, 4, 3, 3);
        g.drawRoundRect(2, 2,  12, 4, 3, 3);
    }

    // ── Table (grid) ──────────────────────────────────────────────────────────
    private static void drawTable(Graphics2D g) {
        Color header = new Color(66, 165, 245);
        Color row1   = new Color(227, 242, 253);
        Color row2   = new Color(187, 222, 251);
        Color border = new Color(100, 160, 220);

        // Header row
        g.setColor(header);
        g.fillRoundRect(1, 1, 14, 4, 2, 2);

        // Data rows
        g.setColor(row1);
        g.fillRect(1, 5, 14, 3);
        g.setColor(row2);
        g.fillRect(1, 8, 14, 3);
        g.setColor(row1);
        g.fillRect(1, 11, 14, 3);

        // Outer border
        g.setColor(border);
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(1, 1, 14, 13, 2, 2);

        // Column divider
        g.setColor(new Color(100, 160, 220, 120));
        g.setStroke(new BasicStroke(0.8f));
        g.drawLine(7, 1, 7, 14);

        // Row dividers
        g.drawLine(1, 5,  15, 5);
        g.drawLine(1, 8,  15, 8);
        g.drawLine(1, 11, 15, 11);
    }

    // ── View (eye over grid) ──────────────────────────────────────────────────
    private static void drawView(Graphics2D g) {
        // Faded table background
        g.setColor(new Color(179, 229, 252, 160));
        g.fillRoundRect(1, 5, 14, 10, 2, 2);
        g.setColor(new Color(100, 181, 246, 100));
        g.setStroke(new BasicStroke(0.8f));
        g.drawRoundRect(1, 5, 14, 10, 2, 2);
        g.drawLine(1, 9, 15, 9);
        g.drawLine(7, 5, 7, 15);

        // Eye shape
        g.setColor(new Color(255, 255, 255, 220));
        g.fillOval(2, 1, 12, 8);

        Path2D eye = new Path2D.Float();
        eye.moveTo(2, 5);
        eye.quadTo(8, 0, 14, 5);
        eye.quadTo(8, 10, 2, 5);
        eye.closePath();
        g.setColor(new Color(30, 136, 229));
        g.fill(eye);

        // Pupil
        g.setColor(new Color(13, 71, 161));
        g.fillOval(5, 3, 6, 5);
        g.setColor(new Color(255, 255, 255, 180));
        g.fillOval(6, 4, 2, 2);
    }

    // ── Function (ƒ symbol) ───────────────────────────────────────────────────
    private static void drawFunction(Graphics2D g) {
        g.setColor(new Color(171, 71, 188));
        g.fillRoundRect(1, 1, 14, 14, 4, 4);

        g.setColor(Color.WHITE);
        g.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 11));
        FontMetrics fm = g.getFontMetrics();
        String txt = "f";
        int tx = (SIZE - fm.stringWidth(txt)) / 2;
        int ty = (SIZE + fm.getAscent() - fm.getDescent()) / 2;
        g.drawString(txt, tx, ty);
    }

    // ── Procedure (gear/cog) ──────────────────────────────────────────────────
    private static void drawProcedure(Graphics2D g) {
        g.setColor(new Color(255, 167, 38));
        g.fillRoundRect(1, 1, 14, 14, 4, 4);

        // Gear teeth
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(1.5f));
        int cx = 8, cy = 8, r = 4;
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45);
            int x1 = (int)(cx + Math.cos(a) * (r - 1));
            int y1 = (int)(cy + Math.sin(a) * (r - 1));
            int x2 = (int)(cx + Math.cos(a) * (r + 2));
            int y2 = (int)(cy + Math.sin(a) * (r + 2));
            g.drawLine(x1, y1, x2, y2);
        }
        g.fillOval(cx - 2, cy - 2, 4, 4);
    }

    // ── Index (lightning bolt) ────────────────────────────────────────────────
    private static void drawIndex(Graphics2D g) {
        g.setColor(new Color(255, 213, 79));
        g.fillRoundRect(1, 1, 14, 14, 4, 4);

        g.setColor(new Color(100, 70, 0));
        Path2D bolt = new Path2D.Float();
        bolt.moveTo(10, 2);
        bolt.lineTo(5,  8);
        bolt.lineTo(8,  8);
        bolt.lineTo(5, 14);
        bolt.lineTo(11, 7);
        bolt.lineTo(8,  7);
        bolt.closePath();
        g.fill(bolt);
    }

    // ── Sequence (123 counter) ────────────────────────────────────────────────
    private static void drawSequence(Graphics2D g) {
        g.setColor(new Color(38, 198, 218));
        g.fillRoundRect(1, 1, 14, 14, 4, 4);

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 7));
        g.drawString("1", 3, 7);
        g.drawString("2", 3, 13);
        g.setStroke(new BasicStroke(1f));
        g.drawLine(8, 4, 13, 4);
        g.drawLine(8, 10, 13, 10);
    }

    // ── Column (single row indicator) ────────────────────────────────────────
    private static void drawColumn(Graphics2D g) {
        g.setColor(new Color(144, 202, 249));
        g.fillRect(1, 5, 14, 6);
        g.setColor(new Color(66, 165, 245));
        g.setStroke(new BasicStroke(0.8f));
        g.drawRect(1, 5, 14, 6);
        g.drawLine(6, 5, 6, 11);

        // Small key icon for primary key feel
        g.setColor(new Color(255, 193, 7));
        g.fillOval(2, 6, 3, 3);
        g.setStroke(new BasicStroke(1f));
        g.drawLine(5, 8, 8, 8);
        g.drawLine(7, 8, 7, 10);
    }

    // ── Folder closed ─────────────────────────────────────────────────────────
    private static void drawFolderClosed(Graphics2D g) {
        Color body = new Color(255, 202, 40);
        Color tab  = new Color(255, 179, 0);

        // Tab
        g.setColor(tab);
        g.fillRoundRect(1, 4, 6, 3, 2, 2);

        // Body
        g.setColor(body);
        g.fillRoundRect(1, 6, 14, 9, 3, 3);

        // Shine
        g.setColor(new Color(255, 255, 255, 60));
        g.fillRoundRect(2, 7, 12, 3, 2, 2);

        // Border
        g.setColor(new Color(180, 130, 0, 120));
        g.setStroke(new BasicStroke(0.8f));
        g.drawRoundRect(1, 6, 14, 9, 3, 3);
    }

    // ── Folder open ───────────────────────────────────────────────────────────
    private static void drawFolderOpen(Graphics2D g) {
        Color body = new Color(255, 213, 79);
        Color tab  = new Color(255, 193, 7);

        g.setColor(tab);
        g.fillRoundRect(1, 4, 6, 3, 2, 2);

        // Open folder body (trapezoid feel)
        Path2D folder = new Path2D.Float();
        folder.moveTo(1,  7);
        folder.lineTo(15, 7);
        folder.lineTo(13, 15);
        folder.lineTo(1,  15);
        folder.closePath();
        g.setColor(body);
        g.fill(folder);

        g.setColor(new Color(255, 255, 255, 60));
        g.fillRect(2, 8, 12, 3);

        g.setColor(new Color(180, 130, 0, 120));
        g.setStroke(new BasicStroke(0.8f));
        g.draw(folder);
    }

    // ── Loading spinner (arc) ─────────────────────────────────────────────────
    private static void drawLoading(Graphics2D g) {
        g.setColor(new Color(150, 150, 150, 80));
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawOval(2, 2, 12, 12);
        g.setColor(new Color(66, 165, 245));
        g.drawArc(2, 2, 12, 12, 90, -270);
    }
}
