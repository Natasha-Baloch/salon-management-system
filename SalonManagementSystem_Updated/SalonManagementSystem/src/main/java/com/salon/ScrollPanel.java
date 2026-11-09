package com.salon;

import javax.swing.*;
import java.awt.*;

/** A panel for scroll panes that always matches the width of the window (so content wraps instead of scrolling sideways). */
public class ScrollPanel extends JPanel implements Scrollable {
    public ScrollPanel(LayoutManager lm) { super(lm); setOpaque(false); }

    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
    @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 120; }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
}
