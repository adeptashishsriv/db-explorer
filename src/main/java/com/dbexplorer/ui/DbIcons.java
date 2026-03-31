package com.dbexplorer.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import javax.swing.Icon;
import javax.swing.ImageIcon;

/**
 * Clean Java2D icon set. Rules:
 *  - Every icon painted at exactly its display size (no scaling pipeline)
 *  - Max 2 colors per icon: one background fill + white symbol
 *  - Thick strokes (2px minimum) — thin lines disappear at 16px
 *  - Simple geometry only: rectangles, circles, lines, triangles
 *  - Full AA + STROKE_NORMALIZE for pixel-snapped edges
 */
public final class DbIcons {

    public static final int SIZE = 16;
    public static final int TB   = 20;

    // Palette — distinct, colorblind-safe
    private static final Color C_BLUE   = new Color(0x1565C0);
    private static final Color C_GREEN  = new Color(0x2E7D32);
    private static final Color C_RED    = new Color(0xC62828);
    private static final Color C_ORANGE = new Color(0xE65100);
    private static final Color C_TEAL   = new Color(0x00695C);
    private static final Color C_PURPLE = new Color(0x6A1B9A);
    private static final Color C_AMBER  = new Color(0xF57F17);
    private static final Color C_GREY   = new Color(0x546E7A);
    private static final Color C_WHITE  = Color.WHITE;

    // ── Tree icons ────────────────────────────────────────────────────────────
    public static final Icon DATABASE_CONNECTED    = px(SIZE, DbIcons::iDbOn);
    public static final Icon DATABASE_DISCONNECTED = px(SIZE, DbIcons::iDbOff);
    public static final Icon DATABASE_DYNAMO       = px(SIZE, DbIcons::iDynamo);
    public static final Icon DATABASE_SQLITE       = px(SIZE, DbIcons::iSqlite);
    public static final Icon DATABASE_GENERIC      = px(SIZE, DbIcons::iGeneric);
    public static final Icon SCHEMA                = px(SIZE, DbIcons::iSchema);
    public static final Icon TABLE                 = px(SIZE, DbIcons::iTable);
    public static final Icon VIEW                  = px(SIZE, DbIcons::iView);
    public static final Icon MAT_VIEW              = px(SIZE, DbIcons::iMatView);
    public static final Icon FUNCTION              = px(SIZE, DbIcons::iFunction);
    public static final Icon PROCEDURE             = px(SIZE, DbIcons::iProcedure);
    public static final Icon INDEX                 = px(SIZE, DbIcons::iIndex);
    public static final Icon SEQUENCE              = px(SIZE, DbIcons::iSequence);
    public static final Icon COLUMN                = px(SIZE, DbIcons::iColumn);
    public static final Icon FOLDER_OPEN           = px(SIZE, DbIcons::iFolderOpen);
    public static final Icon FOLDER_CLOSED         = px(SIZE, DbIcons::iFolderClosed);
    public static final Icon LOADING               = px(SIZE, DbIcons::iLoading);

    // ── Toolbar icons ─────────────────────────────────────────────────────────
    public static final Icon TB_ADD        = px(TB, DbIcons::tbAdd);
    public static final Icon TB_DISCONNECT = px(TB, DbIcons::tbDisconnect);
    public static final Icon TB_RUN        = px(TB, DbIcons::tbRun);
    public static final Icon TB_CANCEL     = px(TB, DbIcons::tbCancel);
    public static final Icon TB_NEW_TAB    = px(TB, DbIcons::tbNewTab);
    public static final Icon TB_EXPLAIN    = px(TB, DbIcons::tbExplain);
    public static final Icon TB_CLEAR      = px(TB, DbIcons::tbClear);
    public static final Icon TB_ABOUT      = px(TB, DbIcons::tbAbout);
    public static final Icon TB_DASHBOARD  = px(TB, DbIcons::tbDashboard);

