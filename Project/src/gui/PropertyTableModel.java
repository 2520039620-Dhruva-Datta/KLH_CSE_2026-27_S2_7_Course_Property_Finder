package gui;

import model.UnifiedProperty;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class PropertyTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {
            "No.", "Source", "Property", "Location", "City / State", "Type / Config", "Area", "Price / Rent", "Furnishing"
    };

    private List<UnifiedProperty> rows = new ArrayList<>();

    public void setRows(List<UnifiedProperty> rows) {
        this.rows = rows;
        fireTableDataChanged();
    }

    public void clear() {
        setRows(new ArrayList<>());
    }

    public UnifiedProperty getPropertyAt(int rowIndex) {
        return rows.get(rowIndex);
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        UnifiedProperty p = rows.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> rowIndex + 1;
            case 1 -> p.getSourceDisplayName();
            case 2 -> p.getTitle();
            case 3 -> p.getLocality();
            case 4 -> cityState(p);
            case 5 -> typeConfig(p);
            case 6 -> p.getArea();
            case 7 -> p.getPriceDisplay();
            case 8 -> p.getFurnishing();
            default -> "";
        };
    }

    private static String cityState(UnifiedProperty p) {
        if (p.getState().equals("N/A")) {
            return p.getCity();
        }
        return p.getCity() + " / " + p.getState();
    }

    private static String typeConfig(UnifiedProperty p) {
        if (p.getConfiguration().equals("N/A")) {
            return p.getPropertyType();
        }
        if (p.getPropertyType().equals("N/A")) {
            return p.getConfiguration();
        }
        return p.getPropertyType() + " / " + p.getConfiguration();
    }
}
