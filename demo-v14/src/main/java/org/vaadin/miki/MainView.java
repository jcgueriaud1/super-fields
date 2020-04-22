package org.vaadin.miki;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.vaadin.flow.router.Route;
import org.vaadin.miki.markers.HasLocale;
import org.vaadin.miki.superfields.dates.SuperDatePicker;
import org.vaadin.miki.superfields.dates.SuperDateTimePicker;
import org.vaadin.miki.superfields.itemgrid.ItemGrid;
import org.vaadin.miki.superfields.lazyload.LazyLoad;
import org.vaadin.miki.superfields.numbers.AbstractSuperNumberField;
import org.vaadin.miki.superfields.numbers.SuperBigDecimalField;
import org.vaadin.miki.superfields.numbers.SuperDoubleField;
import org.vaadin.miki.superfields.numbers.SuperIntegerField;
import org.vaadin.miki.superfields.numbers.SuperLongField;
import org.vaadin.miki.superfields.tabs.SuperTabs;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Demo app for various SuperFields and other components.
 * @author miki
 * @since 2020-04-07
 */
@CssImport("./styles/demo-styles.css")
@Route
public class MainView extends VerticalLayout {

    private final Map<Class<?>, Component> components = new LinkedHashMap<>();

    private final Map<Class<?>, Consumer<Object>> afterLocaleChange = new HashMap<>();

    private final Map<Class<?>, BiConsumer<Component, Consumer<Component[]>>> contentBuilders = new LinkedHashMap<>();

    private static Component generateParagraph(Class<? extends Component> type, int row, int column) {
        Paragraph result = new Paragraph(type.getSimpleName());
        result.setTitle(String.format("row %d, column %d", row, column));
        return result;
    }

    private static Component generateDiv(Class<? extends Component> type, int row, int column) {
        return new LazyLoad<Div>(() -> {
            final Div result = new Div();
            result.addClassNames("item-grid-cell");
            result.add(new Span(String.format("Row: %d. Column: %d.", row, column)));
            final TextField text = new TextField("Class name: ", type.getSimpleName());
            text.setValue(type.getSimpleName());
            text.addClassName("highlighted");
            text.addBlurListener(event -> text.setValue(type.getSimpleName()));
            result.add(text);
            return result;
        }).withId(type.getSimpleName()+"-"+row+"-"+column);
    }

    private void buildAbstractSuperNumberField(Component component, Consumer<Component[]> callback) {
        final Checkbox autoselect = new Checkbox("Select automatically on focus?");
        autoselect.addValueChangeListener(event -> ((AbstractSuperNumberField<?, ?>) component).setAutoselect(event.getValue()));

        final Checkbox separatorHidden = new Checkbox("Hide grouping separator on focus?");
        separatorHidden.addValueChangeListener(event -> ((AbstractSuperNumberField<?, ?>) component).setGroupingSeparatorHiddenOnFocus(event.getValue()));

        final Checkbox prefix = new Checkbox("Show prefix component?");
        prefix.addValueChangeListener(event -> ((AbstractSuperNumberField<?, ?>) component).setPrefixComponent(
                event.getValue() ? new Span(">") : null
        ));

        final Checkbox suffix = new Checkbox("Show suffix component?");
        suffix.addValueChangeListener(event -> ((AbstractSuperNumberField<?, ?>) component).setSuffixComponent(
                event.getValue() ? new Span("€") : null
        ));

        final Checkbox alignRight = new Checkbox("Align text to the right?");
        alignRight.addValueChangeListener(event -> {
                    if(event.getValue())
                        ((AbstractSuperNumberField<?, ?>) component).addThemeVariants(TextFieldVariant.LUMO_ALIGN_RIGHT);
                    else
                        ((AbstractSuperNumberField<?, ?>) component).removeThemeVariants(TextFieldVariant.LUMO_ALIGN_RIGHT);
                }
        );
        callback.accept(new Component[]{autoselect, separatorHidden, prefix, suffix, alignRight});
    }

    private void buildHasLocale(Component component, Consumer<Component[]> callback) {
        final ComboBox<Locale> locales = new ComboBox<>("Select locale:", new Locale("pl", "PL"), Locale.UK, Locale.FRANCE, Locale.GERMANY, Locale.CHINA);
        locales.setItemLabelGenerator(locale -> locale.getDisplayCountry() + " / "+locale.getDisplayLanguage());
        locales.setAllowCustomValue(false);
        locales.addValueChangeListener(event -> {
            ((HasLocale) component).setLocale(event.getValue());
            if(this.afterLocaleChange.containsKey(component.getClass()))
                this.afterLocaleChange.get(component.getClass()).accept(component);
        });
        callback.accept(new Component[]{locales});
    }

    private void buildHasValue(Component component, Consumer<Component[]> callback) {
        ((HasValue<?, ?>) component).addValueChangeListener(this::onAnyValueChanged);
    }