    // ── Menu icons ────────────────────────────────────────────────────────────
    public static final Icon MENU_DDL       = px(SIZE, DbIcons::mDdl);
    public static final Icon MENU_INSERT    = px(SIZE, DbIcons::mInsert);
    public static final Icon MENU_UPDATE    = px(SIZE, DbIcons::mUpdate);
    public static final Icon MENU_CSV       = px(SIZE, DbIcons::mCsv);
    public static final Icon MENU_DIAGRAM   = px(SIZE, DbIcons::mDiagram);
    public static final Icon MENU_VIEW_DATA = px(SIZE, DbIcons::mViewData);
    public static final Icon MENU_COPY      = px(SIZE, DbIcons::mCopy);
    public static final Icon MENU_SAVE      = px(SIZE, DbIcons::mSave);
    public static final Icon MENU_EXPORT    = px(SIZE, DbIcons::mExport);

    private DbIcons() {}

    @FunctionalInterface interface P { void draw(Graphics2D g, int s); }

    /** Paint at exactly s×s — zero scaling, zero blur. */
    private static Icon px(int s, P p) {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,    RenderingHints.VALUE_STROKE_NORMALIZE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);
        p.draw(g, s);
        g.dispose();
        return new ImageIcon(img);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Filled rounded rect background. */
    static void bg(Graphics2D g, Color c, int s, int r) {
        g.setColor(c);
        g.fillRoundRect(1, 1, s-2, s-2, r, r);
    }

    /** White stroke. */
    static BasicStroke ws(float w) {
        return new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }

    /** White square stroke (for grid lines). */
    static BasicStroke ss(float w) {
        return new BasicStroke(w, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER);
    }

    // ── Database cylinder (shared) ────────────────────────────────────────────
    static void cylinder(Graphics2D g, int s, Color fill, Color dot) {
        int x=2, w=s-4, ey=2, eh=4, by=s-6;
        // body
        g.setColor(fill);
        g.fillRect(x, ey+eh/2, w, by-ey-eh/2+eh/2);
        // top ellipse
        g.fillOval(x, ey, w, eh);
        // bottom ellipse (darker)
        g.setColor(fill.darker());
        g.fillOval(x, by, w, eh);
        // outline
        g.setColor(new Color(0,0,0,60));
        g.setStroke(ss(1f));
        g.drawOval(x, ey, w, eh);
        g.drawLine(x, ey+eh/2, x, by+eh/2);
        g.drawLine(x+w, ey+eh/2, x+w, by+eh/2);
        g.drawOval(x, by, w, eh);
        // status dot
        g.setColor(dot);
        g.fillOval(s-6, s-6, 5, 5);
        g.setColor(C_WHITE);
        g.setStroke(ss(1f));
        g.drawOval(s-6, s-6, 5, 5);
    }

    // ── Tree icon painters ────────────────────────────────────────────────────

    static void iDbOn(Graphics2D g, int s)  { cylinder(g, s, C_BLUE,  C_GREEN); }
    static void iDbOff(Graphics2D g, int s) { cylinder(g, s, C_GREY,  C_RED);   }
    static void iSqlite(Graphics2D g, int s){ cylinder(g, s, C_TEAL,  C_GREEN); }
    static void iGeneric(Graphics2D g, int s){ cylinder(g, s, C_GREY, C_AMBER); }

    static void iDynamo(Graphics2D g, int s) {
        cylinder(g, s, C_ORANGE, C_GREEN);
        // lightning bolt
        g.setColor(C_WHITE);
        int[] bx = {9,6,8,5,10,8}, by = {2,8,8,14,7,7};
        g.fillPolygon(bx, by, 6);
    }

    static void iSchema(Graphics2D g, int s) {
        // 3 stacked layers
        int[] ys = {1, 6, 11};
        Color[] cs = {new Color(0x1E88E5), new Color(0x1565C0), new Color(0x0D47A1)};
        for (int i = 0; i < 3; i++) {
            g.setColor(cs[i]);
            g.fillRoundRect(1, ys[i], s-2, 4, 2, 2);
        }
    }

