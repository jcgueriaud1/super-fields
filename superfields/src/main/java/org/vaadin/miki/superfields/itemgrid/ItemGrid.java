package org.vaadin.miki.superfields.itemgrid;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasComponents;
import com.vaadin.flow.component.HasStyle;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.data.binder.HasItems;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Grid of items, with defined number of columns.
 * Each cell in the grid corresponds to a single element from the underlying collection of data.
 *
 * Note: currently this is not lazy-loading the data and not reacting to changes in the underlying data set.
 *
 * @param <T> Type of item stored in the grid.
 *
 * @author miki
 * @since 2020-04-14
 */
@Tag("item-grid")
public class ItemGrid<T> extends CustomField<T> implements HasItems<T>, HasStyle {

    /**
     * Default number of columns.
     */
    public static final int DEFAULT_COLUMN_COUNT = 3;

    /**
     * Style that by default indicates a selected item.
     * This is only needed when default {@link #setCellSelectionHandler(CellSelectionHandler)} is used.
     */
    public static final String DEFAULT_SELECTED_ITEM_CLASS_NAME = "item-grid-selected-cell";

    /**
     * Default {@link CellSelectionHandler}. It adds or removes {@link #DEFAULT_SELECTED_ITEM_CLASS_NAME} to the element of the component.
     * @param event Event.
     * @param <V> Value type.
     */
    public static <V> void defaultCellSelectionHandler(CellSelectionEvent<V> event) {
        if(event.isSelected())
            event.getCellInformation().getComponent().getElement().getClassList().add(DEFAULT_SELECTED_ITEM_CLASS_NAME);
        else
            event.getCellInformation().getComponent().getElement().getClassList().remove(DEFAULT_SELECTED_ITEM_CLASS_NAME);
    }

    /**
     * Default supplier for the main layout of the grid.
     * @return A {@link Div}.
     */
    public static Div defaultMainContainerSupplier() {
        Div result = new Div();
        result.addClassName("item-grid-contents");
        return result;
    }

    /**
     * Default {@link CellGenerator}. It produces a {@link Span} with {@link String#valueOf(Object)} called on {@code item}.
     * @param item Item to generate component for.
     * @param row Row in the grid.
     * @param col Column in the grid.
     * @param <V> Item type.
     * @return A {@link Span}: {@code <span>item</span>}.
     */
    public static <V> Component defaultCellGenerator(V item, int row, int col) {
        Span result = new Span(String.valueOf(item));
        result.addClassNames("item-grid-cell", "item-grid-cell-column-"+evenOrOdd(col), "item-grid-cell-row-"+evenOrOdd(row));
        return result;
    }

    /**
     * Default {@link RowComponentGenerator}. It produces a {@link Div}.
     * @param rowNumber Number of the row to create.
     * @return A {@link Div}.
     */
    public static Div defaultRowComponentGenerator(int rowNumber) {
        Div row = new Div();
        row.addClassNames("item-grid-row", "item-grid-row-"+evenOrOdd(rowNumber));
        return row;
    }

    /**
     * Defines whether a number is even or odd. Used to vary class names of various elements.
     * Internal use only.
     * @param number Number to check.
     * @return {@code "even"} when the {@code number} is even, otherwise {@code "odd"}.
     * @see #defaultRowComponentGenerator(int)
     * @see #defaultCellGenerator(Object, int, int)
     */
    private static String evenOrOdd(int number) {
        return number%2 == 0 ? "even" : "odd";
    }

    private final HasComponents contents;

    private final List<CellInformation<T>> cells = new ArrayList<>();

    private CellInformation<T> markedAsSelected;

    private CellGenerator<T> cellGenerator;

    private CellSelectionHandler<T> cellSelectionHandler;

    private RowComponentGenerator<?> rowComponentGenerator = ItemGrid::defaultRowComponentGenerator;

    private int columnCount = DEFAULT_COLUMN_COUNT;

    /**
     * Creates the component with given items, using default {@link CellGenerator} and {@link CellSelectionHandler}.
     * @param items Items to add to the component.
     */
    @SafeVarargs
    public ItemGrid(T... items) {
        this(null, null, null, items);
    }

    /**
     * Creates the component with given items and {@link CellGenerator}, but with default {@link CellSelectionHandler}.
     * @param generator {@link CellGenerator} to use.
     * @param items Items to add to the component.
     */
    @SafeVarargs
    public ItemGrid(CellGenerator<T> generator, T... items) {
        this(null, generator, null, items);
    }

