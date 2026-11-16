package de.audi.tghu.tone.hmi.pgen2high;

import java.util.List;
import de.audi.atip.hmi.view.HMIView;
import de.audi.atip.hmi.view.IPartialPopupController;
import de.esolutions.hmi.widgets.core.controllers.AbstractWidget;
import de.esolutions.hmi.widgets.core.controllers.PartialPopupStub;
import de.esolutions.hmi.widgets.core.layout.AxisConstraints;
import de.esolutions.hmi.widgets.core.layout.GridLayout;
import de.esolutions.hmi.widgets.core.layout.GridLayoutHints;
import de.esolutions.hmi.widgets.pgen2.ScreenWidgetPGen2;
import de.esolutions.hmi.widgets.pgen2.controllers.LabelControllerPGen2;
import de.esolutions.hmi.widgets.pgen2.menu.MenuController;
import de.esolutions.hmi.widgets.pgen2.menu.MenuItemController;
import de.esolutions.hmi.widgets.pgen2.renderers.CompositeRendererPGen2;
import de.esolutions.hmi.widgets.pgen2.renderers.LabelRendererPGen2;

/** Native Sound menu entry; opens a separate rotary popup. */
public final class PromptLevelPanel {
    private PromptLevelPanel() { }
    public static final class Entry extends MenuItemController {
        private void open() {
            getTerminal().getPartialPopupManager().showPopupAndRepaintScreen(PromptRotaryPopup.POPUP_ID);
        }
        public void onTyped(boolean selected, int index) { open(); }
        protected void onTyped(boolean selected, int index, int x, int y) { open(); }
    }
    private static MenuController findMenu(AbstractWidget widget) {
        if (widget instanceof MenuController) return (MenuController) widget;
        List children = widget.getChildren();
        if (children == null) return null;
        for (int i = 0; i < children.size(); i++) {
            Object child = children.get(i);
            if (child instanceof AbstractWidget) {
                MenuController found = findMenu((AbstractWidget) child);
                if (found != null) return found;
            }
        }
        return null;
    }
    public static void install(ScreenWidgetPGen2 screen, int[] ids, HMIView[][] views,
            ToneScreenFactory factory, int variant) {
        screen.setViews(ids, views);
        MenuController menu = findMenu(screen);
        if (menu == null) throw new IllegalStateException("Sound menu not found");
        Entry entry = new Entry();
        entry.setRenderer(new CompositeRendererPGen2(entry));
        entry.setColorIndices(new int[] {1, 0, 3, 2, 0});
        LabelControllerPGen2 title = new LabelControllerPGen2();
        LabelRendererPGen2 renderer = new LabelRendererPGen2(title);
        title.setRenderer(renderer);
        renderer.setFonts(factory.getFonts(1, variant));
        title.setText("CarPlay Music Level");
        title.setBounds(0, 0, 500, 50);
        title.setColorIndices(new int[] {3, 0, 3, 0, 0, 0, 0});
        GridLayout layout = new GridLayout();
        AxisConstraints columns = new AxisConstraints(1);
        columns.gaps = new int[] {15, 15};
        columns.grow = new float[] {1.0f};
        layout.setColumnConstraints(columns);
        AxisConstraints rows = new AxisConstraints(1);
        rows.gaps = new int[] {15, 15};
        rows.size = new int[] {26};
        layout.setRowConstraints(rows);
        GridLayoutHints hint = new GridLayoutHints(0, 0);
        hint.hidemode = 0;
        layout.setCellConstraints(new GridLayoutHints[] {hint}, new Object[] {title});
        entry.setLayoutManager(layout);
        entry.add(title);
        menu.add(entry);
    }
    public static IPartialPopupController[] appendStub(IPartialPopupController[] existing, int terminal) {
        IPartialPopupController[] result = new IPartialPopupController[existing.length + 1];
        System.arraycopy(existing, 0, result, 0, existing.length);
        result[existing.length] = new PartialPopupStub(PromptRotaryPopup.POPUP_ID,
                -1, 0, -1, 0, 0, true, 7, terminal, 2, null, false, false);
        return result;
    }
}