    static void iTable(Graphics2D g, int s) {
        // header + 2 rows
        g.setColor(C_BLUE);
        g.fillRoundRect(1, 1, s-2, s-2, 2, 2);
        g.setColor(new Color(0x1E88E5));
        g.fillRoundRect(1, 1, s-2, 5, 2, 2);
        g.fillRect(1, 3, s-2, 3);
        // row fills
        g.setColor(new Color(255,255,255,200));
        g.fillRect(2, 7, s-3, 3);
        g.setColor(new Color(255,255,255,120));
        g.fillRect(2, 11, s-3, 3);
        // grid
        g.setColor(new Color(255,255,255,160));
        g.setStroke(ss(1f));
        g.drawLine(1, 6, s-1, 6);
        g.drawLine(1, 10, s-1, 10);
        g.drawLine(s/2, 6, s/2, s-1);
    }

    static void iView(Graphics2D g, int s) {
        bg(g, C_TEAL, s, 3);
        // eye whites
        g.setColor(C_WHITE);
        int[] ex = {1, s/2, s-1, s/2};
        int[] ey = {s/2, 3, s/2, s-3};
        g.fillPolygon(ex, ey, 4);
        // iris
        g.setColor(C_TEAL.darker());
        g.fillOval(s/2-3, s/2-3, 6, 6);
        // pupil
        g.setColor(new Color(0,0,60));
        g.fillOval(s/2-2, s/2-2, 4, 4);
    }

    static void iMatView(Graphics2D g, int s) {
        bg(g, C_TEAL, s, 3);
        // eye diamond (same as iView)
        g.setColor(C_WHITE);
        int[] ex = {1, s/2, s-1, s/2};
        int[] ey = {s/2-2, 2, s/2-2, s/2+2};
        g.fillPolygon(ex, ey, 4);
        // iris
        g.setColor(C_TEAL.darker());
        g.fillOval(s/2-2, s/2-4, 5, 5);
        // pupil
        g.setColor(new Color(0, 0, 60));
        g.fillOval(s/2-1, s/2-3, 3, 3);
        // two stacked rectangles below the eye (cached storage indicator)
        g.setColor(C_WHITE);
        g.fillRoundRect(2, s/2+3, s-4, 3, 1, 1);
        g.setColor(new Color(255, 255, 255, 160));
        g.fillRoundRect(2, s/2+7, s-4, 3, 1, 1);
    }

    static void iFunction(Graphics2D g, int s) {
        bg(g, C_PURPLE, s, 3);
        g.setColor(C_WHITE);
        g.setFont(new Font("Serif", Font.BOLD|Font.ITALIC, s-4));
        FontMetrics fm = g.getFontMetrics();
        g.drawString("f", (s-fm.stringWidth("f"))/2, s-3);
    }

    static void iProcedure(Graphics2D g, int s) {
        bg(g, C_ORANGE, s, 3);
        // gear: circle + 6 teeth
        g.setColor(C_WHITE);
        g.setStroke(ws(2f));
        int cx=s/2, cy=s/2, r=3;
        g.drawOval(cx-r, cy-r, r*2, r*2);
        for (int i=0; i<6; i++) {
            double a = Math.toRadians(i*60);
            g.drawLine((int)(cx+Math.cos(a)*r), (int)(cy+Math.sin(a)*r),
                       (int)(cx+Math.cos(a)*(r+3)), (int)(cy+Math.sin(a)*(r+3)));
        }
    }

    static void iIndex(Graphics2D g, int s) {
        bg(g, C_AMBER, s, 3);
        g.setColor(new Color(0x4A3000));
        // bold lightning
        int[] bx={10,6,8,5,10,8}, by={2,8,8,s-2,7,7};
        g.fillPolygon(bx, by, 6);
    }

