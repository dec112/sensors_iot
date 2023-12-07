package run.jkdev.dec4iot.jetpack.enums;

public enum LocationType {
    GPS_CURRENT_LOCATION(0),
    GPS_LAST_LOCATION(1),
    MLS_LOCATION_VIA_CELL_TOWERS(2);

    final int value;
    LocationType(int value) {
        this.value = value;
    }

    public int toInt() {
        return value;
    }
}