    /**
     * Creates the component with given {@link CellGenerator}, {@link CellSelectionHandler} and items.
     * @param generator {@link CellGenerator} to use.
     * @param handler {@link CellSelectionHandler} to use.
     * @param items Items to add to the component.
     */
    @SafeVarargs
    public ItemGrid(CellGenerator<T> generator, CellSelectionHandler<T> handler, T... items) {
        this(null, generator, handler, items);
    }

    /**
     * Creates the component with given {@link CellGenerator}, {@link CellSelectionHandler} and items, overriding default (empty) value.
     * @param defaultValue Default (empty) value.
     * @param generator {@link CellGenerator} to use.
     * @param handler {@link CellSelectionHandler} to use.
     * @param items Items to add to the component.
     */
    @SafeVarargs
    public ItemGrid(T defaultValue, CellGenerator<T> generator, CellSelectionHandler<T> handler, T... items) {
        this(defaultValue, ItemGrid::defaultMainContainerSupplier, generator, handler, items);
    }

    /**
     * Creates the component with given {@link CellGenerator}, {@link CellSelectionHandler} and items, overriding default (empty) value.
     * @param defaultValue Default (empty) value.
     * @param mainContainerSupplier Method to generate main container component.
     * @param generator {@link CellGenerator} to use.
     * @param handler {@link CellSelectionHandler} to use.
     * @param items Items to add to the component.
     * @param <C> Type parameter to ensure {@code mainContainerSupplier} provides a {@link Component} that also {@link HasComponents}.
     */
    @SafeVarargs
    public <C extends Component & HasComponents> ItemGrid(T defaultValue, Supplier<C> mainContainerSupplier, CellGenerator<T> generator, CellSelectionHandler<T> handler, T... items) {
        super(defaultValue);
        this.contents = mainContainerSupplier.get();
        this.add((Component)this.contents);
        this.setCellGenerator(generator);
        this.setCellSelectionHandler(handler);
        this.setItems(items);
    }

    /**
     * Repaints all current items.
     */
    protected final void repaintAllItems() {
        this.repaintAllItems(this.cells.stream().map(CellInformation::getValue).collect(Collectors.toList()));
    }

    /**
     * Repaints all items in the collection.
     * @param itemCollection Collection with items to repaint.
     */
    protected void repaintAllItems(Collection<T> itemCollection) {
        final T currentValue = this.getValue();

        this.contents.removeAll();
        this.cells.clear();

        // do all items again
        HasComponents rowContainer = this.getRowComponentGenerator().generateRowComponent(0);

        int row = 0;
        int column = 0;

        for(T item: itemCollection) {
            final boolean selected = Objects.equals(item, currentValue);
            final Component itemComponent = this.getCellGenerator().generateComponent(item, row, column);
            CellInformation<T> cellInformation = new CellInformation<>(row, column, item, itemComponent);
            this.getCellSelectionHandler().cellSelectionChanged(new CellSelectionEvent<>(cellInformation, selected));
            this.registerClickEvents(cellInformation);

            if(selected)
                this.markedAsSelected = cellInformation;
            this.cells.add(cellInformation);

            rowContainer.add(itemComponent);
            column+=1;
            if(column == this.getColumnCount()) {
                column = 0;
                row+=1;
                this.contents.add((Component)rowContainer);
                rowContainer = this.getRowComponentGenerator().generateRowComponent(row);
            }
        }
        // add last row, unless it was full
        if(column != 0)
            this.contents.add((Component)rowContainer);
    }

    /**
     * Adds a click listener to the dom element of the {@link Component} inside given {@link CellInformation}.
     * This click listener will select or deselect a cell and update the value of this grid.
     *
     * Note: when overriding this method, please remember to call {@code super}.
     * @param information Information. Never {@code null}.
     */
    protected void registerClickEvents(CellInformation<T> information) {
        information.getComponent().getElement().addEventListener("click", event -> this.clickCellAndUpdateValue(information));
    }

    private void clickCellAndUpdateValue(CellInformation<T> information) {
        this.clickCell(information);
        this.updateValue();
    }