    static void iSequence(Graphics2D g, int s) {
        bg(g, C_TEAL, s, 3);
        g.setColor(C_WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 6));
        g.drawString("123", 2, s-3);
    }

    static void iColumn(Graphics2D g, int s) {
        // single highlighted row
        g.setColor(new Color(0xBBDEFB));
        g.fillRoundRect(1, s/2-3, s-2, 6, 2, 2);
        g.setColor(C_BLUE);
        g.setStroke(ss(1.5f));
        g.drawRoundRect(1, s/2-3, s-2, 6, 2, 2);
        g.drawLine(s/2, s/2-3, s/2, s/2+3);
        // key dot
        g.setColor(C_AMBER);
        g.fillOval(3, s/2-2, 4, 4);
    }

    static void iFolderClosed(Graphics2D g, int s) {
        g.setColor(C_AMBER.darker());
        g.fillRoundRect(1, 5, 6, 3, 2, 2);
        g.setColor(C_AMBER);
        g.fillRoundRect(1, 7, s-2, s-9, 2, 2);
        g.setColor(new Color(0,0,0,40));
        g.setStroke(ss(1f));
        g.drawRoundRect(1, 7, s-2, s-9, 2, 2);
    }

    static void iFolderOpen(Graphics2D g, int s) {
        g.setColor(C_AMBER.darker());
        g.fillRoundRect(1, 4, 6, 3, 2, 2);
        int[] px={1,s-1,s-3,1}, py={6,6,s-1,s-1};
        g.setColor(C_AMBER);
        g.fillPolygon(px, py, 4);
        g.setColor(new Color(0,0,0,40));
        g.setStroke(ss(1f));
        g.drawPolygon(px, py, 4);
    }

    static void iLoading(Graphics2D g, int s) {
        g.setColor(new Color(180,180,180,80));
        g.setStroke(ws(2.5f));
        g.drawOval(2, 2, s-4, s-4);
        g.setColor(C_BLUE);
        g.drawArc(2, 2, s-4, s-4, 90, -240);
    }

    // ── Toolbar painters ──────────────────────────────────────────────────────

    static void tbAdd(Graphics2D g, int s) {
        // green circle + white plus
        g.setColor(C_GREEN);
        g.fillOval(1, 1, s-2, s-2);
        g.setColor(C_WHITE);
        g.setStroke(ws(2.5f));
        g.drawLine(s/2, 4, s/2, s-4);
        g.drawLine(4, s/2, s-4, s/2);
    }

    static void tbDisconnect(Graphics2D g, int s) {
        // red circle + white X
        g.setColor(C_RED);
        g.fillOval(1, 1, s-2, s-2);
        g.setColor(C_WHITE);
        g.setStroke(ws(2.5f));
        g.drawLine(5, 5, s-5, s-5);
        g.drawLine(s-5, 5, 5, s-5);
    }

    static void tbRun(Graphics2D g, int s) {
        // green circle + white triangle
        g.setColor(C_GREEN);
        g.fillOval(1, 1, s-2, s-2);
        g.setColor(C_WHITE);
        int[] px={6,6,s-4}, py={4,s-4,s/2};
        g.fillPolygon(px, py, 3);
    }

    static void tbCancel(Graphics2D g, int s) {
        // red circle + white filled square (stop symbol)
        g.setColor(C_RED);
        g.fillOval(1, 1, s-2, s-2);
        g.setColor(C_WHITE);
        int sq = s / 3;
        g.fillRect(s/2 - sq/2, s/2 - sq/2, sq, sq);
    }

    static void tbNewTab(Graphics2D g, int s) {
        // blue page
        g.setColor(C_BLUE);
        g.fillRoundRect(2, 1, s-6, s-2, 2, 2);
        // folded corner
        g.setColor(new Color(0x0D47A1));
        g.fillPolygon(new int[]{s-6,s-2,s-6}, new int[]{1,5,5}, 3);
        // white lines
        g.setColor(C_WHITE);
        g.setStroke(ss(1.5f));
        g.drawLine(4, 8, s-8, 8);
        g.drawLine(4, 11, s-8, 11);
        // green + badge
        g.setColor(C_GREEN);
        g.fillOval(s-8, s-8, 8, 8);
        g.setColor(C_WHITE);
        g.setStroke(ws(1.5f));
        g.drawLine(s-4, s-7, s-4, s-1);
        g.drawLine(s-7, s-4, s-1, s-4);
    }

    static void tbExplain(Graphics2D g, int s) {
        // purple bg + bar chart
        bg(g, C_PURPLE, s, 3);
        int[] hs={8,5,11,4,9};
        Color[] cs={new Color(0xCE93D8),new Color(0xBA68C8),
                    new Color(0xAB47BC),new Color(0x9C27B0),C_WHITE};
        for (int i=0; i<5; i++) {
            g.setColor(cs[i]);
            g.fillRect(2+i*3+i/2, s-2-hs[i], 3, hs[i]);
        }
    }

    static void tbClear(Graphics2D g, int s) {
        // red trash
        g.setColor(C_RED);
        g.fillRoundRect(s/2-4, 1, 8, 3, 1, 1);  // handle
        g.fillRoundRect(2, 4, s-4, 2, 1, 1);      // lid
        g.fillRoundRect(3, 6, s-6, s-7, 2, 2);    // body
        g.setColor(new Color(255,200,200,160));
        g.setStroke(ss(1.5f));
        g.drawLine(s/2-3, 8, s/2-3, s-3);
        g.drawLine(s/2,   8, s/2,   s-3);
        g.drawLine(s/2+3, 8, s/2+3, s-3);
    }

    static void tbAbout(Graphics2D g, int s) {
        // teal circle + white i
        g.setColor(C_TEAL);
        g.fillOval(1, 1, s-2, s-2);
        g.setColor(C_WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, s-6));
        FontMetrics fm = g.getFontMetrics();
        g.drawString("i", (s-fm.stringWidth("i"))/2, s-3);
    }

    static void tbDashboard(Graphics2D g, int s) {
        // Blue bar-chart icon representing the health dashboard
        g.setColor(C_BLUE);
        int bw = (s - 6) / 3;
        // Three bars of increasing height
        g.fillRect(2,          s - 4,      bw, 3);
        g.fillRect(2 + bw + 1, s - 8,      bw, 7);
        g.fillRect(2 + bw*2+2, s - 13,     bw, 12);
        // Horizontal baseline
        g.setColor(C_GREY);
        g.fillRect(1, s - 2, s - 2, 1);
    }

    // ── Menu icon painters ────────────────────────────────────────────────────

    static void mDdl(Graphics2D g, int s) {
        // orange doc
        g.setColor(C_ORANGE);
        g.fillRoundRect(1, 1, s-5, s-2, 2, 2);
        g.setColor(C_ORANGE.darker());
        g.fillPolygon(new int[]{s-5,s-1,s-5}, new int[]{1,5,5}, 3);
        g.setColor(C_WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 5));
        g.drawString("DDL", 2, s-3);
    }

    static void mInsert(Graphics2D g, int s) {
        // green doc + plus
        g.setColor(C_GREEN);
        g.fillRoundRect(1, 1, s-5, s-2, 2, 2);
        g.setColor(C_WHITE);
        g.setStroke(ss(1.5f));
        for (int y=4; y<=10; y+=3) g.drawLine(3, y, s-6, y);
        g.setColor(C_GREEN);
        g.fillOval(s-7, s-7, 7, 7);
        g.setColor(C_WHITE);
        g.setStroke(ws(1.5f));
        g.drawLine(s-4, s-6, s-4, s-1);
        g.drawLine(s-6, s-3, s-1, s-3);
    }

    static void mUpdate(Graphics2D g, int s) {
        // blue doc + pencil
        g.setColor(C_BLUE);
        g.fillRoundRect(1, 1, s-5, s-2, 2, 2);
        g.setColor(C_WHITE);
        g.setStroke(ss(1.5f));
        g.drawLine(3, 5, s-6, 5);
        g.drawLine(3, 8, s-6, 8);
        // amber pencil
        g.setColor(C_AMBER);
        g.fillPolygon(new int[]{s-6,s-2,s-3,s-7}, new int[]{s-6,s-10,s-9,s-5}, 4);
        g.setColor(C_RED);
        g.fillRect(s-7, s-5, 3, 2);
    }

    static void mCsv(Graphics2D g, int s) {
        // teal grid
        bg(g, C_TEAL, s, 2);
        g.setColor(new Color(255,255,255,100));
        g.setStroke(ss(1f));
        g.drawLine(1, s/3+1, s-1, s/3+1);
        g.drawLine(1, s*2/3, s-1, s*2/3);
        g.drawLine(s/3+1, 1, s/3+1, s-1);
        g.drawLine(s*2/3, 1, s*2/3, s-1);
        g.setColor(C_WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 5));
        g.drawString("CSV", 2, s/2+2);
    }

    static void mDiagram(Graphics2D g, int s) {
        // violet bg + ER nodes
        bg(g, C_PURPLE, s, 3);
        g.setColor(C_WHITE);
        g.setStroke(ws(1.5f));
        g.drawLine(s/4, s/2, s*3/4, s/2);
        g.drawLine(s/2, s/4, s/2, s*3/4);
        g.setColor(C_AMBER);
        int r=2;
        g.fillOval(s/4-r, s/2-r, r*2+1, r*2+1);
        g.fillOval(s*3/4-r, s/2-r, r*2+1, r*2+1);
        g.fillOval(s/2-r, s/4-r, r*2+1, r*2+1);
        g.fillOval(s/2-r, s*3/4-r, r*2+1, r*2+1);
    }

    static void mViewData(Graphics2D g, int s) {
        // blue table + eye
        g.setColor(C_BLUE);
        g.fillRoundRect(1, s/2, s-2, s/2-1, 2, 2);
        g.setColor(C_WHITE);
        g.setStroke(ss(1f));
        g.drawLine(1, s*3/4, s-1, s*3/4);
        // eye
        g.setColor(C_WHITE);
        int[] ex={1,s/2,s-1,s/2}, ey={s/3,2,s/3,s/2};
        g.fillPolygon(ex, ey, 4);
        g.setColor(C_TEAL);
        g.fillOval(s/2-3, s/3-2, 6, 5);
        g.setColor(new Color(0,0,60));
        g.fillOval(s/2-2, s/3-1, 4, 3);
    }

    static void mCopy(Graphics2D g, int s) {
        // two overlapping pages
        g.setColor(new Color(0x42A5F5));
        g.fillRoundRect(4, 4, s-5, s-5, 2, 2);
        g.setColor(C_BLUE);
        g.fillRoundRect(1, 1, s-5, s-5, 2, 2);
        g.setColor(C_WHITE);
        g.setStroke(ss(1.5f));
        g.drawLine(3, 5, s-6, 5);
        g.drawLine(3, 8, s-6, 8);
        g.drawLine(3, 11, s-8, 11);
    }

    static void mSave(Graphics2D g, int s) {
        // floppy disk
        bg(g, C_GREY, s, 2);
        g.setColor(new Color(0xECEFF1));
        g.fillRect(3, 1, s-7, 6);
        g.setColor(C_GREY);
        g.fillRect(s-6, 1, 3, 4);
        g.setColor(new Color(0x78909C));
        g.fillRoundRect(3, s-7, s-6, 5, 1, 1);
        g.setColor(new Color(0xECEFF1));
        g.fillRoundRect(4, s-6, s-8, 3, 1, 1);
    }

    static void mExport(Graphics2D g, int s) {
        // teal box + up arrow
        g.setColor(C_TEAL);
        g.setStroke(ws(2f));
        g.drawRoundRect(1, s/2, s-2, s/2-1, 2, 2);
        g.drawLine(s/2, 1, s/2, s/2+1);
        g.drawLine(s/4, s/4, s/2, 1);
        g.drawLine(s*3/4, s/4, s/2, 1);
    }
}