    @SuppressWarnings("unchecked")
    private void buildItemGrid(Component component, Consumer<Component[]> callback) {
        final RadioButtonGroup<Integer> buttons = new RadioButtonGroup<>();
        buttons.setItems(1, 2, 3, 4, 5, 6);
        buttons.setLabel("Number of columns:");
        buttons.setValue(ItemGrid.DEFAULT_COLUMN_COUNT);
        buttons.addValueChangeListener(event -> ((ItemGrid<?>)component).setColumnCount(event.getValue()));

        final Checkbox alternate = new Checkbox("Generate alternate cells?", event ->
                ((ItemGrid<Class<? extends Component>>)component).setCellGenerator(
                        event.getValue() ? MainView::generateDiv : MainView::generateParagraph
                )
        );

        callback.accept(new Component[]{buttons, alternate});
    }

    private Component buildContentsFor(Class<?> type) {
        VerticalLayout result = new VerticalLayout();
        Component component = this.components.get(type);

        this.contentBuilders.entrySet().stream().
                filter(entry -> entry.getKey().isAssignableFrom(type)).
                forEach(entry -> entry.getValue().accept(component, result::add));

        result.add(component);
        return result;
    }

    private void onAnyValueChanged(HasValue.ValueChangeEvent<?> valueChangeEvent) {
        Notification.show(String.format("%s changed value to %s", valueChangeEvent.getHasValue().getClass().getSimpleName(), valueChangeEvent.getValue()));
    }

    private Component getInfoPage() {
        return new VerticalLayout(
                new Span("Hello and welcome to SuperFields demo! Thank you for your interest in this little project, I hope you find it useful."),
                new Span("The components shown in this demo are available in SuperFields, a small collection of handy stuff designed to work with Vaadin 14 and Java. One day I got tired of repeating the same code over and over again to fix issues that repeat across pretty much every project I coded... and instead of complaining, I decided to fix the problems by releasing this library."),
                new Anchor("https://github.com/vaadin-miki/super-fields/issues", "Please use this link to report issues and request features and components."),
                new Anchor("https://github.com/vaadin-miki/super-fields", "You can also visit the project's main page on GitHub."),
                new Span("Unless otherwise noted, all code has been written by me (Miki) and is released under Apache 2.0 License.")
        );
    }

    public MainView() {
        this.components.put(SuperIntegerField.class, new SuperIntegerField("Integer (6 digits):").withMaximumIntegerDigits(6));
        this.components.put(SuperLongField.class, new SuperLongField("Long (11 digits):").withMaximumIntegerDigits(11));
        this.components.put(SuperDoubleField.class, new SuperDoubleField("Double (8 + 4 digits):").withMaximumIntegerDigits(8).withMaximumFractionDigits(4));
        this.components.put(SuperBigDecimalField.class, new SuperBigDecimalField("Big decimal (12 + 3 digits):").withMaximumIntegerDigits(12).withMaximumFractionDigits(3).withMinimumFractionDigits(1));
        this.components.put(SuperDatePicker.class, new SuperDatePicker("Pick a date:"));
        this.components.put(SuperDateTimePicker.class, new SuperDateTimePicker("Pick a date and time:"));
        this.components.put(ItemGrid.class, new ItemGrid<Class<? extends Component>>(
                null,
                () -> {
                    VerticalLayout result = new VerticalLayout();
                    result.setSpacing(true);
                    result.setPadding(true);
                    result.setAlignItems(Alignment.STRETCH);
                    result.setWidthFull();
                    return result;
                },
                MainView::generateParagraph,
                event -> {
                    if (event.isSelected())
                        event.getCellInformation().getComponent().getElement().getClassList().add("selected");
                    else event.getCellInformation().getComponent().getElement().getClassList().remove("selected");
                },
                SuperIntegerField.class, SuperLongField.class, SuperDoubleField.class,
                SuperBigDecimalField.class, SuperDatePicker.class, SuperDateTimePicker.class,
                SuperTabs.class, ItemGrid.class)
            .withRowComponentGenerator(rowNumber -> {
                    HorizontalLayout result = new HorizontalLayout();
                    result.setSpacing(true);
                    result.setAlignItems(Alignment.CENTER);
                    result.setPadding(true);
                    return result;
                })
        );

        this.contentBuilders.put(AbstractSuperNumberField.class, this::buildAbstractSuperNumberField);
        this.contentBuilders.put(HasLocale.class, this::buildHasLocale);
        this.contentBuilders.put(HasValue.class, this::buildHasValue);
        this.contentBuilders.put(ItemGrid.class, this::buildItemGrid);

        this.afterLocaleChange.put(SuperIntegerField.class, o -> ((SuperIntegerField)o).setMaximumIntegerDigits(6));
        this.afterLocaleChange.put(SuperLongField.class, o -> ((SuperLongField)o).setMaximumIntegerDigits(11));
        this.afterLocaleChange.put(SuperDoubleField.class, o -> ((SuperDoubleField)o).withMaximumIntegerDigits(8).setMaximumFractionDigits(4));
        this.afterLocaleChange.put(SuperBigDecimalField.class, o -> ((SuperBigDecimalField)o).withMaximumIntegerDigits(12).withMaximumFractionDigits(3).setMinimumFractionDigits(1));

        final SuperTabs<Class<?>> tabs = new SuperTabs<>(
                type -> new Tab(type.getSimpleName()),
                this::buildContentsFor,
                this.components.keySet().toArray(new Class<?>[0])
        );

        tabs.addTab(MainView.class, new Tab(new Icon(VaadinIcon.INFO_CIRCLE), new Span("About this demo")), this.getInfoPage());

        this.add(tabs);
    }
}