    /**
     * Reacts to cell being clicked in the browser.
     * @param information Information about the clicked cell.
     */
    protected void clickCell(CellInformation<T> information) {
        // if there is no selection at all, mark and remember component as selected
        if(this.markedAsSelected == null) {
            this.markedAsSelected = information;
            this.getCellSelectionHandler().cellSelectionChanged(new CellSelectionEvent<>(this.markedAsSelected, true));
        }
        // if the same value is selected, deselect it and do nothing else
        else if(Objects.equals(this.markedAsSelected.getValue(), information.getValue())) {
            this.getCellSelectionHandler().cellSelectionChanged(new CellSelectionEvent<>(information, false));
            this.markedAsSelected = null;
        }
        // otherwise deselect existing value and select new value
        else {
            this.getCellSelectionHandler().cellSelectionChanged(new CellSelectionEvent<>(this.markedAsSelected, false));
            this.markedAsSelected = information;
            this.getCellSelectionHandler().cellSelectionChanged(new CellSelectionEvent<>(this.markedAsSelected, true));
        }
    }

    @Override
    protected T generateModelValue() {
        return this.markedAsSelected == null ? this.getEmptyValue() : this.markedAsSelected.getValue();
    }

    @Override
    protected void setPresentationValue(T t) {
        if(Objects.equals(t, this.getEmptyValue()) && this.markedAsSelected != null)
            this.clickCell(this.markedAsSelected);
        else if(!Objects.equals(t, this.getEmptyValue()))
            this.getCellInformation(t).ifPresent(this::clickCell);
    }

    /**
     * Sets new {@link CellGenerator}. Repaints all items.
     * @param cellGenerator Cell generator. If {@code null} is passed, {@link #defaultCellGenerator(Object, int, int)} will be used.
     */
    public void setCellGenerator(CellGenerator<T> cellGenerator) {
        this.cellGenerator = Optional.ofNullable(cellGenerator).orElse(ItemGrid::defaultCellGenerator);
        this.repaintAllItems();
    }

    /**
     * Returns current {@link CellGenerator} used to generate cells.
     * @return A {@link CellGenerator}. Never {@code null}.
     */
    public CellGenerator<T> getCellGenerator() {
        return this.cellGenerator;
    }

    /**
     * Chains {@link #setCellGenerator(CellGenerator)} and returns itself.
     * @param generator {@link CellGenerator} to use.
     * @return This.
     * @see #setCellGenerator(CellGenerator)
     */
    public ItemGrid<T> withCellGenerator(CellGenerator<T> generator) {
        this.setCellGenerator(generator);
        return this;
    }

    /**
     * Sets new {@link CellSelectionHandler}. Repaints all items.
     * @param cellSelectionHandler Cell selection handler. If {@code null} is passed, {@link #defaultCellSelectionHandler(CellSelectionEvent)} will be used.
     */
    public void setCellSelectionHandler(CellSelectionHandler<T> cellSelectionHandler) {
        this.cellSelectionHandler = Optional.ofNullable(cellSelectionHandler).orElse(ItemGrid::defaultCellSelectionHandler);
        this.repaintAllItems();
    }

    /**
     * Returns current {@link CellSelectionHandler} used to react to selection changes.
     * @return A {@link CellSelectionHandler}. Never {@code null}.
     */
    public CellSelectionHandler<T> getCellSelectionHandler() {
        return this.cellSelectionHandler;
    }

    /**
     * Chains {@link #setCellSelectionHandler(CellSelectionHandler)} and returns itself.
     * @param handler {@link CellSelectionHandler} to use.
     * @return This.
     * @see #setCellSelectionHandler(CellSelectionHandler)
     */
    public ItemGrid<T> withCellSelectionHandler(CellSelectionHandler<T> handler) {
        this.setCellSelectionHandler(handler);
        return this;
    }

    /**
     * Returns the number of rows (even incomplete) currently in the grid.
     * @return The number of rows. This will change if new items are added or the number of columns changes.
     * @see #setItems(Collection)
     * @see #setItems(Object[])
     * @see #setItems(Stream)
     * @see #setColumnCount(int)
     */
    public long getRowCount() {
        return ((Component)this.contents).getChildren().count();
    }

    /**
     * Returns the current number of columns.
     * @return The number of columns in the grid.
     */
    public int getColumnCount() {
        return this.columnCount;
    }

