package com.salon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/** Client page: every service as a picture card with its price, and a button to request a booking. */
public class ServicesGallery extends BackgroundPanel {
    public interface BookHandler { void book(Salon.ServiceCard s); }

    private final BookHandler handler;
    private final ScrollPanel grid = new ScrollPanel(new GridLayout(0, 3, 16, 16));

    public ServicesGallery(BookHandler handler) {
        super(new BorderLayout(12, 12), 41);
        this.handler = handler;
        setBorder(new EmptyBorder(14, 14, 14, 14));
        RoundedPanel head = new RoundedPanel(new GridLayout(2, 1), 20);
        head.setBackground(Color.WHITE);
        JLabel t = new JLabel("OUR SERVICES & PRICES");
        t.setFont(Theme.H2); t.setForeground(Theme.PURPLE_DARK);
        JLabel st = new JLabel("Choose a service and request a booking. Our staff will confirm a time, then you can pay online.");
        st.setFont(Theme.BODY); st.setForeground(Theme.MUTED);
        head.add(t); head.add(st);
        add(head, BorderLayout.NORTH);

        grid.setBorder(new EmptyBorder(2, 2, 2, 8));
        JPanel holder = new ScrollPanel(new BorderLayout());
        holder.add(grid, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(holder);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(18);
        add(sp, BorderLayout.CENTER);
        Ui.register(this::refresh);
        refresh();
    }

    public final void refresh() {
        try {
            List<Salon.ServiceCard> services = Salon.clientServices();
            grid.removeAll();
            for (Salon.ServiceCard s : services) grid.add(card(s));
            for (int i = services.size(); i % 3 != 0; i++) { JPanel f = new JPanel(); f.setOpaque(false); grid.add(f); }
            grid.revalidate(); grid.repaint();
        } catch (Exception e) { Ui.error(this, e); }
    }

    private JComponent card(Salon.ServiceCard s) {
        RoundedPanel c = new RoundedPanel(new BorderLayout(0, 8), 22);
        c.setBackground(Color.WHITE);
        c.setBorder(new EmptyBorder(10, 10, 14, 10));
        JComponent pic = new JComponent() {
            private Image cache; private int cw, ch;
            @Override protected void paintComponent(Graphics g) {
                if (getWidth() <= 0 || getHeight() <= 0) return;
                if (cache == null || cw != getWidth() || ch != getHeight()) {
                    cw = getWidth(); ch = getHeight();
                    cache = ImageUtil.icon(s.image(), s.name(), s.category(), cw, ch).getImage();
                }
                g.drawImage(cache, 0, 0, null);
            }
        };
        pic.setPreferredSize(new Dimension(300, 170));
        JLabel name = new JLabel(s.name());
        name.setFont(Theme.serif(Font.BOLD, 18)); name.setForeground(Theme.PURPLE_DARK);
        JLabel meta = new JLabel((s.category().isBlank() ? "Service" : s.category()) + "  •  " + s.duration() + " min");
        meta.setFont(Theme.BODY); meta.setForeground(Theme.MUTED);
        JLabel price = new JLabel("Rs. " + String.format("%,.0f", s.price()));
        price.setFont(Theme.serif(Font.BOLD, 22)); price.setForeground(Theme.ROSE_GOLD);
        JButton book = Theme.button("REQUEST BOOKING", Theme.ROSE_GOLD);
        book.addActionListener(e -> handler.book(s));
        JPanel text = new JPanel(new GridLayout(3, 1, 0, 2));
        text.setOpaque(false);
        text.add(name); text.add(meta); text.add(price);
        JPanel south = new JPanel(new BorderLayout(0, 8));
        south.setOpaque(false);
        south.add(text, BorderLayout.CENTER);
        south.add(book, BorderLayout.SOUTH);
        c.add(pic, BorderLayout.NORTH);
        c.add(south, BorderLayout.CENTER);
        return c;
    }
}
