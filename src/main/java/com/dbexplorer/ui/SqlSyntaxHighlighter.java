package com.dbexplorer.ui;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import java.awt.*;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies SQL keyword highlighting to a JTextPane.
 * Keywords are rendered in blue + bold; color adapts to light/dark themes.
 * Highlighting runs on a short SwingTimer to avoid re-styling on every keystroke.
 */
public class SqlSyntaxHighlighter implements DocumentListener {

    // Dark theme: lighter blue so it's visible on dark backgrounds
    private static final Color KEYWORD_COLOR_DARK = new Color(104, 151, 255);
    // Light theme: deep blue for contrast on white/light backgrounds
    private static final Color KEYWORD_COLOR_LIGHT = new Color(0, 0, 160);

    /** Determine keyword color based on current background brightness. */
    private Color getKeywordColor() {
        Color bg = textPane.getBackground();
        // Perceived brightness: dark bg -> use light keyword color, light bg -> use dark keyword color
        double brightness = (0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue()) / 255.0;
        return brightness < 0.5 ? KEYWORD_COLOR_DARK : KEYWORD_COLOR_LIGHT;
    }

    /** Determine normal text color based on current background brightness. */
    private Color getTextColor() {
        Color bg = textPane.getBackground();
        double brightness = (0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue()) / 255.0;
        return brightness < 0.5 ? new Color(210, 210, 210) : new Color(30, 30, 30);
    }

    private static final Set<String> KEYWORDS = Set.of(
            "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET",
            "DELETE", "CREATE", "ALTER", "DROP", "TABLE", "INDEX", "VIEW", "DATABASE",
            "GRANT", "REVOKE", "TRUNCATE", "JOIN", "INNER", "LEFT", "RIGHT", "OUTER",
            "FULL", "CROSS", "ON", "AND", "OR", "NOT", "IN", "EXISTS", "BETWEEN",
            "LIKE", "IS", "NULL", "AS", "ORDER", "BY", "GROUP", "HAVING", "LIMIT",
            "OFFSET", "UNION", "ALL", "DISTINCT", "CASE", "WHEN", "THEN", "ELSE",
            "END", "ASC", "DESC", "COUNT", "SUM", "AVG", "MIN", "MAX", "CAST",
            "COALESCE", "IF", "BEGIN", "COMMIT", "ROLLBACK", "SAVEPOINT", "TRANSACTION",
            "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "CONSTRAINT", "DEFAULT",
            "CHECK", "UNIQUE", "AUTO_INCREMENT", "IDENTITY", "SEQUENCE", "NEXTVAL",
            "WITH", "RECURSIVE", "FETCH", "FIRST", "NEXT", "ROWS", "ONLY",
            "TOP", "PERCENT", "MERGE", "USING", "MATCHED", "REPLACE", "EXPLAIN",
            "ANALYZE", "VACUUM", "RETURNING", "OVER", "PARTITION", "WINDOW",
            "RANK", "ROW_NUMBER", "DENSE_RANK", "LAG", "LEAD", "NTILE",
            "VARCHAR", "INT", "INTEGER", "BIGINT", "SMALLINT", "DECIMAL", "NUMERIC",
            "FLOAT", "DOUBLE", "BOOLEAN", "DATE", "TIMESTAMP", "TEXT", "CHAR", "BLOB",
            "CLOB", "SERIAL", "EXEC", "EXECUTE", "PROCEDURE", "FUNCTION", "CALL",
            "DECLARE", "CURSOR", "OPEN", "CLOSE", "DEALLOCATE", "PREPARE",
            "TRUE", "FALSE", "SCHEMA", "CATALOG", "COLUMN", "ADD", "RENAME",
            "EXCEPT", "INTERSECT", "ANY", "SOME", "LATERAL", "NATURAL", "PIVOT",
            "UNPIVOT", "GO", "USE", "SHOW", "DESCRIBE", "COMMENT", "DENY"
    );

    // Matches word boundaries around alphanumeric+underscore tokens
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\b[A-Za-z_][A-Za-z0-9_]*\\b");

    private final JTextPane textPane;
    private final StyledDocument doc;
    private final Timer debounceTimer;

    public SqlSyntaxHighlighter(JTextPane textPane) {
        this.textPane = textPane;
        this.doc = textPane.getStyledDocument();
        // Debounce: re-highlight 150ms after last keystroke
        this.debounceTimer = new Timer(150, e -> highlight());
        this.debounceTimer.setRepeats(false);
        doc.addDocumentListener(this);

        // Re-highlight when theme changes so colors adapt
        ThemeManager.addThemeChangeListener(this::forceHighlight);
    }

    private void highlight() {
        SwingUtilities.invokeLater(() -> {
            String text;
            try {
                text = doc.getText(0, doc.getLength());
            } catch (BadLocationException e) {
                return;
            }

            // Save caret position
            int caretPos = textPane.getCaretPosition();

            // Default style for all text
            Style defaultStyle = textPane.addStyle("default", null);
            StyleConstants.setForeground(defaultStyle, getTextColor());
            StyleConstants.setBold(defaultStyle, false);
            StyleConstants.setFontFamily(defaultStyle, Font.MONOSPACED);
            StyleConstants.setFontSize(defaultStyle, 13);
            doc.setCharacterAttributes(0, text.length(), defaultStyle, true);

            // Keyword style
            Style keywordStyle = textPane.addStyle("keyword", null);
            StyleConstants.setForeground(keywordStyle, getKeywordColor());
            StyleConstants.setBold(keywordStyle, true);
            StyleConstants.setFontFamily(keywordStyle, Font.MONOSPACED);
            StyleConstants.setFontSize(keywordStyle, 13);

            // Apply keyword highlighting
            Matcher matcher = TOKEN_PATTERN.matcher(text);
            while (matcher.find()) {
                if (KEYWORDS.contains(matcher.group().toUpperCase())) {
                    doc.setCharacterAttributes(matcher.start(), matcher.end() - matcher.start(),
                            keywordStyle, true);
                }
            }

            // Restore caret
            try {
                textPane.setCaretPosition(caretPos);
            } catch (IllegalArgumentException ignored) {}
        });
    }

    /** Force an immediate highlight pass (e.g. on initial load). */
    public void forceHighlight() {
        highlight();
    }

    @Override
    public void insertUpdate(DocumentEvent e) { debounceTimer.restart(); }

    @Override
    public void removeUpdate(DocumentEvent e) { debounceTimer.restart(); }

    @Override
    public void changedUpdate(DocumentEvent e) { /* style changes, ignore */ }
}