    /**
     * Sets the new number of columns. Repaints all items.
     * @param columnCount Number of columns. Values less than {@code 1} are replaced with {@code 1} instead,
     */
    public void setColumnCount(int columnCount) {
        this.columnCount = Math.max(1, columnCount);
        this.repaintAllItems();
    }

    /**
     * Chains {@link #setColumnCount(int)} and returns itself.
     * @param columnCount Number of columns.
     * @return This.
     * @see #setColumnCount(int)
     */
    public ItemGrid<T> withColumnCount(int columnCount) {
        this.setColumnCount(columnCount);
        return this;
    }

    @Override
    public void setItems(Collection<T> collection) {
        this.repaintAllItems(collection);
    }

    /**
     * Returns the number of cells.
     * @return Number of cells.
     */
    public int size() {
        return this.cells.size();
    }

    /**
     * Returns a list with information about each cell.
     * @return A list with {@link CellInformation}. Never {@code null}. Changes to the resulting object do not affect the grid.
     */
    public List<CellInformation<T>> getCellInformation() {
        return new ArrayList<>(this.cells);
    }

    /**
     * Returns {@link CellInformation} about currently selected cell.
     * @return A {@link CellInformation}, if any cell is currently selected.
     */
    public Optional<CellInformation<T>> getSelectedCellInformation() {
        return Optional.ofNullable(this.markedAsSelected);
    }

    /**
     * Returns {@link CellInformation} that corresponds to the provided value.
     * @param value A value to look for.
     * @return A {@link CellInformation}, if a cell corresponding to the provided value is available.
     * @see #setItems(Object[])
     * @see #setItems(Collection)
     * @see #setItems(Stream)
     */
    public Optional<CellInformation<T>> getCellInformation(T value) {
        return this.cells.stream().filter(cell -> Objects.equals(cell.getValue(), value)).findFirst();
    }

    /**
     * Returns {@link CellInformation} that corresponds to the cell of given coordinates.
     * @param row Row number (0-based).
     * @param column Column number (0-based).
     * @return A {@link CellInformation}, if a cell corresponding to the given coordinates is available.
     * @see #setColumnCount(int)
     * @see #getColumnCount()
     * @see #getRowCount()
     */
    public Optional<CellInformation<T>> getCellInformation(int row, int column) {
        return this.cells.stream().filter(cell -> cell.getRow() == row && cell.getColumn() == column).findFirst();
    }

    /**
     * Returns a {@link Stream} of all {@link Component}s in the cells.
     * @return A {@link Stream}. Never {@code null}.
     * @see #setCellGenerator(CellGenerator)
     */
    public Stream<Component> getCellComponents() {
        return this.cells.stream().map(CellInformation::getComponent);
    }

    /**
     * Sets new {@link RowComponentGenerator} invoked every time a new row for grid cells is needed.
     * @param rowComponentGenerator {@link RowComponentGenerator} to use. If {@code null} is passed, then {@link #defaultRowComponentGenerator(int)} will be used.
     */
    public void setRowComponentGenerator(RowComponentGenerator<?> rowComponentGenerator) {
        if(rowComponentGenerator == null)
            this.rowComponentGenerator = ItemGrid::defaultRowComponentGenerator;
        else this.rowComponentGenerator = rowComponentGenerator;
        this.repaintAllItems();
    }

    /**
     * Returns current {@link RowComponentGenerator}.
     * @return A {@link RowComponentGenerator}. Never {@code null}.
     */
    public RowComponentGenerator<?> getRowComponentGenerator() {
        return rowComponentGenerator;
    }

    /**
     * Chains {@link #setRowComponentGenerator(RowComponentGenerator)} and returns itself.
     * @param generator A {@link RowComponentGenerator} to use.
     * @return This.
     * @see #setRowComponentGenerator(RowComponentGenerator)
     */
    public ItemGrid<T> withRowComponentGenerator(RowComponentGenerator<?> generator) {
        this.setRowComponentGenerator(generator);
        return this;
    }

    /**
     * Simulates clicking a cell at given coordinates (which means it updates the value).
     * Nothing happens if there is no cell that corresponds to given coordinates.
     *
     * This method is For testing purposes only.
     *
     * @param row Row the cell is in.
     * @param col Column the cell is in.
     */
    void simulateCellClick(int row, int col) {
        this.getCellInformation(row, col).ifPresent(this::clickCellAndUpdateValue);
    }

}